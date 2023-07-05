/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.logging.Logger;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;

import com.raelity.astrolog.castro.antlr.AstroLexer;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.lib.CentralLookup;

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


public static record CastroOut(PrintWriter pw){};
public static record CastroErr(PrintWriter pw){};
public static record CastroOutputOptions(EnumSet<OutputOptions> outputOpts) {
    public CastroOutputOptions(EnumSet<OutputOptions> outputOpts)
        { this.outputOpts = EnumSet.copyOf(outputOpts); }
    public EnumSet<OutputOptions> outputOpts()
        { return EnumSet.copyOf(this.outputOpts); }
    };

static final String cmdName = "castro";
static final String IN_EXT = ".castro";
static final String OUT_EXT = ".macro.as";
static final String OUT_TEST_EXT = ".macro.test";

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
            Usage: {cmdName} [-h] [--test] [-v] [infile [outfile]]
                infile/outfile default to stdin/stdout
                infile/outfile may be '-' for stdin/stdout
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
    LOG.getLevel(); // So now it's used.
    addLookup(err);
    
    // https://www.gnu.org/software/gnuprologjava/api/gnu/getopt/Getopt.html
    LongOpt longOpts[] = new LongOpt[] {
        new LongOpt("test", LongOpt.OPTIONAL_ARGUMENT, null, 2),
        new LongOpt("formatoutput", LongOpt.REQUIRED_ARGUMENT, null, 3),
    };
    Getopt g = new Getopt(cmdName, args, "hv", longOpts);
    
    EnumSet<OutputOptions> oset = EnumSet.noneOf(OutputOptions.class);
    int c;
    while ((c = g.getopt()) != -1) {
        switch (c) {
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
    addLookup(new CastroOutputOptions(oset));
    if(optVerbose > 0)
        System.err.println(String.format("java:%s vm:%s date:%s os:%s",
                           System.getProperty("java.version"),
                           System.getProperty("java.vm.version"),
                           System.getProperty("java.version.date"),
                           System.getProperty("os.name")));

    String inName = null;
    String outName = null;

    int i = g.getOptind();
    if (i != args.length) {
        // There are positional arguemnts.
        inName = args[i++];
        if (i != args.length)
            outName = args[i++];
        if(i != args.length)
            usage("At most two arguments");
    }

    CastroIO castroIO = new CastroIO(inName, outName);
    if(castroIO.status != null)
        usage(castroIO.status);
    if(castroIO.doAbort)
        System.exit(1);

    out = new CastroOut(castroIO.outputWriter);
    addLookup(out);
    
    AstroParseResult apr = castroIO.apr;
    addLookup(apr);

    switch(runOption) {
    case "test" -> runCompilerTest();
    case null, default -> Compile.compile();
    }
    

    if(out != null) {
        out.pw().close();
        removeLookup(out);
    }
    for(CastroOut tout : CentralLookup.getDefault().lookupAll(CastroOut.class)) {
        tout.pw().close();
        removeLookup(tout);
    }
    for(CastroErr terr : CentralLookup.getDefault().lookupAll(CastroErr.class)) {
        terr.pw().close();
        removeLookup(terr);
    }
    if(apr.hasError())
        System.exit(1);
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

    private PrintWriter outputWriter;
    private CharStream input;
    private boolean doAbort;
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
        }

        try {
            Path tinPath = null;
            if(inFileName != null) {
                tinPath = new File(inFileName).toPath();
                if(!Files.exists(tinPath))
                    return String.format("input file '%s' does not exist", inFileName);
            }

            outputWriter = outFileName == null
                    ? new PrintWriter(System.out, true)   // TODO: true/false option
                    : new PrintWriter(Files.newBufferedWriter(new File(outFileName).toPath(), WRITE, TRUNCATE_EXISTING, CREATE));

            input = tinPath != null ? fromPath(tinPath) : fromStream(System.in);
        } catch(IOException ex) {
            String msg = String.format(
                    "%s: %s\n", ex.getClass().getSimpleName(), ex.getMessage());

            lookup(CastroErr.class).pw().print(msg);
            //Castro.getErr().print(msg);
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
