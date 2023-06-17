/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.antlr.ParseTreeUtil;
import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.*;

import static com.raelity.antlr.ParseTreeUtil.getRuleName;
import static com.raelity.astrolog.castro.antlr.AstroParser.Minus;
import static com.raelity.astrolog.castro.antlr.AstroParser.Plus;

/**
 * Output the tree in "prefix" notation; keep operator symbols and lvals.
 * This is primarily used for testing to verify that the results haven't
 * changed.
 * <p>
 * General strategy is that an exit listener sets the echo string
 * incorporating it's children. Repeat until all text is in an expr.
 * Then traverse the tree and build one string per statment.
 * 
 * TODO: clear value in a node after it's used.
 * 
 * @author err
 */
public class CastroEcho
{

enum  ConstantType { INT, REAL, CHARS }

class TreeProps<T> extends ParseTreeProperty<T>
{
    int size() { return annotations.size(); }
}

final TreeProps<String> echo = new TreeProps<>();
final ParseTreeWalker walker = new ParseTreeWalker();
final ProgramContext program;
final AstroParser parser;
final CharStream input;
final PrintWriter out;

private CastroEcho(AstroParser parser, CharStream input, ProgramContext program, PrintWriter out) {
    this.parser = parser;
    this.input = input;
    this.program = program;
    this.out = out;
}

StringBuilder sb = new StringBuilder();

static void genPrefixNotation(AstroParser parser, CharStream input,
                              ProgramContext program, PrintWriter out)
{
    CastroEcho castroEcho = new CastroEcho(parser, input, program, out);
    try {
        castroEcho.doEcho();
    } catch (Exception ex) {
        out.printf("ABORT: %s\n", ex.getMessage());
        ex.printStackTrace(out);
    }
}

record DumpCounts(int walker, int statement){};

String doEcho()
{
    EchoPass1 echoPass1 = new EchoPass1();
    walker.walk(echoPass1, program);

    sb.setLength(0);
    ExprStatementDump exprDump = new ExprStatementDump();
    DumpCounts dump = exprDump.dump();
    if(dump.walker() != dump.statement() || echo.size() != 0) {
        out.printf("PARSE ERROR: statements %d, found %d. echos left %d\n",
                   dump.statement(), dump.walker(), echo.size());
    }

    return sb.toString();
}

private void putEcho(ParseTree ctx, String s)
{
    if(Castro.getVerbose() >= 2)
        out.printf("Saving: %08x %s %s'%s'\n",
                   System.identityHashCode(ctx), s,
                   getRuleName(parser, ctx, true), ctx.getText());
    if(s == null)
        Objects.requireNonNull(s, "putEcho");
    echo.put(ctx, s);
}

private String removeFromEcho(ParseTree ctx)
{
    String s = echo.removeFrom(ctx);
    if(Castro.getVerbose() >= 2)
        out.printf("Remove: %08x %s\n", System.identityHashCode(ctx), s);
    if(s == null)
        Objects.requireNonNull(s, "removeFromEcho");
    return s;
}

    class EchoPass1  extends AstroBaseListener
    {
    
    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
        super.exitEveryRule(ctx);
        if(Castro.getVerbose() >= 2)
            out.println("exit " + getRuleName(parser, ctx, false));
    }
    
