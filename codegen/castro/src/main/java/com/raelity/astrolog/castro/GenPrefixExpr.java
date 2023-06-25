/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.antlr.AstroBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.*;

import static com.raelity.antlr.ParseTreeUtil.getRuleName;
import static com.raelity.astrolog.castro.Util.lookup;

/**
 * Process the tree so that each top level expr node has a String property
 which is the prefix notation code for it's expr; using putToPrefixExpr()
 and removeFromPrefixExpr, this class manages
 the {@code prefixCode} properties and subclass provide the data.
 * A top level expr node has no expr ascendants. 
 * At each expr node, invoke an abstract method with the node's tokens
 * and the children expressions; the subclass generates and returns the
 * node's expression; the children expr node data is removed.
 * <p>
 * The general strategy is that an exit listener sets an expr's
 * prefixCode string incorporating it's children. Repeat until only the
 * top level exprs have prefixCode.
 */
public abstract class GenPrefixExpr extends AstroBaseListener
{

final AstroParseResult apr;

public GenPrefixExpr(AstroParseResult apr)
{
    this.apr = apr;
}

abstract String genIfOp(String condition, String if_true);
abstract String genIfElseOp(String condition, String if_true, String if_false);
abstract String genRepeatOp(String count, String statement);
abstract String genWhileOp(String condition, String statement);
abstract String genDoWhileOp(String statement, String condition);
abstract String genForOp(String counter, String begin, String end, String statement);
abstract String genBraceBlockOp(List<String> statements);
abstract String genFuncCallOp(String funcName, List<String> args);
abstract String genQuestColonOp(String condition, String if_true, String if_false);
abstract String genUnOp(Token opToken, String expr);
abstract String genBinOp(Token opToken, String lhs, String rhs);
abstract String genAssOp(Token opToken, String lhs, String rhs);
abstract String genLval(LvalContext ctx, String... expr);
abstract String genAddr(TermAddressOfContext ctx);

private static PrintWriter getOut()
{
    return lookup(CastroOut.class).pw;
}

@Override
public void exitEveryRule(ParserRuleContext ctx)
{
    super.exitEveryRule(ctx);
    if(Castro.getVerbose() >= 2)
        getOut().println("exit " + getRuleName(apr.getParser(), ctx, false));
}

@Override
public void exitExprIfOp(ExprIfOpContext ctx)
{
    String s = genIfOp(apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                       apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprIfElseOp(ExprIfElseOpContext ctx)
{
    String s = genIfElseOp(apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                           apr.prefixExpr.removeFrom(ctx.expr(0)),
                           apr.prefixExpr.removeFrom(ctx.expr(1)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprRepeatOp(ExprRepeatOpContext ctx)
{
    String s = genRepeatOp(apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                           apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprWhileOp(ExprWhileOpContext ctx)
{
    String s = genWhileOp(apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                          apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprDowhileOp(ExprDowhileOpContext ctx)
{
    String s = genDoWhileOp(apr.prefixExpr.removeFrom(ctx.expr()),
                            apr.prefixExpr.removeFrom(ctx.paren_expr().expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprForOp(ExprForOpContext ctx)
{
    String s = genForOp(apr.prefixExpr.removeFrom(ctx.lval()),
                        apr.prefixExpr.removeFrom(ctx.expr(0)),
                        apr.prefixExpr.removeFrom(ctx.expr(1)),
                        apr.prefixExpr.removeFrom(ctx.expr(2)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
{
    String s = genBraceBlockOp(ctx.brace_block().bs.stream()
            .map((bsctx) -> apr.prefixExpr.removeFrom(bsctx.astroExpr().expr()))
            .collect(Collectors.toList()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprFunc(ExprFuncContext ctx)
{
    String s = genFuncCallOp(ctx.func_call().Identifier().getText(),
                             ctx.func_call().args.stream()
                                     .map((arg) -> apr.prefixExpr.removeFrom(arg))
                                     .collect(Collectors.toList()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprUnOp(ExprUnOpContext ctx)
{
    String s = genUnOp(((TerminalNode)ctx.getChild(0)).getSymbol(),
                       apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprQuestOp(ExprQuestOpContext ctx)
{
    String s = genQuestColonOp(apr.prefixExpr.removeFrom(ctx.expr(0)),
                               apr.prefixExpr.removeFrom(ctx.expr(1)),
                               apr.prefixExpr.removeFrom(ctx.expr(2)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprBinOp(ExprBinOpContext ctx)
{
    String s = genBinOp(((TerminalNode)ctx.getChild(1)).getSymbol(),
                        apr.prefixExpr.removeFrom(ctx.getChild(0)),
                        apr.prefixExpr.removeFrom(ctx.getChild(2)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprAssOp(ExprAssOpContext ctx)
{
    String s = genAssOp(((TerminalNode)ctx.getChild(1)).getSymbol(),
                        apr.prefixExpr.removeFrom(ctx.getChild(0)),
                        apr.prefixExpr.removeFrom(ctx.getChild(2)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprTermOp(ExprTermOpContext ctx)
{
    apr.prefixExpr.put(ctx, apr.prefixExpr.removeFrom(ctx.term()));
}

@Override
public void exitTermSingle(TermSingleContext ctx)
{
    apr.prefixExpr.put(ctx, apr.prefixExpr.removeFrom(ctx.getChild(0)));
}

@Override
public void exitTermParen(TermParenContext ctx)
{
    apr.prefixExpr.put(ctx, apr.prefixExpr.removeFrom(ctx.paren_expr().expr()));
}

@Override
public void exitTermAddressOf(TermAddressOfContext ctx)
{
    String s = genAddr(ctx);
    apr.prefixExpr.put(ctx, s);
}

// TODO: tracked as an integer?
@Override
public void exitInteger(IntegerContext ctx)
{
    apr.prefixExpr.put(ctx, ctx.IntegerConstant().getText());
}

@Override
public void exitLvalMem(LvalMemContext ctx)
{
    String s = genLval(ctx);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitLvalArray(LvalArrayContext ctx)
{
    String s = genLval(ctx, apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitLvalIndirect(LvalIndirectContext ctx)
{
    String s = genLval(ctx);
    apr.prefixExpr.put(ctx, s);
}

}
    
