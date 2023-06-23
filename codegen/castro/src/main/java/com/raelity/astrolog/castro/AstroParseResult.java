/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;


import java.io.PrintWriter;
import java.util.Objects;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

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

final Props prefixExpr = new Props("PrefixExpr");

public static AstroParseResult get(AstroParser parser, AstroLexer lexer,
                                CharStream input, ParserRuleContext context)
{
    return new AstroParseResult(parser, lexer, input, context);
}

public static AstroParseResult get(AstroParser parser, AstroLexer lexer,
                                CharStream input)
{
    return new AstroParseResult(parser, lexer, input, null);
}

private AstroParseResult(AstroParser parser, AstroLexer lexer,
                                CharStream input, ParserRuleContext context)
{
    super(parser, lexer, input, context);
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

public Props getPrefixExprProps()
{
    return prefixExpr;
}



    /** Props with size() and optional debug output */
    public class Props extends ParseTreeProperty<String>
    {
    private final String tag;

    public Props(String tag) { this.tag = tag; }

    public int size() { return annotations.size(); }

    private String reportNullProp(ParseTree node, String tag)
    {
        assert node != null;
        // TODO: get line's text
        if(node instanceof ParserRuleContext ctx)
            getErr().printf("%s '%s' not found for %s\n",
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
            getErr().printf("Remove: %08x %s\n", System.identityHashCode(node), s);
        return s != null ? s : reportNullProp(node, "removeFrom"+tag);
    }
    
    @Override
    public void put(ParseTree node, String value)
    {
        if(Castro.getVerbose() >= 2)
            getErr().printf("Saving: %08x %s %s'%s'\n",
                            System.identityHashCode(node), value,
                            getRuleName(getParser(), node, true), node.getText());
        if(value == null) {
            reportNullProp(node, "putTo"+tag);
            Objects.requireNonNull(value, "putTo"+tag);
        }
        super.put(node, value);
    }

    private PrintWriter getErr()
    {
        return lookup(CastroErr.class).pw;
    }
    }

}
