/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;

import com.raelity.astrolog.castro.antlr.AstroLexer;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.lib.CentralLookup;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.antlr.v4.runtime.CharStreams.fromPath;
import static org.antlr.v4.runtime.CharStreams.fromStream;

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
private static CastroOut out;


public static record CastroOut(PrintWriter pw, String baseName, Path outDir,
                                                                String inFile){};
public static record CastroErr(PrintWriter pw){};
public static record CastroOutputOptions(EnumSet<OutputOptions> outputOpts) {
    public CastroOutputOptions(EnumSet<OutputOptions> outputOpts)
        { this.outputOpts = EnumSet.copyOf(outputOpts); }
    public EnumSet<OutputOptions> outputOpts()
        { return EnumSet.copyOf(this.outputOpts); }
    };

// Keep track of definitions, externs that are read.
public static interface MemAccum {AstroMem defined();AstroMem extern();}
public static record RegistersAccum(Registers defined, Registers extern)
        implements MemAccum {};
public static record SwitchesAccum(Switches defined, Switches extern) 
        implements MemAccum {};
public static record MacrosAccum(Macros defined, Macros extern) 
        implements MemAccum {};

static final String cmdName = "castro";
static final String IN_EXT = ".castro";
static final String OUT_EXT = ".as";
static final String DEF_EXT = ".def";
static final String OUT_TEST_EXT = ".castro.test";

private static final Logger LOG = Logger.getLogger(Castro.class.getName());

private static int optVerbose;
private static boolean optTest;
private static String runOption = "compile";

private static void usage() { usage(null); }
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private static void usage(String note)
{
    if(note != null)
        System.err.printf("%s: %s\n", cmdName, note);
    String usage =
            """
            Usage: {cmdName} [-h] [--test] [-v] [-o outfile] infile+
                infile may be '-' for stdin.
                if outfile not specified, it is derived from infile.
                -o outfile      allowed if exactly one infile, '-' is stdout
                --formatoutput=opt1,... # comma separated list of:
                    1st two for switch and macro, next two for run
                        bslash          - split into new-line/backslash lines
                        nl              - split into lines
                        indent          - indent lines
                        run_nl          - split into lines
                        run_indent      - indent lines
                        debug           - precede macro output with original text
                    Default is no options; switch/macro/run on a single line
                    which is compatible with all Astrolog versions.
                --test  output prefix parse data
                -v      output more info
                -h      output this message
            """.replace("{cmdName}", cmdName);
    System.err.println(usage);
    System.exit(1);
}

public static int getVerbose()
{
    return optVerbose;
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public static void main(String[] args)
{
    String outName = null;

    LOG.getLevel(); // So now it's used.
    addLookup(err);
    
    // https://www.gnu.org/software/gnuprologjava/api/gnu/getopt/Getopt.html
    LongOpt longOpts[] = new LongOpt[] {
        new LongOpt("test", LongOpt.OPTIONAL_ARGUMENT, null, 2),
        new LongOpt("formatoutput", LongOpt.REQUIRED_ARGUMENT, null, 3),
    };
    Getopt g = new Getopt(cmdName, args, "o:hv", longOpts);
    
    EnumSet<OutputOptions> oset = EnumSet.noneOf(OutputOptions.class);
    int c;
    while ((c = g.getopt()) != -1) {
        switch (c) {
        case 'o' -> outName = g.getOptarg();
        case 'h' -> usage();
        case 'v' -> optVerbose++;
        case 2 -> {
            switch(longOpts[g.getLongind()].getName()) {
            case "test" -> { optTest = true; runOption = "test"; }
            }
        }
        case 3 -> {
            String optarg = g.getOptarg();
            String[] opts = optarg.split(",");
            for(String opt : opts) {
                OutputOptions oo = switch(opt) {
                case "bslash" -> OutputOptions.SM_BACKSLASH;
                case "nl" -> OutputOptions.SM_NEW_LINES;
                case "indent" -> OutputOptions.SM_INDENT;
                case "run_nl" -> OutputOptions.RUN_NEW_LINES;
                case "run_indent" -> OutputOptions.RUN_INDENT;
                case "debug" -> OutputOptions.SM_DEBUG;
                case null, default -> null;
                };
                if(oo == null)
                    usage("Unknown output option '"+opt+"'");
                oset.add(oo);
            }
        }
        default -> {
            usage();
        }
        }
    }

    if(outName != null && args.length - g.getOptind() > 1)
        usage("If '-o' specified, then at most one input file allowed");

    addLookup(new CastroOutputOptions(oset));
    if(optVerbose > 0)
        System.err.println(String.format("java:%s vm:%s date:%s os:%s",
                           System.getProperty("java.version"),
                           System.getProperty("java.vm.version"),
                           System.getProperty("java.version.date"),
                           System.getProperty("os.name")));

    addLookup(new RegistersAccum(new Registers(), new Registers()));
    addLookup(new MacrosAccum(new Macros(), new Macros()));
    addLookup(new SwitchesAccum(new Switches(), new Switches()));

    List<String> inputFiles = new ArrayList<>(Arrays.asList(args).subList(g.getOptind(), args.length));
    
    for(String inputFile : inputFiles) {
        
        CastroIO castroIO = new CastroIO(inputFile, outName);
        if(castroIO.status != null)
            usage(castroIO.status);
        if(castroIO.doAbort)
            System.exit(1);
        
        out = new CastroOut(castroIO.outputWriter, castroIO.baseName,
                castroIO.outDir, inputFile);
        replaceLookup(out);
        
        if(!optTest)
            outputFileHeader(out.pw(), ";");
        
        AstroParseResult apr = castroIO.apr;
        replaceLookup(apr);
        
        switch(runOption) {
        case "test" -> runCompilerTest();
        case null, default -> Compile.compile();
        }
        
        
        if(out != null) {
            out.pw().close();
            removeLookup(out);
        }
        
        if(apr.hasError())
            System.exit(1);
        
        // re-open the output file and write the "@" marker indicating no errors
        if(!optTest && castroIO.isDiskFile) {
            try (OutputStream ch = Files.newOutputStream(castroIO.outPath, WRITE)) {
                ch.write('@');
            } catch(IOException ex) {
                ex.printStackTrace(System.err);
            }
        }
    }

    for(CastroOut tout : CentralLookup.getDefault().lookupAll(CastroOut.class)) {
        // ERROR
        tout.pw().close();
        removeLookup(tout);
    }
    for(CastroErr terr : CentralLookup.getDefault().lookupAll(CastroErr.class)) {
        terr.pw().close();
        removeLookup(terr);
    }
}

private static String runDate;
/** All output files from this run have the same header. */
static void outputFileHeader(PrintWriter out, String commentLeader)
{
    if(runDate == null)
        runDate = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME);
                //ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                //LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
    // Leave space for a "@", written if no compiler errors.
    out.printf("    %s Compiled for Astrolog v7.60\n", commentLeader);
    out.printf("    %s Generated by castro %s on %s\n", commentLeader, "v0.5", runDate);
}