    @Override
    public void exitExprIfOp(ExprIfOpContext ctx)
    {
        sb.setLength(0);
        sb.append("IF ")
                .append(removeFromEcho(ctx.paren_expr().expr())).append(' ')
                .append(removeFromEcho(ctx.expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprIfElseOp(ExprIfElseOpContext ctx)
    {
        sb.setLength(0);
        sb.append("IF ")
                .append(removeFromEcho(ctx.paren_expr().expr())).append(' ')
                .append(removeFromEcho(ctx.expr(0))).append(' ')
                .append("ELSE ")
                .append(removeFromEcho(ctx.expr(1)));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprRepeatOp(ExprRepeatOpContext ctx)
    {
        sb.setLength(0);
        sb.append("REPEAT ")
                .append(removeFromEcho(ctx.paren_expr().expr())).append(' ')
                .append(removeFromEcho(ctx.expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprWhileOp(ExprWhileOpContext ctx)
    {
        sb.setLength(0);
        sb.append("WHILE ")
                .append(removeFromEcho(ctx.paren_expr().expr())).append(' ')
                .append(removeFromEcho(ctx.expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprDowhileOp(ExprDowhileOpContext ctx)
    {
        sb.setLength(0);
        sb.append("DO_WHILE ")
                .append(removeFromEcho(ctx.expr())).append(' ')
                .append(removeFromEcho(ctx.paren_expr().expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprForOp(ExprForOpContext ctx)
    {
        sb.setLength(0);
        sb.append("FOR ")
                .append(removeFromEcho(ctx.lval())).append(' ')
                .append("<== ")
                .append(removeFromEcho(ctx.expr(0))).append(' ')
                .append("UNTIL ")
                .append(removeFromEcho(ctx.expr(1))).append(' ')
                .append(removeFromEcho(ctx.expr(2)));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
    {
        List<AstroExprStatementContext> bs = ctx.brace_block().bs;
        sb.setLength(0);
        sb.append("BLOCK(").append(bs.size()).append(") ");
        for(AstroExprStatementContext s : bs) {
            sb.append(removeFromEcho(s.astroExpr().expr())).append(' ');
        }

        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprFunc(ExprFuncContext ctx)
    {
        List<ExprContext> args = ctx.func_call().args;
        sb.setLength(0);
        sb.append("FUNC(").append(args.size()).append(") ");
        for(ExprContext arg : args) {
            sb.append(removeFromEcho(arg)).append(' ');
        }
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprUnOp(ExprUnOpContext ctx)
    {
        sb.setLength(0);
        int opType = ctx.getStart().getType();
        String text = opType == Minus ? "u-" : opType == Plus ? "u+"
                                               : ctx.getStart().getText();
        sb.append(text).append(' ')
                .append(removeFromEcho(ctx.expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprQuestOp(ExprQuestOpContext ctx)
    {
        sb.setLength(0);
        sb.append("?: ").append(removeFromEcho(ctx.expr(0))).append(' ')
                .append(removeFromEcho(ctx.expr(1))).append(' ')
                .append(removeFromEcho(ctx.expr(2))).append(' ');
        putEcho(ctx, sb.toString());
        super.exitExprQuestOp(ctx);
    }
    
    private void expr3(ExprContext ctx)
    {
        sb.setLength(0);
        sb.append(ctx.getChild(1).getText()).append(' ')
                .append(removeFromEcho(ctx.getChild(0))).append(' ')
                .append(removeFromEcho(ctx.getChild(2)));
        putEcho(ctx, sb.toString());
    }

    @Override
    public void exitExprBinOp(ExprBinOpContext ctx)
    {
        expr3(ctx);
    }
    
    @Override
    public void exitExprAssOp(ExprAssOpContext ctx)
    {
        expr3(ctx);
    }
    
    @Override
    public void exitExprTermOp(ExprTermOpContext ctx)
    {
        putEcho(ctx, removeFromEcho(ctx.term()));
    }
    
    @Override
    public void exitTermSingle(TermSingleContext ctx)
    {
        //putEcho(ctx, removeFromEcho(ctx.lval()));
        putEcho(ctx, removeFromEcho(ctx.getChild(0)));
    }

    @Override
    public void exitTermParen(TermParenContext ctx)
    {
        putEcho(ctx, removeFromEcho(ctx.paren_expr().expr()));
    }
    
    @Override
    public void exitInteger(IntegerContext ctx)
    {
        putEcho(ctx, ctx.IntegerConstant().getText());
    }
    
    @Override
    public void exitLvalMem(LvalMemContext ctx)
    {
        putEcho(ctx, ctx.Identifier().getText());
    }

    @Override
    public void exitLvalArray(LvalArrayContext ctx)
    {
        sb.setLength(0);
        sb.append("INDEX").append(' ').append(ctx.Identifier()).append(' ')
                .append(removeFromEcho(ctx.expr()));
        putEcho(ctx, sb.toString());
    }

    @Override
    public void exitLvalIndirect(LvalIndirectContext ctx)
    {
        sb.setLength(0);
        sb.append("INDIR").append(' ').append(ctx.Identifier().getText());
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitTermAddressOf(TermAddressOfContext ctx)
    {
        sb.setLength(0);
        sb.append("ADDR").append(' ').append(ctx.Identifier().getText());
        putEcho(ctx, sb.toString());
        super.exitTermAddressOf(ctx);
    }
    
    }

    /**
     * Output one line per statement of any collected info.
     */
    class ExprStatementDump  extends AstroBaseListener
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
        String s = echo.get(ctx);
        if(s != null)
            walkerCount++;
    }

    void dumpStatement(AstroExprStatementContext ctx)
    {
        out.printf("input: %s\n", ParseTreeUtil.getOriginalText(ctx, input));
        out.printf("    %s %s\n", getRuleName(parser, ctx, true),
                                  removeFromEcho(ctx.astroExpr.expr));
    }
    
    @Override
    public void exitMacro(MacroContext ctx)
    {
        super.exitMacro(ctx);
        List<AstroExprStatementContext> es = ctx.s;

        sb.setLength(0);
        sb.append("=== MACRO ").append(ctx.Identifier().getText());
        if(ctx.addr != null) {
            sb.append("@").append(removeFromEcho(ctx.addr));
            astroExpressionCount++; // weird, but the macro addr counts
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
