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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.getopt.Getopt;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.astrolog.castro.antlr.AstroLexer;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;

/**
 * TODO: remove extraneous nodes from the tree after parsing.
 * @author err
 */
public class Castro
{
private static int verbose;
private static String infileName;
private static String outfileName;

private static void usage()
{
    String usage = """
        Usage: Castro [-v] [infile [outfile]]
            infile/outfile default to stdin/stdout
            outfile may be '-' for stdout
            
        """;
    System.err.println(usage);
    System.exit(1);
}

public static int getVerbose()
{
    return verbose;
}

public static void main(String[] args)
{
    System.err.println(String.format("java:%s vm:%s date:%s os:%s",
                       System.getProperty("java.version"),
                       System.getProperty("java.vm.version"),
                       System.getProperty("java.version.date"),
                       System.getProperty("os.name")));
    
    // https://www.gnu.org/software/gnuprologjava/api/gnu/getopt/Getopt.html
    //Getopt g = new Getopt("castro", args, "hvdinrp:s:");
    Getopt g = new Getopt("castro", args, "hvdinrp:s:");
    
    int c;
    while ((c = g.getopt()) != -1) {
        switch (c) {
        case 'v':
            verbose++;
            break;
        default:
        }
    }
    int i = g.getOptind();
    CharStream cs = null;

    try {
        //if(Boolean.FALSE)
        //    cs = CharStreams.fromFileName("/ref/tools/astrolog.d/castro/grammar/x");
        //else
        {
        if (i == args.length)
            cs = CharStreams.fromStream(System.in);
        else {
            // There are positional arguemnts. Can only be one.
            if (i + 1 != args.length) {
                usage();
            }
            infileName = args[i];
            cs = CharStreams.fromFileName(infileName);
        }
        }
    } catch(IOException ex) {
        System.err.println(ex.getMessage());
        usage();
    }

    AstroLexer lexer = new AstroLexer(cs);
    AstroParser parser = new AstroParser(new CommonTokenStream(lexer));
    ProgramContext program = parser.program();

    String s = CastroEcho.getPrefixNotation(parser, program);
    System.out.println(s);
    
}

}
