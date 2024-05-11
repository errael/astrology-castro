/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.visitors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTree;

import com.raelity.astrolog.castro.AstroParseResult;
import com.raelity.astrolog.castro.Castro;
import com.raelity.astrolog.castro.TreeProps;
import com.raelity.astrolog.castro.Util;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprBinOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprTermOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermParenContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermSingleContext;
import com.raelity.astrolog.castro.visitors.BinaryOpOptim.BinOpStatus;
import com.raelity.astrolog.castro.visitors.BinaryOpOptim.OpPrecedence;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.objectID;
import static com.raelity.astrolog.castro.Util.sf;
import static com.raelity.astrolog.castro.antlr.AstroLexer.*;
import static com.raelity.astrolog.castro.visitors.BinaryOpOptim.OpPrecedence.*;
import static com.raelity.astrolog.castro.visitors.FoldConstants.fold2Int;

/**
 * For BinOp, create list of ExprContext where order of evaluation doesn't matter.
 * The idea is to reorder the list and collapse all the constants.
 * <p>
 * Can only be used after pass1 has completed.
 * <p>
 * This is a hack, better to rewrite a tree; then could be more general.
 * <p>
 * ONLY HANDLES +/- for now. 
 */
public class BinaryOpOptim extends Visitor<BinOpStatus>
{
private static final boolean debugFlag = Castro.getVerbose() >= 2;
private static BinaryOpOptim INSTANCE;
private static AstroParseResult apr;

/** sub trees with all elements of equal precedence */
private static TreeProps<BinOpStatus>opStatus = new TreeProps<>();

private static void init()
{
    if(INSTANCE != null)
        return;
    INSTANCE = new BinaryOpOptim();
    apr = lookup(AstroParseResult.class);
}

public static void check(ExprBinOpContext ctx)
{
    if(Castro.getOptimize() < 2)
        return;
    init();
    INSTANCE.methodCheck(ctx);
}

public static String optimize(ExprBinOpContext ctx)
{
    if(Castro.getOptimize() < 2)
        return null;
    init();
    return INSTANCE.methodOptimize(ctx);
}

public void methodCheck(ExprBinOpContext ctx)
{
    boolean cached = opStatus.getMap().containsKey(ctx);
    debugNL(); debug(() -> "CHECK" + (cached ? " CACHED" : ""), ctx);
    if(cached)
        return;
    BinOpStatus status = new BinaryOpOptim().visitExprBinOp(ctx);
    debug("CHECK RESULT", status);
    debugMap();
}

public String methodOptimize(ExprBinOpContext ctx)
{
    if(Castro.getOptimize() < 2)
        return null;
    BinOpStatus status = opStatus.get(ctx);
    if(status == null || status.prec() != PrecPlusMinus || fold2Int(ctx) != null)
        return null;
    if(debugFlag) {
        debugNL(); debug("OPTIMIZE", status);
        for(Operand operand : status.list()) {
            String s = apr.getCachedPrefixExprProps().get(operand.ctx);
            Objects.requireNonNull(s);
            debug(opToString(operand.opType()), operand.ctx.getText(), s, operand.ctx);
        }
    }
    try {
        List<Operand> operands = new ArrayList<>();
        int nConstant = 0;
        long constant = 0;
        // Split into list of exprs and count and accumulate constants
        for(Operand operand : status.list()) {
            Integer foldedInt = fold2Int(operand.ctx);
            if(foldedInt == null)
                operands.add(operand);
            else {
                nConstant++;
                if(operand.opType == Minus)
                    constant -= foldedInt;
                else
                    constant += foldedInt;
                if(Util.isOverflow(constant))
                    return null;
            }
        }

        if(nConstant == 0)
            return null;

        StringBuilder sb = new StringBuilder();
        boolean useIncDec = Math.abs(constant) == 1;
        // operands.size() is one less than the number of operands added;
        // the last operand is the constant.
        // If useIncDec then the first operand is "Inc"/"Dec" and the
        // constant is implicit and not needed.

        // The first operand is for the constant,
        // If not inc/dec then use "Add" because the constant is output signed
        if(useIncDec)
            sb.append(constant < 0 ? "Dec " : "Inc ");
        else
            sb.append("Add ");

        // traverse the operand list in reverse order to generate
        // the operands. Reverse for prefix notation.

        for(int i = operands.size() - 1; i > 0; i--)
            sb.append(operands.get(i).opType == Minus ? "Sub " : "Add ");


        for(Operand operand : operands)
            sb.append(apr.getCachedPrefixExprProps().get(operand.ctx));

        if(!useIncDec)
            sb.append(constant).append(' ');

        if(debugFlag)
            debug(sf("optim: %s %d '%s'\n",
                          operands.stream().map((op)->op.ctx.getText()).
                                  collect(Collectors.toList()),
                          constant, sb.toString()));
        return sb.toString();
    } catch(Exception ex) {
        return null;
    }
}

record Operand(ExprContext ctx, int opType)
{
    Operand(ExprContext ctx)
    {
        this(ctx, 0);
    }
    @Override
    public String toString()
    {
        return "Operand[ctx="+ctx.getText()+", opType=" + opType + "]";
    }
}

/** opType needed for precedence with multiple opTypes: PrecedencePlusMinus */
record BinOpStatus(OpPrecedence prec,
                          List<Operand> list,
                          Operand baseOperand,
                          int opType)
{
    BinOpStatus(OpPrecedence prec,
                          List<Operand> list,
                          Operand baseOperand) {
        this(prec, list, baseOperand, 0);
    }

    //BinOpStatus(BinOpStatus status, int opType)
    //{
    //    this(status.prec(), status.list(), status.baseOperand(), opType);
    //}

    BinOpStatus(OpPrecedence prec, ExprContext ctx)
    {
        // TODO: "Operand operand = new Operand(ctx)", then use it.
        this(prec, List.of(new Operand(ctx)), new Operand(ctx), 0);
    }

    List<String> textList()
    {
        return list().stream()
                .map((operand) -> operand.ctx.getText())
                .collect(Collectors.toList());
    }
};

/** Only track ExprBinOpContext */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void recordContextStatus(ExprContext ctx, BinOpStatus status)
{
    if(ctx instanceof ExprBinOpContext) {
        if(!opStatus.getMap().containsKey(ctx))
            opStatus.put(ctx, status);
        else
            debug("DISCARD duplicate");
    } else
        debug("record not BinOp");
}

public enum OpPrecedence {
PrecPlusMinus(true),
PrecAndAnd(false),
PrecOrOr(false),
// "None" expr can be part of any BinOp
None(false);

//Term(false),

public final boolean canOptim;      // TODO: looks like this isn't needed
private OpPrecedence(boolean f)
{
    canOptim = f;
}

// /** Is this an optimizable BinaryOp */
// boolean canOptim() {
//     return canOptim;
// }

}; /////////////////////// enum OpPrecedence

OpPrecedence opPrecedence(ExprContext ctx) {
    OpPrecedence retStatus = None;
    if(ctx instanceof ExprBinOpContext ctx_)
        retStatus = switch(ctx_.o.getType()) {
        case Plus, Minus -> PrecPlusMinus;
        case AndAnd -> PrecAndAnd;
        case OrOr -> PrecOrOr;
        default -> None;
        };
    return retStatus;
}

int negate(int opType)
{
    return opType == Minus ? Plus : Minus;
}
Operand negate(Operand operand)
{
    return new Operand(operand.ctx, negate(operand.opType));
}

private void addToOperandList(OpPrecedence prec,
                           List<Operand> list, BinOpStatus status,
                           int opType)
{
    if(opType != Minus) {
        if(prec == status.prec())
            list.addAll(status.list());
        else
            list.add(status.baseOperand());
    } else {
        if(prec == status.prec())
            list.addAll(status.list().stream()
                    .map((operand) -> negate(operand))
                    .collect(Collectors.toList())
            );
        else
            list.add(negate(status.baseOperand()));
    }
}

@Override
@SuppressWarnings("NonPublicExported")
public BinOpStatus visitExprBinOp(ExprBinOpContext ctx)
{
    BinOpStatus retStatus = opStatus.get(ctx);
    debug("VISIT" + (retStatus != null ? " CACHED" : ""), ctx);
    if(retStatus != null)
        return retStatus;
 
    retStatus = switch(opPrecedence(ctx)) {
    case PrecPlusMinus -> {
        BinOpStatus l = visitExpr(ctx.l);
        BinOpStatus r = visitExpr(ctx.r);

        List<Operand> list = new ArrayList<>();
        addToOperandList(opPrecedence(ctx), list, l, 0);
        addToOperandList(opPrecedence(ctx), list, r, ctx.o.getType());

        yield new BinOpStatus(PrecPlusMinus, list, new Operand(ctx));
    }
    // The following are essentially ignored for optimization;
    // they use a shortcut BinOpStatus. At some point might want like
    //      Operand operand = new Operand(ctx);
    //      yield new BinOpStatus(PrecAndAnd, List.of(operand), operand);

    //case PrecAndAnd, PrecOrOr
    default -> {
        yield new BinOpStatus(opPrecedence(ctx), ctx);
    }
    };
    debug(() -> "VISIT BINOP " + ctx.o.getText(), retStatus);
    recordContextStatus(ctx, retStatus);
    return retStatus;
}

private BinOpStatus visitExpr(ExprContext ctx)
{
    BinOpStatus status = switch(ctx) {
    case ExprBinOpContext ctx_ -> visitExprBinOp(ctx_);
    case ExprTermOpContext ctx_ -> {
        yield switch(ctx_.t) {
        case TermSingleContext ctx1_ -> { Objects.isNull(ctx1_);
                yield new BinOpStatus(None, ctx);
        }
        case TermParenContext ctx1_ -> {
            yield visitExpr(ctx1_.p.e);
        }
        case null,default -> new BinOpStatus(None, ctx);
        };
    }
    case null,default -> new BinOpStatus(None, ctx);
    };
    return status;
}

private String opToString(int op)
{
    return switch(op) {
    case Minus -> "-";
    case Plus -> "+";
    default -> " ";
    };
}

private void debug(Supplier<String> tag, BinOpStatus status)
{
    if(!debugFlag)
        return;
    debug(tag.get(), status);
}
private void debug(String tag, BinOpStatus status)
{
    if(!debugFlag)
        return;
    List<String> data = status.textList();
    debug(tag, status.prec().toString(), data.toString(), status);
}

private void debug(Supplier<String> tag, ExprBinOpContext ctx)
{
    if(!debugFlag)
        return;
    debug(tag.get(), ctx);
}
private void debug(String tag, ExprBinOpContext ctx)
{
    if(!debugFlag)
        return;
    debug(tag, ctx.o.getText(), ctx.getText(), ctx);
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void debug(String tag, String textID, String textData, Object ctx)
{
    if(!debugFlag)
        return;
    System.err.printf("%23s '%s' %s %s\n", tag, textID, objectID(ctx), textData);
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void debug(String msg) {
    if(!debugFlag)
        return;
    System.err.println(msg);
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void debugNL() {
    if(!debugFlag)
        return;
    System.err.println("");
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void debugMap() {
    if(!debugFlag)
        return;
    for(Entry<ParseTree, BinOpStatus> entry : opStatus.getMap().entrySet()) {
        System.err.printf("%23s %s %s\n", "MAP",
                          objectID(entry.getValue().prec().toString(), entry.getKey()),
                          entry.getValue().textList().toString());
    }
}


}
