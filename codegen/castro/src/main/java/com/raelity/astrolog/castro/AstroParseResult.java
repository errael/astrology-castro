/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;


import java.io.PrintWriter;
import java.util.Objects;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.raelity.antlr.ParseResult;
import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.antlr.AstroLexer;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;

import static com.raelity.antlr.ParseTreeUtil.getRuleName;
import static com.raelity.astrolog.castro.Util.getLineText;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.tokenLoc;

/**
 * Starts as the result of an initial parse; with properties
 * to set by walkers.
 */
public class AstroParseResult extends ParseResult<AstroParser, AstroLexer>
{
// TODO: accumulate errors in here
private int errors;
private final String inputFileName;

final Props prefixExpr = new Props("PrefixExpr");

public static AstroParseResult get(AstroParser parser, AstroLexer lexer,
                                CharStream input, ParserRuleContext context,
                                String inputFileName)
{
    return new AstroParseResult(parser, lexer, input, context, inputFileName);
}

public static AstroParseResult get(AstroParser parser, AstroLexer lexer,
                                CharStream input, ParserRuleContext context)
{
    return get(parser, lexer, input, context, null);
}

public static AstroParseResult get(AstroParser parser, AstroLexer lexer,
                                CharStream input)
{
    return get(parser, lexer, input, null);
}

public static AstroParseResult testingResult()
{
    return new AstroParseResult(null, null, null, null, null);
}

private AstroParseResult(AstroParser parser, AstroLexer lexer,
                         CharStream input, ParserRuleContext context,
                         String inputFileName)
{
    super(parser, lexer, input, context);
    this.inputFileName = inputFileName;
}

public boolean hasError()
{
    return errors != 0 || getParser().getNumberOfSyntaxErrors() != 0;
}

public int errors()
{
    return errors;
}

public void countError()
{
    errors++;
}

/**
 * Typically the AstroParser result is a program; this is a convenience
 * method.
 * @return the program
 * @throws ClassCastException if context is not program
 */
public ProgramContext getProgram()
{
    return (ProgramContext)super.getContext();
}

/** May be null */
public String getInputFileName()
{
    return inputFileName;
}

public Props getPrefixExprProps()
{
    return prefixExpr;
}



    /** Props with optional debug output */
    public class Props extends TreeProps<String>
    {
    private final String tag;

    public Props(String tag) { this.tag = tag; }

    private String reportNullProp(ParseTree node, String tag)
    {
        assert node != null;
        // TODO: get line's text
        if(node instanceof ParserRuleContext ctx)
            err().printf("%s '%s' not found for %s\n",
                            tokenLoc(ctx), getLineText(ctx.start), tag);
        else
            Objects.requireNonNull(null, String.format("%s '%s'", tag, node.getText()));
        return "#unknown#";
    }
    
    @Override
    public String removeFrom(ParseTree node)
    {
        String s = super.removeFrom(node);
        if(Castro.getVerbose() >= 2)
            err().printf("Remove: %08x %s\n", System.identityHashCode(node), s);
        return s != null ? s : reportNullProp(node, "removeFrom"+tag);
    }
    
    @Override
    public void put(ParseTree node, String value)
    {
        if(Castro.getVerbose() >= 2)
            err().printf("Saving: %08x %s %s'%s'\n",
                         System.identityHashCode(node), value,
                         getRuleName(getParser(), node, true), node.getText());
        if(value == null) {
            reportNullProp(node, "putTo"+tag);
            Objects.requireNonNull(value, "putTo"+tag);
        }
        super.put(node, value);
    }

    private PrintWriter err()
    {
        return lookup(CastroErr.class).pw();
    }
    }

}