static void runCompilerTest()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    // Have Err go to Out. Everything goes to the same place for tests
    replaceLookup(new CastroErr(lookup(CastroOut.class).pw()));
    ProgramContext program = apr.getParser().program();
    apr.setContext(program);
    GenSimpleOutput.genPrefixNotation();
}

    /** Given an input and output file name,
     * check/setup paths/files; setup parser with inputFile's stream.
     * @return null if OK, else error message.
     */
    static class CastroIO
    {
    private String status;

    private AstroParser parser = new AstroParser(null);
    private AstroLexer lexer;

    private Path outDir = null;
    private Path outPath = null;
    private String baseName = null;

    private PrintWriter outputWriter;
    private CharStream input;
    private boolean doAbort;
    private boolean isDiskFile;
    AstroParseResult apr;

    public CastroIO(String inFileName, String outFileName)
    {
        status = setupIO(inFileName, outFileName);
    }
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    private String setupIO(String inFileName, String outFileName)
    {
        if("-".equals(inFileName))
            inFileName = null;

        // TODO: check for identical files (not just name)
        if(inFileName != null && inFileName.equals(outFileName))
            return "infile and outfile can not be the same file";
        
        // TODO: if inName not found, try inName.castro
        if("-".equals(outFileName))
            outFileName = null;
        else if(outFileName == null && inFileName != null) {
            // Pick outFile built from inFile
            String base = inFileName;
            if(inFileName.endsWith(IN_EXT))
                base = inFileName.substring(0, inFileName.lastIndexOf(IN_EXT));
            outFileName = base + (optTest ? OUT_TEST_EXT : OUT_EXT);
            baseName = base;
        }

        try {
            Path tinPath = null;
            if(inFileName != null) {
                tinPath = Path.of(inFileName);
                if(!Files.exists(tinPath))
                    return String.format("input file '%s' does not exist", inFileName);
            }

            if(outFileName != null)
                outPath = Path.of(outFileName);

            outputWriter = outPath == null
                    ? new PrintWriter(System.out, true)   // TODO: true/false option
                    : new PrintWriter(Files.newBufferedWriter(outPath,
                                      WRITE, TRUNCATE_EXISTING, CREATE));
            if(outPath != null && Files.isRegularFile(outPath)) {
                isDiskFile = true;
                outDir = outPath.getParent();
            }

            input = tinPath != null ? fromPath(tinPath) : fromStream(System.in);
        } catch(IOException ex) {
            String msg = String.format(
                    "%s: %s\n", ex.getClass().getSimpleName(), ex.getMessage());

            lookup(CastroErr.class).pw().print(msg);
            doAbort = true;
            return null;
        }

        lexer = new AstroLexer(input);
        parser.setTokenStream(new CommonTokenStream(lexer));
        apr = AstroParseResult.get(parser, lexer, input);

        return null;
    }
    }

}
