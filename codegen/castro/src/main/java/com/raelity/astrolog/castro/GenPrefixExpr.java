/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser.*;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Function;

import static com.raelity.antlr.ParseTreeUtil.getRuleName;
import static com.raelity.antlr.ParseTreeUtil.hasErrorNode;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.optim.FoldConstants.fold2Int;

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

private boolean optimConstant = true;
private boolean dump = false;

/**
 * 
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private String optimExprPrefixRem(ExprContext ctx, String tag)
{
    String s = apr.prefixExpr.removeFrom(ctx);
    if(!optimConstant)
        return s;

    if(!dump)
        return fold2Int(ctx, s);
    else {
        Integer f2i = fold2Int(ctx);
        if(dump)System.err.printf("%s: '%s' %s\n", tag, s, f2i);
        return f2i != null ? f2i.toString() + ' ' : s;
    }
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
    String p = optimExprPrefixRem(ctx.p.e, "IF p");
    String e = optimExprPrefixRem(ctx.e, "IF et");

    String s = genIfOp(ctx, p, e);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprIfElseOp(ExprIfElseOpContext ctx)
{
    String p = optimExprPrefixRem(ctx.p.e, "IFE p");
    String et = optimExprPrefixRem(ctx.et, "IFE et");
    String ef = optimExprPrefixRem(ctx.ef, "IFE ef");

    String s = genIfElseOp(ctx, p, et, ef);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprRepeatOp(ExprRepeatOpContext ctx)
{
    String p = optimExprPrefixRem(ctx.p.e, "REP p");
    String e = optimExprPrefixRem(ctx.e, "REP e");

    String s = genRepeatOp(ctx, p, e);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprWhileOp(ExprWhileOpContext ctx)
{
    String p = optimExprPrefixRem(ctx.p.e, "WHI p");
    String e = optimExprPrefixRem(ctx.e, "WHI e");

    String s = genWhileOp(ctx, p, e);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprDowhileOp(ExprDowhileOpContext ctx)
{
    String e = optimExprPrefixRem(ctx.e, "DOW e");
    String p = optimExprPrefixRem(ctx.p.e, "DOW p");
    String s = genDoWhileOp(ctx, e, p);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprForOp(ExprForOpContext ctx)
{
    // TODO: lval OPTIM
    String l   = apr.prefixExpr.removeFrom(ctx.l);
    String low = optimExprPrefixRem(ctx.low, "For low");
    String up  = optimExprPrefixRem(ctx.up, "For up");
    String e   = optimExprPrefixRem(ctx.e, "For e");

    String s = genForOp(ctx, l, low, up, e);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
{
    String s = genBraceBlockOp(ctx,
            ctx.bb.bs.stream()
                .map((bsctx) -> optimExprPrefixRem(bsctx.e.e, "BBexpr"))
                .collect(Collectors.toCollection(ArrayList::new)));
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprFunc(ExprFuncContext ctx)
{
    String id = ctx.fc.id.getText();
    Function f = Functions.get(id);
    List<String> args = ctx.fc.args.stream()
            .map((arg) -> f.targetMemSpace() == null
                          ? optimExprPrefixRem(arg, "FArg")
                          : apr.prefixExpr.removeFrom(arg))
            .collect(Collectors.toList());
    String s = genFuncCallOp(ctx, id, args);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprUnOp(ExprUnOpContext ctx)
{
    String e = optimExprPrefixRem(ctx.e, "UnOp");

    String s = genUnOp(ctx, ctx.o, e);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprQuestOp(ExprQuestOpContext ctx)
{
    String ec = optimExprPrefixRem(ctx.ec, "EQC c");
    String et = optimExprPrefixRem(ctx.et, "EQC et");
    String ef = optimExprPrefixRem(ctx.ef, "EQC ef");

    String s = genQuestColonOp(ctx, ec, et, ef);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprBinOp(ExprBinOpContext ctx)
{
    String l = optimExprPrefixRem(ctx.l, "BinOp l");
    String r = optimExprPrefixRem(ctx.r, "BinOp r");

    String s = genBinOp(ctx, ctx.o, l, r);
    apr.prefixExpr.put(ctx, s);
}

@Override
public void exitExprAssOp(ExprAssOpContext ctx)
{
    // TODO: lval OPTIM
    String l = apr.prefixExpr.removeFrom(ctx.l);
    String e = optimExprPrefixRem(ctx.e, "AssOp");

    String s = genAssOp(ctx, ctx.ao, l, e);
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

// TODO: lval OPTIM
@Override
public void exitLvalArray(LvalArrayContext ctx)
{
    // array index expr is needed more than once
    // in the case where it is used as the target of an assignment
    // or if the address &arr[expr] is taken.
    String expr = apr.prefixExpr.removeFrom(ctx.idx);
    //String expr = optimExprPrefixRem(ctx.idx, "LVidx");
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
    String s = ctx.s != null
             ? genString(ctx.s)
             : apr.prefixExpr.removeFrom(ctx.e); // bring up the expr
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
    apr.prefixExpr.put(ctx, apr.prefixExpr.removeFrom(ctx.t));
}

@Override
public void exitTermSingle(TermSingleContext ctx)
{
    apr.prefixExpr.put(ctx, apr.prefixExpr.removeFrom(ctx.getChild(0)));
}

@Override
public void exitTermParen(TermParenContext ctx)
{
    String e = apr.prefixExpr.removeFrom(ctx.p.e);
    //String e = optimExpr(ctx.p.e, "TermParen");
    apr.prefixExpr.put(ctx, e);
}
}
    
