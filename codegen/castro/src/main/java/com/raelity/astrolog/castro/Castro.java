/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;

import com.raelity.astrolog.castro.antlr.AstroLexer;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.antlr.v4.runtime.CharStreams.fromPath;
import static org.antlr.v4.runtime.CharStreams.fromStream;

/**
 * TODO: remove extraneous nodes from the tree after parsing.
 * @author err
 */
public class Castro
{
static final String cmdName = "castro";
static final String IN_EXT = ".castro";
static final String OUT_EXT = ".macro.as";
static final String OUT_TEST_EXT = ".macro.test";
private static final Logger LOG = Logger.getLogger(Castro.class.getName());

private static int optVerbose;
private static boolean optTest;
private static String runOption;

private static PrintWriter outputWriter = null;

static AstroParser parser = new AstroParser(null);
static AstroLexer lexer;

private static void usage() { usage(null); }
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private static void usage(String note)
{
    if(note != null)
        System.err.printf("%s: %s\n", cmdName, note);
    String usage = """
        Usage: {cmdName} [-h] [--test] [-v] [infile [outfile]]
            infile/outfile default to stdin/stdout
            infile/outfile may be '-' for stdin/stdout
            -h      output this message
            --test  output prefix parse data
            -v      output more info
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
    String inFileName = null;
    String outFileName = null;
    LOG.getLevel(); // So now it's used.
    
    // https://www.gnu.org/software/gnuprologjava/api/gnu/getopt/Getopt.html
    LongOpt longOpts[] = new LongOpt[] {
        new LongOpt("test", LongOpt.OPTIONAL_ARGUMENT, null, 2),
    };
    Getopt g = new Getopt(cmdName, args, "hv", longOpts);
    
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
        default -> {
            usage();
        }
        }
    }
    if(optVerbose > 0)
        System.err.println(String.format("java:%s vm:%s date:%s os:%s",
                           System.getProperty("java.version"),
                           System.getProperty("java.vm.version"),
                           System.getProperty("java.version.date"),
                           System.getProperty("os.name")));

    int i = g.getOptind();
    if (i != args.length) {
        // There are positional arguemnts.
        inFileName = args[i++];
        if (i != args.length)
            outFileName = args[i++];
        if("-".equals(inFileName))
            inFileName = null;
        if(i != args.length)
            usage("At most two arguments");
    }

    if(inFileName != null && inFileName.equals(outFileName))
        usage("infile and outfile can not be the same file");
    
    // TODO: check for identical files (not just name)
    if(inFileName != null && inFileName.equals(outFileName))
        usage("infile and outfile can not be the same file");
    
    // TODO: if inFileName not found, try inFileName.castro
    if("-".equals(outFileName))
        outFileName = null;
    else if(outFileName == null && inFileName != null) {
        // Pick outFile built from inFile
        String base = inFileName;
        if(inFileName.endsWith(IN_EXT))
            base = inFileName.substring(0, inFileName.lastIndexOf(IN_EXT));
        outFileName = base + (optTest ? OUT_TEST_EXT : OUT_EXT);
    }

    boolean some_error = false;
    outputWriter = null;
    try {
        Path inPath = null;
        if(inFileName != null) {
            inPath = new File(inFileName).toPath();
            if(!Files.exists(inPath))
                usage(String.format("input file '%s' does not exist", inFileName));
        }

        outputWriter = outFileName == null
                ? new PrintWriter(System.out, true)   // TODO: true/false option
                : new PrintWriter(Files.newBufferedWriter(new File(outFileName).toPath(), WRITE, TRUNCATE_EXISTING, CREATE));

        ProgramContext program;
        Castro castro = new Castro(inPath, outFileName);
        CharStream input = inPath != null ? fromPath(inPath) : fromStream(System.in);
        program = castro.parseProgram(input);
        AstroParseResult apr
                = AstroParseResult.get(parser, lexer, input, program, outputWriter);
        switch(runOption) {
        //case "test" -> CastroEcho.genPrefixNotation(parser, input, program, outputWriter);
        case "test" -> GenSimpleOutput.genPrefixNotation(apr);
        case null, default -> usage("compile not supported (YET)");
        }
    } catch(IOException ex) {
        String msg = String.format(
                "%s: %s\n", ex.getClass().getSimpleName(), ex.getMessage());
        System.err.print(msg);
        if(outputWriter != null)
            outputWriter.print(msg);
        //LOG.log(Level.SEVERE, null, ex);
        some_error = true;
    }
    if(outputWriter != null)
        outputWriter.close();
    if(some_error)
        System.exit(1);
}

//Path inPath;
//String outFileName;

public Castro(Path inPath, String outFileName)
{
    //this.inPath = inPath;
    //this.outFileName = outFileName;
}

ProgramContext parseProgram(CharStream cs)
throws IOException
{

    lexer = new AstroLexer(cs);
    parser.setTokenStream(new CommonTokenStream(lexer));

    ProgramContext program = parser.program();
    return program;
}

}
