/*
 * Portions created by Ernie Rael are
 * Copyright (C) 2023 Ernie Rael.  All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * Contributor(s): Ernie Rael <errael@raelity.com>
 */

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

static AstroParser parser = new AstroParser(null);

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
    PrintWriter outputWriter = null;
    try {
        Path inPath = null;
        if(inFileName != null) {
            inPath = new File(inFileName).toPath();
            if(!Files.exists(inPath))
                usage(String.format("input file '%s' does not exist", inFileName));
        }

        outputWriter = outFileName == null
                ? new PrintWriter(System.out)
                : new PrintWriter(Files.newBufferedWriter(new File(outFileName).toPath(), WRITE, TRUNCATE_EXISTING, CREATE));

        ProgramContext program;
        Castro castro = new Castro(inPath, outFileName);
        program = castro.parseProgram();
        switch(runOption) {
        case "test" -> CastroEcho.genPrefixNotation(parser, program, outputWriter);
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


Path inPath;
String outFileName;

public Castro(Path inPath, String outFileName)
{
    this.inPath = inPath;
    this.outFileName = outFileName;
}

ProgramContext parseProgram()
throws IOException
{
    // if in not found, try in.castro

    CharStream cs = inPath != null ? fromPath(inPath) : fromStream(System.in);

    AstroLexer lexer = new AstroLexer(cs);
    parser.setTokenStream(new CommonTokenStream(lexer));

    ProgramContext program = parser.program();
    return program;
}

}
