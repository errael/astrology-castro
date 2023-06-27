/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map.Entry;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.xpath.XPath;

import com.raelity.antlr.ParseTreeUtil;
import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.*;

import static com.raelity.antlr.ParseTreeUtil.getRuleName;
import static com.raelity.antlr.ParseTreeUtil.hasAncestor;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.antlr.AstroParser.Minus;
import static com.raelity.astrolog.castro.antlr.AstroParser.Plus;

/**
 * Output the tree in "prefix" notation; keep operator symbols and lvals.
 * This is primarily used for testing to verify that the results haven't
 * changed.
 * <p>
 * Build the exprs driven by {@linkplain GenPrefixExpr}.
 * Then traverse the tree and build one string per statement
 * grouped by the macro in which the statements are defined.
 * <p>
 * 
 * 
 * TODO: clear value in a node after it's used.
 * 
 * @author err
 */
public class GenSimpleOutput
{

final ParseTreeWalker walker = new ParseTreeWalker();
final ProgramContext program;
final AstroParser parser;
final CharStream input;
final PrintWriter out;
final AstroParseResult apr;

private GenSimpleOutput()
{
    this.apr = lookup(AstroParseResult.class);
    this.parser = apr.getParser();
    this.input = apr.getInput();
    this.program = apr.getProgram();
    this.out = lookup(CastroOut.class).pw;
}

StringBuilder sb = new StringBuilder();

private static PrintWriter getErr()
{
    return lookup(CastroErr.class).pw;
}

static void genPrefixNotation()
{
    GenSimpleOutput simpleOutput = new GenSimpleOutput();
    try {
        simpleOutput.generateAndOutputExprsByMacro();
    } catch (Exception ex) {
        getErr().printf("ABORT: %s\n", ex.getMessage());
        ex.printStackTrace(getErr());
    }
}

record DumpCounts(int walker, int statement){};

void generateAndOutputExprsByMacro()
{
    Pass1 pass1 = new Pass1(apr);
    walker.walk(pass1, program);

    Pass2 exprDump = new Pass2();
    DumpCounts dump = exprDump.dump();

    // verify that the number of statements is correct and that
    // all properties are consumed.

    // First get rid of var initializers and layout stuff. Work on that later.
    // But need to count them.
    int countExtra = 0;
    //out.printf("prefixExpr size: %d\n", apr.prefixExpr.size());
    for(String xpath : new String[]
            {"//var//expr", "//var//integer", "//layout//integer" }) {
        for(ParseTree pt : XPath.findAll(apr.getProgram(), xpath, apr.getParser())) {
            if(!xpath.equals("//var//expr")
                    || !hasAncestor((ParserRuleContext)pt, ExprContext.class)) {
                // this case is because "var foo {a + b};"
                // has "+ a b" but "//var//expr" also cactches "a" and "b".
                apr.prefixExpr.removeFrom(pt);
                countExtra++;
            }
        }
        // out.printf("prefixExpr size: %d (after filter %s)\n",
        //            apr.prefixExpr.size(), xpath);
    }

    if(Castro.getVerbose() > 0)
        out.printf("statements %d, extra %d, found %d. prefixExpr left %d\n",
                   dump.statement(), countExtra, dump.walker(), apr.prefixExpr.size());

    if(dump.walker() != dump.statement() + countExtra || apr.prefixExpr.size() != 0) {
        out.printf("PARSE ERROR: statements %d, extra %d, found %d. prefixExpr left %d\n",
                   dump.statement(), countExtra, dump.walker(), apr.prefixExpr.size());
        for(Entry<ParseTree, String> pt : apr.prefixExpr.getMap().entrySet()) {
            int line = pt.getKey() instanceof ParserRuleContext prc
                       ? prc.start.getLine(): -1;
            out.printf("pt: line %d: %s %s\n",
                       line, pt.getKey().getClass().getSimpleName(), pt);
        }
    }
}

    /** Generate top level expression text */
    private class Pass1  extends GenPrefixExpr
    {
    Pass1(AstroParseResult apr)
    {
        super(apr);
    }

    @Override
    String genIfOp(String condition, String if_true)
    {
        sb.setLength(0);
        sb.append("IF ")
                .append(condition).append(' ')
                .append(if_true);
        return sb.toString();
    }
            
    @Override
    String genIfElseOp(String condition, String if_true, String if_false)
    {
        sb.setLength(0);
        sb.append("IF ")
                .append(condition).append(' ')
                .append(if_true).append(' ')
                .append("ELSE ")
                .append(if_false);
        return sb.toString();
    }
    
    @Override
    String genRepeatOp(String count, String statement)
    {
        sb.setLength(0);
        sb.append("REPEAT ")
                .append(count).append(' ')
                .append(statement);
        return sb.toString();
    }
    
