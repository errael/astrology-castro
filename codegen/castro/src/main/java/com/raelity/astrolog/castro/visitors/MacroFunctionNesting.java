/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.visitors;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

import com.raelity.astrolog.castro.Castro;
import com.raelity.astrolog.castro.Util;
import com.raelity.astrolog.castro.antlr.AstroParser.*;
import com.raelity.astrolog.castro.tables.Functions;

import static com.raelity.astrolog.castro.Castro.MACRO_FUNC_VERBOSE;
import static com.raelity.astrolog.castro.Util.objectID;

/**
 * Look for nested macro function calls.
 * A macro function with more than one param can not nest, for example
 * "mac(a, mac(b, c))". Zero or one paraam is OK.
 * <p>
 * Get notified on entry/exit on ExprFunc. On entry, if not currently
 * in a function call, record the ctx. On exit, if the ctx matches,
 * then then visit looking for nested uses of the function.
 * <p>
 * Note "mac_1(mac_2(mac_1(mac_2())))" is possible, so the entry ctx is
 * recorded for each macro function name encountered.
 * <p>
 * At any given point in the parse tree, the elements of "rootFuncContexts"
 * contains the names/contexts of active nested calls. 
 * The most nested (deepest) macro function call is checked first and if
 * not the root, i.e. it is nested, then it is an error.
 * At the completion of a macro call check, if
 * it was the root for a given name, it is removed from the roots. This
 * means that if a macro name is encountered and the name does not have
 * an associated root, then any possible nested calls have already been
 * checked against their root and don't have to be checked again.
 * 
 */
public class MacroFunctionNesting extends Visitor<Boolean>
{
/** singleton */
private static MacroFunctionNesting nest = new MacroFunctionNesting();

public static void beginExprFunc(ExprFuncContext ctx)
{
    nest.enterExprFunc(ctx);
}

public static void endExprFunc(ExprFuncContext ctx)
{
    nest.exitExprFunc(ctx);
}

private final boolean debugFlag = Castro.getVerbose() >= MACRO_FUNC_VERBOSE;

/** Map userFunc name to first ctx. Empty when not in a function call */
private Map<String, ExprFuncContext> rootFuncContexts= new HashMap<>();

/** The first function ctx that might have nested function calls. */
private ExprFuncContext primaryRootFuncCtx;

public void enterExprFunc(ExprFuncContext ctx)
{
    String name = ctx.fc.id.getText();
    if(Functions.get(name).isBuiltin())
        return;
    // Looking at a macro function.
    // If zero or one params, then nesting is OK.
    // But the 1 params must be included here, since their param may call.
    if(ctx.fc.args.isEmpty())
        return;
    if(primaryRootFuncCtx == null) {
        primaryRootFuncCtx = ctx;
        debugNL(); debugExprFunc("SET PRIMARY ROOT", ctx);
    }
    if(!rootFuncContexts.containsKey(name)) debugExprFunc("SET MACRO ROOT", ctx);
    rootFuncContexts.putIfAbsent(name, ctx);
}

public void exitExprFunc(ExprFuncContext ctx)
{
    String name = ctx.fc.id.getText();
    if(rootFuncContexts.get(name) == ctx) {
        visitExprFunc(ctx);
        rootFuncContexts.remove(name);
        debugExprFunc("REMOVE MACRO ROOT", ctx);
    }
    if(ctx == primaryRootFuncCtx) {
        if(!rootFuncContexts.isEmpty())
            throw new IllegalStateException("expr funcs not empty");
        primaryRootFuncCtx = null;
        debugExprFunc("REMOVE PRIMARY ROOT", ctx);
    }
}

@Override
public Boolean visitExprFunc(ExprFuncContext ctx)
{
    String name = ctx.fc.id.getText();
    ExprFuncContext thisNameRootFuncCtx = rootFuncContexts.get(name);
    debugExprFunc("CHECKING" + (thisNameRootFuncCtx == ctx ? " root" : ""), ctx);
    // If not a macro under consideration, i.e. the name has no root, return.
    if(thisNameRootFuncCtx == null) {
        debugExprFunc("CHECKING not active", ctx);
        return true;
    }
    if(thisNameRootFuncCtx != ctx && ctx.fc.args.size() > 1)
        Util.reportError(ctx, "Can not nest call: '%s' takes more than one arg", name);
    for(ExprContext arg : ctx.fc.args)
        visitExpr(arg);
    return true;
}

private Boolean visitExpr(ExprContext ctx)
{
    debugVisit(ctx);
    switch(ctx) {
    case ExprFuncContext ctx_ -> { return visitExprFunc(ctx_); }
    case ExprBraceBlockOpContext ctx_ -> { return visitExprBraceBlockOp(ctx_); }
    case null,default -> {}
    }
    // visit all the children expressions
    for(int i = 0; i < ctx.getChildCount(); i++) {
        ParseTree pt = ctx.getChild(i);
        if(pt instanceof ExprContext ctx_)
            visitExpr(ctx_);
    }
    return true;
}

@Override
public Boolean visitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
{
    for(AstroExprStatementContext sc : ctx.bb.bs) {
        visitExpr(sc.astroExpr().e);
    }
    return true;
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void debugExprFunc(String tag, ExprFuncContext ctx)
{
    if(!debugFlag)
        return;
    String name = ctx.fc.id.getText();
    //System.err.printf("%23s %s\n", tag, objectID(name, ctx));
    System.err.printf("%23s %s %s\n", tag, objectID(name, ctx), ctx.getText());
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void debugVisit(ExprContext ctx)
{
    if(!debugFlag)
        return;
    System.err.printf("visit expr: %s\n", objectID(ctx.getText(), ctx));
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void debugNL() {
    if(!debugFlag)
        return;
    System.err.println("");
}

}
