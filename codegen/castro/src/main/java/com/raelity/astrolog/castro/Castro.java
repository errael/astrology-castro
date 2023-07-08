/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.lib.CentralLookup;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

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

static void usage() { usage(null); }
@SuppressWarnings("UseOfSystemOutOrSystemErr")
static void usage(String note)
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
    boolean optTest = false;
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
            case "test" -> { optTest = true; }
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

    List<String> inputFiles = new ArrayList<>(Arrays.asList(args).subList(g.getOptind(), args.length));

    if(optTest) {
        runCompilerTest(inputFiles.get(0), outName);

        AstroParseResult apr = lookup(AstroParseResult.class);

        CastroOut testOut = lookup(CastroOut.class);
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
    
    for(CastroOut tout : CentralLookup.getDefault().lookupAll(CastroOut.class)) {
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

static void runCompilerTest(String inputFile, String outName)
{
    CastroIO castroIO = new CastroIO(inputFile, outName, true);
    if(castroIO.getErrorMsg() != null)
        usage(castroIO.getErrorMsg());
    if(castroIO.isAbort())
        return;
    
    out = new CastroOut(castroIO.getOutputWriter(), castroIO.getBaseName(),
            castroIO.getOutDir(), inputFile);
    replaceLookup(out);
    
    AstroParseResult apr = castroIO.getApr();
    replaceLookup(apr);

    // Have Err go to Out. Everything goes to the same place for tests
    replaceLookup(new CastroErr(lookup(CastroOut.class).pw()));

    ProgramContext program = apr.getParser().program();
    apr.setContext(program);
    GenSimpleOutput.genPrefixNotation();
}

}