    @Override
    String genWhileOp(String condition, String statement)
    {
        sb.setLength(0);
        sb.append("WHILE ")
                .append(condition).append(' ')
                .append(statement);
        return sb.toString();
        
    }
    
    @Override
    String genDoWhileOp(String statement, String condition)
    {
        sb.setLength(0);
        sb.append("DO_WHILE ")
                .append(statement).append(' ')
                .append(condition);
        return sb.toString();
    }
    
    @Override
    String genForOp(String counter, String begin, String end,
                                          String statement)
    {
        sb.setLength(0);
        sb.append("FOR ")
                .append(counter).append(' ')
                .append("<== ")
                .append(begin).append(' ')
                .append("UNTIL ")
                .append(end).append(' ')
                .append(statement);
        return sb.toString();
    }
    
    @Override
    String genBraceBlockOp(List<String> statements)
    {
        sb.setLength(0);
        sb.append("BLOCK(").append(statements.size()).append(") ");
        for(String s : statements) {
            sb.append(s).append(' ');
            
        }
        return sb.toString();
    }
    
    @Override
    String genFuncCallOp(String funcName, List<String> args)
    {
        sb.setLength(0);
        sb.append("FUNC(").append(args.size()).append(") ");
        sb.append(funcName).append(' ');
        for(String arg : args) {
            sb.append(arg).append(' ');
        }
        return sb.toString();
    }
    
    @Override
    String genUnOp(Token opToken, String expr)
    {
        sb.setLength(0);
        int opType = opToken.getType();
        String text = opType == Minus ? "u-" : opType == Plus ? "u+"
                                       :opToken.getText();
        sb.append(text).append(' ').append(expr);
        return sb.toString();
    }
    
    @Override
    String genQuestColonOp(String condition, String if_true, String if_false)
    {
        sb.setLength(0);
        sb.append("?: ")
                .append(condition).append(' ')
                .append(if_true).append(' ')
                .append(if_false).append(' ');
        return sb.toString();
    }
    
    @Override
    String genBinOp(Token opToken, String lhs, String rhs)
    {
        sb.setLength(0);
        sb.append(opToken.getText()).append(' ')
                .append(lhs).append(' ')
                .append(rhs);
        return sb.toString();
    }
    
    @Override
    String genAssOp(Token opToken, String lhs, String rhs)
    {
        return genBinOp(opToken, lhs, rhs);
    }
    
    @Override
    String genLval(LvalContext ctx01, String... expr)
    {
        sb.setLength(0);
        return switch(ctx01) {
        case LvalMemContext ctx -> ctx.Identifier().getText();
        case LvalArrayContext ctx -> {
            sb.append("INDEX").append(' ').append(ctx.Identifier()).append(' ')
                    .append(expr[0]);
            yield sb.toString();
        }
        case LvalIndirectContext ctx -> {
            sb.append("INDIR").append(' ').append(ctx.Identifier().getText());
            yield sb.toString();
        }
        default -> throw new IllegalArgumentException("unknown class");
        };
    }
    
    @Override
    String genAddr(TermAddressOfContext ctx)
    {
        sb.setLength(0);
        sb.append("ADDR").append(' ').append(ctx.Identifier().getText());
        return sb.toString();
    }
                
    }

    /**
     * Output one line per statement of any collected info.
     */
    class Pass2  extends AstroBaseListener
    {
    int walkerCount;
    int astroExpressionCount;

    DumpCounts dump()
    {
        walker.walk(this, program);
        return new DumpCounts(walkerCount, astroExpressionCount);
    }
    
    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
        super.exitEveryRule(ctx);
        String s = apr.prefixExpr.get(ctx);
        if(s != null)
            walkerCount++;
    }

    void dumpStatement(AstroExprStatementContext ctx)
    {
        out.printf("input: %s\n", ParseTreeUtil.getOriginalText(ctx, input));
        out.printf("    %s %s\n", getRuleName(parser, ctx, true),
                                  apr.prefixExpr.removeFrom(ctx.astroExpr.expr));
    }
    
    @Override
    public void exitMacro(MacroContext ctx)
    {
        super.exitMacro(ctx);
        List<AstroExprStatementContext> es = ctx.s;

        sb.setLength(0);
        sb.append("=== MACRO ").append(ctx.Identifier().getText());
        if(ctx.addr != null) {
            sb.append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
            astroExpressionCount++; // weird, but the macro addr is an expr
        }
        sb.append('(').append(es.size()).append(')');

        out.printf("\n%s\n", sb.toString());
        for(AstroExprStatementContext s : es) {
            dumpStatement(s);
            astroExpressionCount++;
        }
    }
    
    }
    
}
