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

import java.util.List;
import java.util.Objects;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.*;

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
private CastroEcho(AstroParser parser, ProgramContext program) {
    this.parser = parser;
    this.program = program;
}

StringBuilder sb = new StringBuilder();

static String getPrefixNotation(AstroParser parser, ProgramContext program)
{
    CastroEcho castroEcho = new CastroEcho(parser, program);
    return castroEcho.doEcho();
}

record DumpCounts(int walker, int statement){};

@SuppressWarnings("UseOfSystemOutOrSystemErr")
String doEcho()
{
    EchoPass1 echoPass1 = new EchoPass1();
    walker.walk(echoPass1, program);

    sb.setLength(0);
    EchoDump echoDump = new EchoDump();
    DumpCounts dump = echoDump.dump();
    if(dump.walker() != dump.statement() || echo.size() != 0) {
        System.out.printf(
                "PARSE ERROR: statements %d, found %d. echos left %d\n",
                dump.statement(), dump.walker(), echo.size());
    }

    return sb.toString();
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void putEcho(ParseTree ctx, String s)
{
    if(Castro.getVerbose() >= 2)
        System.out.printf("Saving: %08x %s\n", System.identityHashCode(ctx), s);
    if(s == null)
        Objects.requireNonNull(s, "putEcho");
    echo.put(ctx, s);
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private String removeFromEcho(ParseTree ctx)
{
    String s = echo.removeFrom(ctx);
    if(Castro.getVerbose() >= 2)
        System.out.printf("Remove: %08x %s\n", System.identityHashCode(ctx), s);
    if(s == null)
        Objects.requireNonNull(s, "putEcho");
    return s;
}

String rn(RuleContext ctx, boolean useBrackets)
{
    String s;
    if(useBrackets)
        s = '[' + parser.getRuleNames()[ctx.getRuleIndex()] + ']';
    else
        s = parser.getRuleNames()[ctx.getRuleIndex()];
    return s;
}

class EchoPass1  extends AstroBaseListener
    {
    
    @Override
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void enterEveryRule(ParserRuleContext ctx)
    {
        if(Castro.getVerbose() >= 2)
            System.out.println("enter " + rn(ctx, false));
    }
    
    @Override
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void exitEveryRule(ParserRuleContext ctx)
    {
        if(Castro.getVerbose() >= 2)
            System.out.println("exit " + rn(ctx, false));
    }
    
    @Override
    public void exitExprIfOp(ExprIfOpContext ctx)
    {
        sb.setLength(0);
        sb.append("IF ")
                .append(removeFromEcho(ctx.paren_expr().expr())).append(' ')
                .append(removeFromEcho(ctx.trailing_expr_block().expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprIfElseOp(ExprIfElseOpContext ctx)
    {
        sb.setLength(0);
        sb.append("IF ")
                .append(removeFromEcho(ctx.paren_expr().expr())).append(' ')
                .append(removeFromEcho(ctx.expr_block().expr())).append(' ')
                .append("ELSE ")
                .append(removeFromEcho(ctx.trailing_expr_block().expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprWhileOp(ExprWhileOpContext ctx)
    {
        sb.setLength(0);
        sb.append("WHILE ")
                .append(removeFromEcho(ctx.paren_expr().expr())).append(' ')
                .append(removeFromEcho(ctx.trailing_expr_block().expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprDowhileOp(ExprDowhileOpContext ctx)
    {
        sb.setLength(0);
        sb.append("DO_WHILE ")
                .append(removeFromEcho(ctx.expr_block().expr())).append(' ')
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
                .append(removeFromEcho(ctx.trailing_expr_block().expr()));
        putEcho(ctx, sb.toString());
    }
    
    @Override
    public void exitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
    {
        List<ExprContext> l = ctx.brace_block().bs;
        sb.setLength(0);
        sb.append("BLOCK(").append(l.size()).append(") ");
        for(ExprContext ec : l) {
            sb.append(removeFromEcho(ec)).append(' ');
        }
        putEcho(ctx, sb.toString());
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
        sb.append(ctx.Identifier().getText()).append('[')
                .append(removeFromEcho(ctx.expr())).append(']');
        putEcho(ctx, sb.toString());
    }

    @Override
    public void exitLvalIndirect(LvalIndirectContext ctx)
    {
        sb.setLength(0);
        sb.append('@').append(ctx.Identifier().getText());
        putEcho(ctx, sb.toString());
    }

    }

    /**
     * record one line per statement of any collected info
     */
    class EchoDump  extends AstroBaseListener
    {
    int walkerCount;
    int statementCount;

    DumpCounts dump()
    {
        walker.walk(this, program);
        return new DumpCounts(walkerCount, statementCount);
    }
    
    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
        String s = echo.get(ctx);
        if(s != null) {
            walkerCount++;
            //if(newLine)
            //    sb.append("    ");
            //sb.append(rn(ctx, true)).append(s).append(' ');
            //newLine = false;
        }
    }
    
    @Override
    public void exitStatement(StatementContext ctx)
    {
        sb.append(rn(ctx, true)).append(' ')
                .append(removeFromEcho(ctx.expr)).append('\n');
        statementCount++;
        //newLine = true;
    }
    
    }

}
