/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.lib.CentralLookup;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;
import com.raelity.astrolog.castro.optim.FoldConstants;

import static com.raelity.astrolog.castro.Error.*;
import static com.raelity.astrolog.castro.Util.addLookup;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.removeLookup;
import static com.raelity.astrolog.castro.Util.replaceLookup;


/**
 * TODO: remove extraneous nodes from the tree after parsing.
 * @author err
 */
public class Castro
{
// until we figure it out, put some common things here up front
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private static CastroErr err = new CastroErr(new PrintWriter(System.err, true));

public static record CastroErr(PrintWriter pw){};
public static record CastroMapName(String mapName){};
public static record CastroOutputOptions(EnumSet<OutputOptions> outputOpts) {
    public CastroOutputOptions(EnumSet<OutputOptions> outputOpts)
        { this.outputOpts = EnumSet.copyOf(outputOpts); }
    public EnumSet<OutputOptions> outputOpts()
        { return EnumSet.copyOf(this.outputOpts); }
    };
/** which errors to treat as warngings */
public static record CastroWarningOptions(EnumSet<Error> warn) {
    public CastroWarningOptions(EnumSet<Error> warn)
        { this.warn = EnumSet.copyOf(warn); }
    public EnumSet<Error> warn()
        { return EnumSet.copyOf(this.warn); }
    };
public static record CastroLineMaps(Map<String,LineMap> lineMaps){};

// Keep track of definitions, externs that are read.
public static interface MemAccum {AstroMem defined();AstroMem alloc(); AstroMem extern(); AstroMem global();}
public static record RegistersAccum(Registers defined, Registers alloc,
                                    Registers extern, Registers global)
        implements MemAccum {};
public static record MacrosAccum(Macros defined, Macros alloc,
                                 Macros extern, Macros global) 
        implements MemAccum {};
public static record SwitchesAccum(Switches defined, Switches alloc,
                                   Switches extern, Switches global) 
        implements MemAccum {};

static final String cmdName = "castro";
static final String IN_EXT = ".castro";
static final String OUT_EXT = ".as";
static final String DEF_EXT = ".def";
static final String MAP_EXT = ".map";
static final String OUT_TEST_EXT = ".test";

private static final Logger LOG = Logger.getLogger(Castro.class.getName());

private static int optVerbose;

private static EnumSet<Error> defaultWarn
        = EnumSet.of(FUNC_CASTRO, VAR_RSV, INNER_QUOTE, CONST_AMBIG);

public static String getVersionString()
{
    String version;
    String resourceName = "com/raelity/astrolog/castro/version.properties";
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    Properties props = new Properties();
    try(InputStream resourceStream = loader.getResourceAsStream(resourceName)) {
        props.load(resourceStream);

    version = props.getProperty("version");

    } catch(IOException ex) {
        version = "#MissingReourceFile#";
    }

    return version;
}

public static String getAstrologVersionString()
{
    return "7.60";
}

public static String getCompactAstrologVersionString()
{
    return getAstrologVersionString().replace(".", "");
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private static void version()
{
    System.out.printf("castro %s\n", getVersionString());
    System.exit(0);
}

static String listEwarnOptions()
{
    StringBuilder sb = new StringBuilder(500);
    sb.append("Errors that can be made warnings\n");
    for(Error e : Error.values())
        if(!e.help().isEmpty())
            sb.append(String.format("    %-12s %s\n", e.toString(), e.help()));

    return sb.toString();
}

private static void usageExit() { System.exit(1); }
static void usage() { usage(null); }
@SuppressWarnings("UseOfSystemOutOrSystemErr")
static void usage(String note)
{
    if(note != null)
        System.err.printf("%s: %s\n", cmdName, note);
    String defaultW = defaultWarn.stream().map((e) -> e.toString())
            .collect(Collectors.joining(" "));
    String usage = """
            Usage: {cmdName} [-h] [several-options] [-o outfile] infile+
                infile may be '-' for stdin.
                if outfile not specified, it is derived from infile.
                -o outfile      allowed if exactly one infile, '-' is stdout
                --mapname=mapname     Map file name is <mapname>.map.
                                        Default is derived from first infile.
                --Ewarn=ename   Make the specified error a warning.
                                Multiple uses is additive.
                                Can do no-ename to turn warning to error.
                                Use --Ewarn=all for all errors as warnings.
                                Use --Ewarn=none for no errors as warnings.
                                Order is important when using all/none.
                                Use --Ewarn=junk for a list.
                                Default: {defaultWarn}.
                --formatoutput=opt1,... # comma separated list of:
                        min             - no extra/blank lines
                        qflip           - quote flip default inner/outer
                        bslash          - split into new-line/backslash lines
                        nl              - split into lines
                        indent          - indent lines
                        run_nl          - split into lines
                        run_indent      - indent lines
                        debug           - precede macro output with original text
                    Default is no options; switch/macro/run on a single line
                    which is compatible with all Astrolog versions.
                    "min"/"qflip" usable with any Astrolog version.
                --anonymous     no dates/versions in output files (for test golden)
                --version       version
                -h      output this message

                The following options are primarily for debug. --gui is also fun to see
                and may provide insight. It shows how the program is parsed. Only uses
                the first file and does not generate any compilation output files.
                --gui           show AST in GUI
                --console       show the tokens and AST in the console
                --test  output prefix parse data
                -v      output more info
            """.replace("{cmdName}", cmdName).replace("{defaultWarn}", defaultW);
    System.err.println(usage);
    System.err.println(listEwarnOptions());
    usageExit();
}

public static int getVerbose()
{
    return optVerbose;
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public static void main(String[] args)
{
    boolean optTest = false;
    String outName = null;
    String mapName = null;

    boolean console = false;
    boolean gui = false;

    LOG.getLevel(); // So now it's used.
    addLookup(err);
    
    // https://www.gnu.org/software/gnuprologjava/api/gnu/getopt/Getopt.html
    LongOpt longOpts[] = new LongOpt[] {
        new LongOpt("test", LongOpt.NO_ARGUMENT, null, 2),
        new LongOpt("formatoutput", LongOpt.REQUIRED_ARGUMENT, null, 3),
        new LongOpt("anonymous", LongOpt.NO_ARGUMENT, null, 4),
        new LongOpt("mapname", LongOpt.REQUIRED_ARGUMENT, null, 5),
        new LongOpt("Ewarn", LongOpt.REQUIRED_ARGUMENT, null, 6),
        new LongOpt("gui", LongOpt.NO_ARGUMENT, null, 7),
        new LongOpt("console", LongOpt.NO_ARGUMENT, null, 8),
        new LongOpt("version", LongOpt.NO_ARGUMENT, null, 9),
    };
    Getopt g = new Getopt(cmdName, args, "o:hv", longOpts);
    
    EnumSet<OutputOptions> oset = EnumSet.noneOf(OutputOptions.class);
    // Default warnings
    EnumSet<Error> warnset = EnumSet.copyOf(defaultWarn);
    int c;
    while ((c = g.getopt()) != -1) {
        switch (c) {
        case 'o' -> outName = g.getOptarg();
        case 'h' -> usage();
        case 'v' -> optVerbose++;
        case 2 -> {
            switch(longOpts[g.getLongind()].getName()) {
            case "test" -> { optTest = true; }
            }
        }
        case 3 -> {
            String optarg = g.getOptarg();
            String[] opts = optarg.split(",");
            for(String opt : opts) {
                OutputOptions oo = OutputOptions.parse(opt);
                if(oo == null)
                    usage("Unknown output option '" + opt + "'");
                oset.add(oo);
            }
        }
        case 4 -> oset.add(OutputOptions.GENERAL_ANON);
        case 5 -> mapName = g.getOptarg();
        case 6 -> {
            // Option is to make Error a warning.
            if(g.getOptarg().equals("all")) {
                warnset.addAll(EnumSet.allOf(Error.class));
                break;
            }
            if(g.getOptarg().equals("none")) {
                warnset.clear();
                break;
            }

            EParse e = Error.parseErrorName(g.getOptarg());
            if(e == null) {
                System.err.printf("'%s' unknown error name\n%s",
                                  g.getOptarg(), listEwarnOptions());
                usageExit();
            } else {
                if(!e.negated())        // make the error a warning
                    warnset.add(e.error());
                else                    // -Ewarn=no-xxx, xxx causes an error
                    warnset.remove(e.error());
            }
        }
        case 7 -> gui = true;
        case 8 -> console = true;
        case 9 -> version();
        default -> {
            usage();
        }
        }
    }

    if(outName != null && args.length - g.getOptind() > 1)
        usage("If '-o' specified, then at most one input file allowed");

    if(optVerbose > 0)
        System.err.println(String.format("java:%s vm:%s date:%s os:%s",
                           System.getProperty("java.version"),
                           System.getProperty("java.vm.version"),
                           System.getProperty("java.version.date"),
                           System.getProperty("os.name")));

    List<String> inputFiles = new ArrayList<>(Arrays.asList(args).subList(g.getOptind(), args.length));
    if(!checkFileIssues(inputFiles))
        usage();
    
    addLookup(new CastroOutputOptions(oset));
    addLookup(new CastroWarningOptions(warnset));
    if(mapName != null)
        addLookup(new CastroMapName(mapName));

    if(gui || console) {
        show(inputFiles.get(0), gui, console);
        return;
    }

    if(optTest) {
        runCompilerTest(inputFiles.get(0), outName);

        AstroParseResult apr = lookup(AstroParseResult.class);

        CastroIO testOut = lookup(CastroIO.class);
        if(testOut != null) {
            testOut.pw().close();
            removeLookup(testOut);
        }

        if(apr == null || apr.hasError())
            System.exit(1);
        return;
    }

    // Note that outName can only be non-null if inputFiles is size 1.
    boolean success = Compile.compile(inputFiles, outName);

    if(optVerbose > 0) {
        FoldConstants.outputStats(lookup(CastroErr.class).pw());
    }
    
    for(CastroIO tout : CentralLookup.getDefault().lookupAll(CastroIO.class)) {
        // ERROR
        tout.pw().close();
        removeLookup(tout);
    }
    for(CastroErr terr : CentralLookup.getDefault().lookupAll(CastroErr.class)) {
        terr.pw().close();
        removeLookup(terr);
    }
        
    if(!success) {
        System.exit(1);
    }
}

/** verify that can read the file and that the same file is not
 * in the list twice.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
static boolean checkFileIssues(List<String> inputFiles)
{
    boolean read_error = false;
    ArrayList<Path> paths = inputFiles.stream().map((f) -> Path.of(f))
            .collect(Collectors.toCollection(ArrayList::new));
    // First just spin through and get rid of stuff that have access problems.
    for(Iterator<Path> it = paths.iterator(); it.hasNext();) {
        Path path = it.next();
        try (InputStream is = Files.newInputStream(path)) { 
            is.read();
        } catch(IOException ex) {
            System.err.printf("%s %s\n", ex.getClass().getSimpleName(),ex.getMessage());
            it.remove();
            read_error = true;
        }
    }

    // Can't have the same file in the list more than once
    record Pair(Path p1, Path p2){}
    List<Pair> dups = new ArrayList<>();
    for(int i = 0; i < paths.size(); i++) {
        Path p1 = paths.get(i);
        for(int j = i + 1; j < paths.size(); j++) {
            boolean file_error = false;
            boolean isSame = false;
            Path p2 = paths.get(j);
            //System.err.printf("    Check %d-%s %d-%s\n", i, p1, j, p2);
            try {
                isSame = Files.isSameFile(p1, p2);
            } catch(IOException ex) {
                System.err.printf("%s %s\n", ex.getClass().getSimpleName(),ex.getMessage());
                file_error = true;
            }
            if(isSame) {
                dups.add(new Pair(p1, p2));
                file_error = true;
            }
            if(file_error) {
                paths.remove(j);
                //System.err.printf("    REMOVE: %d %s\n", j, p2);
                j -= 1;
            }
        }
    }
    if(read_error || !dups.isEmpty()) {
        dups.forEach((p) -> System.err.printf("Same file: '%s' '%s'\n", p.p1, p.p2));
        return false;
    }
    return true;
}

static void runCompilerTest(String inputFile, String outName)
{
    CastroIO castroIO = new CastroIO(inputFile, outName, true);
    if(castroIO.getErrorMsg() != null)
        usage(castroIO.getErrorMsg());
    if(castroIO.isAbort())
        return;
    
    replaceLookup(castroIO);
    
    AstroParseResult apr = castroIO.getApr();
    replaceLookup(apr);

    // Have Err go to Out. Everything goes to the same place for tests
    replaceLookup(new CastroErr(lookup(CastroIO.class).pw()));

    ProgramContext program = apr.getParser().program();
    apr.setContext(program);
    GenSimpleOutput.genPrefixNotation();
}

static void show(String inputFile, boolean gui, boolean console) {
    CastroIO castroIO = new CastroIO(inputFile, "-", true);
    if(castroIO.getErrorMsg() != null)
        usage(castroIO.getErrorMsg());
    if(castroIO.isAbort())
        return;
    AstroParseResult apr = castroIO.getApr();

    AstroParser parser = apr.getParser();
    ProgramContext program = apr.getParser().program();

    if(console) {   //show tokens and AST in console
        PrintWriter pw = castroIO.pw();
        CommonTokenStream tokens = (CommonTokenStream)parser.getTokenStream();
        tokens.fill();

        for (Token tok : tokens.getTokens()) {
            assert tok != null;
            if ( tok instanceof CommonToken commonToken ) {
                pw.println(commonToken.toString(apr.getLexer()));
            }
            else {
                pw.println(tok.toString());
            }
        }
        pw.println(program.toStringTree(parser));
    }

    if(gui) {
        TreeViewer viewer = new TreeViewer(Arrays.asList(
                parser.getRuleNames()),program);
        viewer.open();
        // JFrame frame = new JFrame("Antlr AST");
        // JPanel panel = new JPanel();
        // viewer.setScale(1.5); // Scale a little
        // panel.add(viewer);
        // frame.add(panel);
        // frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // frame.pack();
        // frame.setVisible(true);
    }
}

}
