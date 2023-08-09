/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.*;

import static com.raelity.antlr.ParseTreeUtil.getRuleName;
import static com.raelity.antlr.ParseTreeUtil.hasErrorNode;
import static com.raelity.astrolog.castro.Util.lookup;

/**
 * Process the tree so that each top level expr node has a String property
 which is the prefix notation code for it's expr; using prefixExpr.put()
 and prefixExpr.removeFrom(), this class manages
 the {@code prefixExpr} properties and subclass provide the data.
 * A top level expr node has no expr ascendants. 
 * At each expr node, invoke an abstract method with the node's tokens
 * and the children expressions; the subclass generates and returns the
 * node's expression; the children expr node data is removed.
 * <p>
 * The general strategy is that an exit listener sets an expr's
 * prefixExpr string incorporating it's children. Repeat until only the
 * top level exprs have prefixExpr.
 */
public abstract class GenPrefixExpr extends AstroParserBaseListener
{

final AstroParseResult apr;
final TreeProps<String> lvalArrayIndex = new TreeProps<>(); // OUCH
/** May need to look  at the expressions individually later; NOTE static.
 * Can contain info from multiple parse trees.
 */
final static TreeProps<List<String>> switchCommandExpressions = new TreeProps<>();

public GenPrefixExpr(AstroParseResult apr)
{
    this.apr = apr;
}

// There is "genSw_cmdString(Switch_cmdContext ctx)" which can be overriden
abstract String genSw_cmdExpr_arg(Switch_cmdContext ctx,
                                  List<String> bs);
abstract String genSw_cmdName(Switch_cmdContext ctx,
                              String name, List<String> bs);
abstract String genSw_cmdStringAssign(Switch_cmdContext ctx,
                                String name);

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

abstract String genLval(LvalMemContext ctx);
abstract String genLval(LvalIndirectContext ctx);
abstract String genLval(LvalArrayContext ctx, String expr);
abstract String genInteger(IntegerContext ctx);
abstract String genFloat(FloatContext ctx);

abstract String genAddr(TermAddressOfContext ctx);

private static PrintWriter getOut()
{
    return lookup(CastroIO.class).pw();
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
                .map((bsctx) -> apr.prefixExpr.removeFrom(bsctx.e.e))
                .collect(Collectors.toCollection(ArrayList::new)));
        apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprFunc(ExprFuncContext ctx)
{
    String s = genFuncCallOp(ctx,
                             ctx.fc.id.getText(),
                             ctx.fc.args.stream()
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
public void exitTermAddressOf(TermAddressOfContext ctx)
{
    String s = genAddr(ctx);
    apr.prefixExpr.put(ctx, s);
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
    // array index expr is needed more than once
    // in the case where it is used as the target of an assignment
    String expr = apr.prefixExpr.removeFrom(ctx.idx);
    lvalArrayIndex.put(ctx, expr);
    String s = genLval(ctx, expr);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitLvalIndirect(LvalIndirectContext ctx)
{
    String s = genLval(ctx);
    apr.prefixExpr.put(ctx, s);
}

// TODO: tracked as an integer?
@Override
public void exitInteger(IntegerContext ctx)
{
    String s = genInteger(ctx);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitFloat(FloatContext ctx)
{
    String s = genFloat(ctx);
    apr.prefixExpr.put(ctx, s);
}

/** switch_cmd has 3 forms, string form just sets the name;
 * the 2 others have abstract gen*().
 */
@Override
public void exitSwitch_cmd(Switch_cmdContext ctx)
{
    List<String> bs = ctx.bs.stream()
            .map((bsctx) -> apr.prefixExpr.removeFrom(bsctx.e.e))
            .collect(Collectors.toCollection(ArrayList::new));
    if(!bs.isEmpty())
        switchCommandExpressions.put(ctx, bs);
    String s = ctx.string != null ? genSw_cmdString(ctx)
        : ctx.expr_arg != null ? genSw_cmdExpr_arg(ctx, bs)
          : ctx.name != null ? genSw_cmdName(ctx, apr.prefixExpr.removeFrom(ctx.name), bs)
            : ctx.assign != null ? genSw_cmdStringAssign(ctx, apr.prefixExpr.removeFrom(ctx.l))
              : null;
    if(s == null) {
        s = "#switch_cmdERROR#";
        if(!hasErrorNode(ctx))
            throw new IllegalArgumentException("#switch_cmdERROR#");
    }
    apr.prefixExpr.put(ctx, s);
}

protected String genSw_cmdString(Switch_cmdContext ctx)
{
    return ctx.getText();
}

protected String genString(Token token)
{
    return token.getText();
}

/**
 * Might be a string or an expr; bring up and expr, build a string.
 */
@Override
public void exitStr_expr(Str_exprContext ctx)
{
    if(ctx.s != null) {
        String s = genString(ctx.s);
        apr.prefixExpr.put(ctx, s);
        return;
    }

    // bring up the expr
    String s = apr.prefixExpr.removeFrom(ctx.e);
    apr.prefixExpr.put(ctx, s);
}

// The following simply pull up strings, no transformation through gen*().

@Override
public void exitSw_name(Sw_nameContext ctx)
{
    apr.prefixExpr.put(ctx, ctx.getText());
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
}
    
