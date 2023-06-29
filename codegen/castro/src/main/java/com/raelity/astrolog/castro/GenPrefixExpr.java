/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.antlr.AstroLexer;
import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
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
public abstract class GenPrefixExpr extends AstroParserBaseListener
{

final AstroParseResult apr;

public GenPrefixExpr(AstroParseResult apr)
{
    this.apr = apr;
}

abstract String genSwitch(SwitchContext ctx,
                          List<String> l, String joined,
                          boolean hasQuote1, boolean hasQuote2);
abstract String genIfOp(ExprIfOpContext ctx,
                        String condition, String if_true);
abstract String genIfElseOp(ExprIfElseOpContext ctx,
                            String condition, String if_true, String if_false);
abstract String genRepeatOp(ExprRepeatOpContext ctx,
                            String count, String statement);
abstract String genWhileOp(ExprWhileOpContext ctx,
                           String condition, String statement);
abstract String genDoWhileOp(ExprDowhileOpContext ctx,
                             String statement, String condition);
abstract String genForOp(ExprForOpContext ctx,
                         String counter, String begin, String end, String statement);
abstract String genBraceBlockOp(ExprBraceBlockOpContext ctx,
                                List<String> statements);
abstract String genFuncCallOp(ExprFuncContext ctx,
                              String funcName, List<String> args);
abstract String genQuestColonOp(ExprQuestOpContext ctx,
                                String condition, String if_true, String if_false);
abstract String genUnOp(ExprUnOpContext ctx,
                        Token opToken, String expr);
abstract String genBinOp(ExprBinOpContext ctx,
                         Token opToken, String lhs, String rhs);
abstract String genAssOp(ExprAssOpContext ctx,
                         Token opToken, String lhs, String rhs);
abstract String genLval(LvalContext ctx,
                        String... expr);
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
    String s = genIfOp(ctx,
                       apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                       apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprIfElseOp(ExprIfElseOpContext ctx)
{
    String s = genIfElseOp(ctx,
                           apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                           apr.prefixExpr.removeFrom(ctx.expr(0)),
                           apr.prefixExpr.removeFrom(ctx.expr(1)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprRepeatOp(ExprRepeatOpContext ctx)
{
    String s = genRepeatOp(ctx,
                           apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                           apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprWhileOp(ExprWhileOpContext ctx)
{
    String s = genWhileOp(ctx,
                          apr.prefixExpr.removeFrom(ctx.paren_expr().expr()),
                          apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprDowhileOp(ExprDowhileOpContext ctx)
{
    String s = genDoWhileOp(ctx,
                            apr.prefixExpr.removeFrom(ctx.expr()),
                            apr.prefixExpr.removeFrom(ctx.paren_expr().expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprForOp(ExprForOpContext ctx)
{
    String s = genForOp(ctx,
                        apr.prefixExpr.removeFrom(ctx.lval()),
                        apr.prefixExpr.removeFrom(ctx.expr(0)),
                        apr.prefixExpr.removeFrom(ctx.expr(1)),
                        apr.prefixExpr.removeFrom(ctx.expr(2)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
{
    String s = genBraceBlockOp(ctx,
            ctx.brace_block().bs.stream()
                .map((bsctx) -> apr.prefixExpr.removeFrom(bsctx.astroExpr().expr()))
                .collect(Collectors.toCollection(ArrayList::new)));
        apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprFunc(ExprFuncContext ctx)
{
    String s = genFuncCallOp(ctx,
                             ctx.func_call().func_name().getText(),
                             ctx.func_call().args.stream()
                                     .map((arg) -> apr.prefixExpr.removeFrom(arg))
                                     .collect(Collectors.toList()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprUnOp(ExprUnOpContext ctx)
{
    String s = genUnOp(ctx,
                       ((TerminalNode)ctx.getChild(0)).getSymbol(),
                       apr.prefixExpr.removeFrom(ctx.expr()));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprQuestOp(ExprQuestOpContext ctx)
{
    String s = genQuestColonOp(ctx,
                               apr.prefixExpr.removeFrom(ctx.expr(0)),
                               apr.prefixExpr.removeFrom(ctx.expr(1)),
                               apr.prefixExpr.removeFrom(ctx.expr(2)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprBinOp(ExprBinOpContext ctx)
{
    String s = genBinOp(ctx,
                        ((TerminalNode)ctx.getChild(1)).getSymbol(),
                        apr.prefixExpr.removeFrom(ctx.getChild(0)),
                        apr.prefixExpr.removeFrom(ctx.getChild(2)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprAssOp(ExprAssOpContext ctx)
{
    String s = genAssOp(ctx,
                        ((TerminalNode)ctx.getChild(1)).getSymbol(),
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

@Override
public void exitSwitch(SwitchContext ctx)
{
    List<String> l = new ArrayList<>(ctx.q.size());
    boolean hasQuote1 = false;
    boolean hasQuote2 = false;
    for(int i = 0; i < ctx.q.size(); i++) {
        Token token = ctx.q.get(i);
        String s = token.getText();
        if(token.getType() == AstroLexer.Stuff) {
            s = s.replaceAll("[\n\r ]+", " ");
            // get rid of any escape for including a '>'
            s = s.replaceAll(Matcher.quoteReplacement("\\>"), ">");
        } else {
            if(s.startsWith("'"))
                hasQuote1 = true;
            else
                hasQuote2 = true;
        }
        l.add(s);
    }
    String joined = String.join("", l).trim();
    String s = genSwitch(ctx, l, joined, hasQuote1, hasQuote2);
    apr.prefixExpr.put(ctx, s);
}
}
    
