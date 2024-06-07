/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.visitors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.raelity.astrolog.castro.AstroParseResult;
import com.raelity.astrolog.castro.Castro;
import com.raelity.astrolog.castro.TreeProps;
import com.raelity.astrolog.castro.Util;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprBinOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprTermOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprUnOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermParenContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermSingleContext;
import com.raelity.astrolog.castro.visitors.OperatorOptim.OperatorState;
import com.raelity.astrolog.castro.visitors.OperatorOptim.OpPrecedence;

import static com.raelity.astrolog.castro.Castro.BINOP_OPTIM_VERBOSE;
import static com.raelity.astrolog.castro.Util.getDisplayName;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.objectID;
import static com.raelity.astrolog.castro.Util.sf;
import static com.raelity.astrolog.castro.antlr.AstroParser.AndAnd;
import static com.raelity.astrolog.castro.antlr.AstroParser.Minus;
import static com.raelity.astrolog.castro.antlr.AstroParser.OrOr;
import static com.raelity.astrolog.castro.antlr.AstroParser.Plus;
import static com.raelity.astrolog.castro.visitors.OperatorOptim.OpPrecedence.*;
import static com.raelity.astrolog.castro.visitors.FoldConstants.fold2Int;

// OperatorState has "OpPrecedence" PrecPlusMinus, PrecAndAnd, PrecOrOr, ...
// and the operands have a sign. So
//       a + b   ::: PrecPlusMinus: (+a), (+b)
//       a - b   ::: PrecPlusMinus: (+a), (-b)
//      -a + b   ::: PrecPlusMinus: (-a), (+b)
//      -a - b   ::: PrecPlusMinus: (-a), (-b)
//
//      +a + b   ::: PrecPlusMinus: (+a), (+b)
//      +a - b   ::: PrecPlusMinus: (+a), (-b)
//     --a + b   ::: PrecPlusMinus: (+a), (+b)
//     --a - b   ::: PrecPlusMinus: (+a), (-b)

/**
 * For a given operator sub-tree, create an ordered list of
 * {@linkplain Operand}s (an operand has  sign) at the
 * same operator precedence.
 * With +/-, it's operands may be reordered and integer constants collapsed.
 * <p>
 * Other operators are currently ignored; in the future they could
 * be examined and simplified; consider
 * {@literal <operand> && <operand> && <operand> &&...}
 * could trim where an operand is a constant zero, same for multiplication;
 * a constant one could be removed.
 * <p>
 * Can only be used after pass1 has completed.
 * <p>
 * This is a hack, better to rewrite a tree; then could be more general.
 * <p>
 * ONLY HANDLES +/- for now. 
 */
public class OperatorOptim extends Visitor<OperatorState>
{
private static final boolean debugFlag = Castro.getVerbose() >= BINOP_OPTIM_VERBOSE;
private static OperatorOptim INSTANCE;
private static AstroParseResult apr;
private static final int PLUS_MINUS_OPTIM_LEVEL = 3;
private static final int LOGICAL_OPTIM_LEVEL = 4;

/** sub trees with all elements of equal precedence */
private static TreeProps<OperatorState>operatorState = new TreeProps<>();

private static OperatorOptim getThis()
{
    if(INSTANCE == null) {
        INSTANCE = new OperatorOptim();
        apr = lookup(AstroParseResult.class);
    }
    return INSTANCE;
}

public static void check(ExprContext ctx)
{
    if(Castro.getOptimize() < PLUS_MINUS_OPTIM_LEVEL)
        return;
    getThis().internalCheck(ctx);
}

public static String optimize(ExprContext ctx)
{
    if(Castro.getOptimize() < PLUS_MINUS_OPTIM_LEVEL)
        return null;
    return getThis().internalOptimize(ctx);
}

/**
 * Examine the subtree and build up {@linkplain Operand} lists.
 * @param ctx 
 */
private void internalCheck(ExprContext ctx)
{
    if(!(ctx instanceof ExprBinOpContext) && !(ctx instanceof ExprUnOpContext))
        return;
    boolean cached = operatorState.getMap().containsKey(ctx);
    debugNL(); debug(() -> "CHECK" + (cached ? " CACHED" : ""), ctx);
    if(cached)
        return;
    OperatorState opState = visitExpr(ctx);
    debug("CHECK RESULT", opState);
    debugMap();
}

private String internalOptimize(ExprContext ctx)
{
    if(!(ctx instanceof ExprBinOpContext) && !(ctx instanceof ExprUnOpContext))
        return null;
    OperatorState opState = operatorState.get(ctx);
    if(debugFlag && opState != null) {
        debugNL(); debug("OPTIMIZE", opState);
        for(Operand operand : opState.list()) {
            String s = apr.getCachedPrefixExprProps().get(operand.ctx);
            Objects.requireNonNull(s);
            debug(String.valueOf(operand.sign), operand.ctx.getText(), s, operand.ctx);
        }
    }
    if(opState == null || fold2Int(ctx) != null)
        return null;

    return switch(opState.prec) {
    case PrecPlusMinus, PrecAny -> optimizePlusMinus(opState);
    case PrecAndAnd -> optimizeAndAnd(opState);
    case PrecOrOr -> optimizeOrOr(opState);
    default -> null;
    };
}

/**
 * Using the data structs, if any, generated by xCheck in an earlier pass,
 * attempt to generate code for the given ctx.
 */
private String optimizePlusMinus(OperatorState opState)
{
    try {
        List<Operand> operands = new ArrayList<>();
        int nConstant = 0;
        long constant = 0;

        // Split into list of exprs and count and accumulate constants
        for(Operand operand : opState.list()) {
            Integer foldedInt = fold2Int(operand.ctx);
            if(foldedInt == null)
                operands.add(operand);
            else {
                nConstant++;
                if(operand.sign == '-')
                    constant -= foldedInt;
                else
                    constant += foldedInt;
                if(Util.isOverflow(constant))
                    return null;
            }
        }

        if(nConstant == 0 && operands.isEmpty())
            return null;

        if(operands.isEmpty())
            throw new IllegalStateException();

        // Want the first expr operand to be Plus (not Minus),
        // Then the constant can be put last. "Add [Add/Sub]* constant"
        //
        // If all the expr operands are Minus,
        // put the constant first and all "Sub ". "[Sub]* constant operands".

        // Guess at "isAllMinus"
        boolean isAllMinus = operands.get(0).sign == '-';
        if(isAllMinus) {
            // Check if it's really "isAllMinus"
            // If any non-minus item is found,
            // then swap it with the initial item which is minus.
            for(int i = 1; i < operands.size(); i++) {
                Operand operand = operands.get(i);
                if(operand.sign != '-') {
                    Collections.swap(operands, 0, i);
                    isAllMinus = false;     // Something non-minus found.
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        if(nConstant == 0) {
            // This code generation is a simplification of
            // the "has an operand" case below.
            // Output the operators in reverse order, then the operands.
            if(isAllMinus) {
                sb.append("Sub ".repeat(operands.size() - 1))
                        .append("Neg ");    // All minus, first operand negative
            } else {
                for(int i = operands.size() - 1; i > 0; i--)
                    sb.append(operands.get(i).sign == '-' ? "Sub " : "Add ");
            }
            for(Operand operand : operands)
                sb.append(apr.getCachedPrefixExprProps().get(operand.ctx));
            return sb.toString();
        }

        if(isAllMinus) {
            // The all "Sub " case is simple.
            // (The mixed case deals with inc/dec and interspersed Sub/Add)
            sb.append("Sub ".repeat(operands.size()))
                        .append(constant).append(' ');
            for(Operand operand : operands)
                sb.append(apr.getCachedPrefixExprProps().get(operand.ctx));
        } else {
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
                sb.append(operands.get(i).sign == '-' ? "Sub " : "Add ");
            
            
            for(Operand operand : operands)
                sb.append(apr.getCachedPrefixExprProps().get(operand.ctx));
            
            if(!useIncDec)
                sb.append(constant).append(' ');
        }

        if(debugFlag)
            debug(sf("optim: %s %d '%s'\n",
                          operands.stream().map((op)->op.ctx.getText()).
                                  collect(Collectors.toList()),
                          constant, sb.toString()));
        return sb.toString();
    } catch(IllegalStateException ex) {
        throw ex; // Logic error
    } catch(Exception ex) {
        return null;
    }
}

/**
 * Optimize "a [ && b]*".
 * <br/> remove non-zero constants
 * <br/> discard terms after zero constant
 * 
 * Note that even if know that value of logical expression is false,
 * must execute earlier terms in case of side effects.
 * 
 * Using the data structs, if any, generated by Check in an earlier pass.
 */
// a = b && c && d;
//          =a If If @b Bool @c Bool @d
//      optimize to
//          =a If @b If @c Bool @d
//      which is more readable: chained (not nested) "if" and fewer "Bool"
//
// The above is better, but in all cases consider in the following the
// Bool is not needed because it's inside an if (used as a logop).
// a = if(b && c) x;
//      =a If If @b Bool @c @x
//
// To do this right, best to know if evaluating an "if" condition;
//          if(a && b && c) x;
//      unoptimized
//          If If If @a Bool @b Bool @c @x
//      with AndAnd optimization
//          If If @a If @b Bool @c @x
//      could be, since inside an "if"
//          If If @a If @b @c @x

private String optimizeAndAnd(OperatorState opState)
{
    if(Castro.getOptimize() < LOGICAL_OPTIM_LEVEL)
        return null;
    if(opState.list.size() < 2)
        throw new IllegalStateException();
    try {
        List<Operand> operands = new ArrayList<>();
        // For constants, non-zero discard single, zero trim remaining.
        boolean forceFalse = false;
        for(Operand operand : opState.list) {
            Integer constant = fold2Int(operand.ctx);
            if(constant == null)
                operands.add(operand);
            else if(constant == 0) {
                forceFalse = true;
                operands.add(null); // ends up as "0"
                break;  // throw the rest of the operands away.
            }
            // else do nothing, the non-zero operand is discarded
        }
        if(operands.isEmpty())
            return forceFalse ? "0 " : "1 ";

        StringBuilder sb = new StringBuilder();
        // Handle the first n - 1 operands.
        for(int i = 0; i < operands.size() - 1; i++) {
            Operand operand = operands.get(i);
            sb.append("If ");
            appendMayNeg(sb, operand);
        }
        if(forceFalse)
            sb.append("0 ");
        else {
            // The last is "Bool", unless it's a bool expr
            Operand operand = operands.get(operands.size()-1);
            if(operand.sign == '+' && Util.isBoolExpr(operand.ctx))
                sb.append(apr.getCachedPrefixExprProps().get(operand.ctx));
            else {
                sb.append("Bool ");
                appendMayNeg(sb, operands.get(operands.size()-1));
            }
        }
        return sb.toString();
    } catch(IllegalStateException ex) {
        throw ex; // Logic error
    } catch(Exception ex) {
        return null;
    }
}

// Basically the same as "optimizeAndAnd", but too many little things...
private String optimizeOrOr(OperatorState opState)
{
    if(Castro.getOptimize() < LOGICAL_OPTIM_LEVEL)
        return null;
    if(opState.list.size() < 2)
        throw new IllegalStateException();
    try {
        List<Operand> operands = new ArrayList<>();
        // For constants, zero discard single, non-zero trim remaining.
        boolean forceTrue = false;
        for(Operand operand : opState.list) {
            Integer constant = fold2Int(operand.ctx);
            if(constant == null)
                operands.add(operand);
            else if(constant != 0) {
                forceTrue = true;
                operands.add(null); // ends up as "1"
                break;  // throw the rest of the operands away.
            }
            // else do nothing, the zero operand is discarded
        }
        if(operands.isEmpty())
            return forceTrue ? "1 " : "0 ";

        StringBuilder sb = new StringBuilder();
        // Handle the first n - 1 operands.
        for(int i = 0; i < operands.size() - 1; i++) {
            Operand operand = operands.get(i);
            sb.append("IfElse ");
            appendMayNeg(sb, operand).append("1 ");
        }
        if(forceTrue)
            sb.append("1 ");
        else {
            // The last is "Bool", unless it's a bool expr
            Operand operand = operands.get(operands.size()-1);
            if(operand.sign == '+' && Util.isBoolExpr(operand.ctx))
                sb.append(apr.getCachedPrefixExprProps().get(operand.ctx));
            else {
                sb.append("Bool ");
                appendMayNeg(sb, operands.get(operands.size()-1));
            }
        }
        return sb.toString();
    } catch(IllegalStateException ex) {
        throw ex; // Logic error
    } catch(Exception ex) {
        return null;
    }
}

private static StringBuilder appendMayNeg(StringBuilder sb, Operand operand)
{
    if(operand.sign == '-')
        sb.append("Neg ");
    sb.append(apr.getCachedPrefixExprProps().get(operand.ctx));
    return sb;
}

/**
 * Save collected information that can be used later for optimization.
 * Only track things that might be optimized.
 * 
 * TODO: ? just track everything, avoids redoing some work
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void recordContextState(ExprContext ctx, OperatorState opState)
{
    //
    // The extra checks may not be needed,
    // but this makes the current situation most clear.
    //
    // Currently, only save things with a +/- operator.
    //
    Token token = null;
    boolean save = switch(ctx) {
    case ExprBinOpContext ctx_ -> {
        token = ctx_.o;
        yield token.getType() == Plus || token.getType() == Minus
            || token.getType() == AndAnd || token.getType() == OrOr;
    }
    case ExprUnOpContext ctx_ -> {
        token = ctx_.o;
        yield token.getType() == Plus || token.getType() == Minus;
    }
    default -> false;
    };
    if(save) {
        if(!operatorState.getMap().containsKey(ctx))
            operatorState.put(ctx, opState);
        else
            debug("DISCARD duplicate");
    } else
        debug("recordContextState: not tracked: "
                +ctx.getClass().getSimpleName()
                +(token == null ? "" : ": ") + getDisplayName(token)
                + ": "+ctx.getText());
}

///////////////////////////////////////////////////////////////////////////////
//

    // TODO: Should there be a "real" PrecAny? //////////////////////////////
    // OpPrecedence PrecAny = None;
    enum OpPrecedence {
    PrecPlusMinus,
    PrecAndAnd,
    PrecOrOr,
    // ...
    PrecAny,
    // "None" expr can be part of any BinOp
    None;

    static OpPrecedence get(ExprContext ctx) {
        OpPrecedence retStatus = switch(ctx) {
        case ExprBinOpContext ctx_ -> {
            yield switch(ctx_.o.getType()) {
            case Plus, Minus -> PrecPlusMinus;
            case AndAnd -> PrecAndAnd;
            case OrOr -> PrecOrOr;
            // ...
            default -> None;
            };
        }
        case ExprUnOpContext ctx_ -> {
            yield ctx_.o.getType() == Plus || ctx_.o.getType() == Minus
                ? PrecAny : None;   // TODO: can be PrecAny in all cases?
        }
        default -> None;
        };
        return retStatus;
    }

    }; /////////////////////// enum OpPrecedence

    //////////////////////////////////////////////////////////////////////

    /**
     * An expr with a sign
     */
    record Operand(ExprContext ctx, char sign)
    {
    Operand(ExprContext ctx)
    {
        this(ctx, '+');
    }
    @Override
    public String toString()
    {
        return String.format("Operand[ctx=%s sign=%c]", ctx.getText(), sign);
    }
    } /////////// record Operand

    //////////////////////////////////////////////////////////////////////

    /**
     * An operator has a list of operands that are the same precedence;
     * some operands may be exprs (and who knows what they might contain).
     * 
     * NOTE: Sometimes single/term (only one item in list).
     */
    // TODO: realOperand could have a sign
    record OperatorState(OpPrecedence prec,
                              List<Operand> list,
                              Operand realOperand)
    {
    OperatorState(OpPrecedence prec,
                              List<Operand> list,
                              Operand realOperand)
    {
        this.prec = prec;
        this.list = ImmutableList.copyOf(list);
        this.realOperand = realOperand;
    }
    List<String> textList()
    {
        if (Boolean.FALSE) list.add(null);
        return list().stream()
                .map((operand) -> operand.ctx.getText())
                .collect(Collectors.toList());
    }
    };  /////////// record OperatorState

private OperatorState newConstOrIgnoreState(ExprContext ctx)
{
    return new OperatorState(None, ImmutableList.of(new Operand(ctx)), new Operand(ctx));
}

    //////////////////////////////////////////////////////////////////////

private char opToSign(int operatorType)
{
    char sign = operatorType == Minus ? '-' : operatorType == Plus ? '+' : 0;
    if(sign == 0)
        throw new IllegalArgumentException();
    return sign;
}

private char negate(char sign)
{
    return sign == '-' ? '+' : '-';
}
private Operand negate(Operand operand)
{
    return new Operand(operand.ctx, negate(operand.sign));
}
private Operand applySign(char sign, Operand operand)
{
    return sign == '+' ? operand : negate(operand);
}
/** May return the same list */
private List<Operand> applySign(char sign, List<Operand> operands)
{
    if(sign == '+')
        return operands;
    Builder<Operand> builder = ImmutableList.builderWithExpectedSize(operands.size());
    for(Operand operand : operands)
        builder.add(negate(operand));
    return builder.build();
}

/**
 * An operator has a list of operands at the same precedence;
 * when an operatorState with the same precedencei, or PrecAny,
 * is added its elements are individually added; otherwise the list is added.
 * If operatorType is Minus, then the added items are negated before adding.
 * 
 * @param targetPrec the associated operator's precedence
 * @param targetList target list, add stuff to this
 * @param opState stuff to add
 * @param operandType may cause opState stuff to be negated
 */
private void addToOperatorList(OpPrecedence targetPrec,
                              List<Operand> targetList,
                              OperatorState opState,
                              char sign)
{
    //if(targetPrec == opState.prec || opState.prec == PrecAny)
    // NOTE: TODO: no difference between PrecAny/None
    if(targetPrec == opState.prec || opState.list.size() == 1) {
        targetList.addAll(applySign(sign, opState.list()));
    } else {
        if(opState.prec == PrecAny && opState.list.size() != 1)
            throw new IllegalStateException();
        targetList.add(applySign(sign, opState.realOperand));
    }
}

/**
 * Plus or Minus unary op. If the opState is PlusMinus, then add
 */
private OperatorState inheritStatePlusMinusUnOp(OperatorState opState,
                                   ExprUnOpContext ctx,
                                   char sign)
{
    if(opState.prec != PrecPlusMinus) {
        // For non +/- opState, inherit as a single operand
        // and make it PrecAny.
        List<Operand> operands = opState.list.size() == 1
                          ? opState.list : ImmutableList.of(opState.realOperand);
        return new OperatorState(PrecAny,
                                 applySign(sign, operands),
                                 new Operand(ctx));
    }
    // Apply +/- to operands.
    return new OperatorState(opState.prec, applySign(sign, opState.list), new Operand(ctx));
}

/**
 * Simply bring up the opState list.
 */
private OperatorState inheritStateTerm(OperatorState opState, ExprContext ctx)
{
    // TODO: if inherited opState is None, make it PrecAny?
    return new OperatorState(opState.prec, opState.list, new Operand(ctx));
}

@Override
@SuppressWarnings("NonPublicExported")
public OperatorState visitExprBinOp(ExprBinOpContext ctx)
{
    OperatorState opState = operatorState.get(ctx);
    debug("VISIT" + (opState != null ? " CACHED" : ""), ctx);
    if(opState != null)
        return opState;
 
    OpPrecedence opPrecedence = OpPrecedence.get(ctx);
    opState = switch(opPrecedence) {
    case PrecPlusMinus, PrecAndAnd, PrecOrOr -> {
        // Pick up the previously recorded left and right operand state.
        OperatorState lState = visitExpr(ctx.l);
        OperatorState rState = visitExpr(ctx.r);
        //System.err.printf("%s\n    l: %s\n    r: %s\n", ctx.getText(), lState, rState);

        List<Operand> list = new ArrayList<>();
        addToOperatorList(opPrecedence, list, lState, '+');
        addToOperatorList(opPrecedence, list, rState, opPrecedence == PrecPlusMinus
                                                      ? opToSign(ctx.o.getType()) : '+');

        yield new OperatorState(opPrecedence, list, new Operand(ctx));
    }
    default -> {
        yield newConstOrIgnoreState(ctx);
    }
    };
    debug(() -> "VISIT BIN_OP " + exprOpText(ctx), opState);
    return opState;
}

@Override
@SuppressWarnings("NonPublicExported")
public OperatorState visitExprUnOp(ExprUnOpContext ctx)
{
    //System.err.printf("VisitExprUnOp: %s: %s\n",
    //                  ctx.getClass().getSimpleName(), ctx.getText());
    if(ctx.o.getType() == Plus || ctx.o.getType() == Minus) {
        OperatorState opState = inheritStatePlusMinusUnOp(visitExpr(ctx.e), ctx,
                                                        opToSign(ctx.o.getType()));
        debug(() -> "VISIT UN_OP " + exprOpText(ctx), opState);
        return opState;
    }
    return newConstOrIgnoreState(ctx);
}

@Override
@SuppressWarnings("NonPublicExported")
public OperatorState visitExprTermOp(ExprTermOpContext ctx)
{
    //System.err.printf("VisitExprTermOp: %s: %s\n",
    //                  ctx.getClass().getSimpleName(), ctx.getText());
    OperatorState opState = switch(ctx.t) {
    case TermSingleContext ctx_ -> { Objects.isNull(ctx_);
        yield new OperatorState(PrecAny,
                ImmutableList.of(new Operand(ctx, opToSign(Plus))), new Operand(ctx));
    }
    case TermParenContext ctx_ -> {
        yield inheritStateTerm(visitExpr(ctx_.p.e), ctx);
    }
    default -> newConstOrIgnoreState(ctx);
    };
    
    debug(() -> "VISIT TERM_OP " + exprOpText(ctx), opState);
    return opState;
}

private OperatorState visitExpr(ExprContext ctx)
{
    //System.err.printf("VisitExpr: %s: %s\n",
    //                  ctx.getClass().getSimpleName(), ctx.getText());
    if(fold2Int(ctx) != null)   // Nothing to do with a constant.
        return newConstOrIgnoreState(ctx);
    OperatorState opState = switch(ctx) {
    case ExprBinOpContext ctx_ -> visitExprBinOp(ctx_);
    case ExprUnOpContext ctx_ -> visitExprUnOp(ctx_);
    case ExprTermOpContext ctx_ -> visitExprTermOp(ctx_);
    default -> newConstOrIgnoreState(ctx);
    };
    recordContextState(ctx, opState);
    return opState;
}

private void debug(Supplier<String> tag, OperatorState opState)
{
    if(!debugFlag)
        return;
    debug(tag.get(), opState);
}
private void debug(String tag, OperatorState opState)
{
    if(!debugFlag)
        return;
    List<String> data = opState.textList();
    debug(tag, opState.prec().toString(), data.toString(), opState);
}

private void debug(Supplier<String> tag, ExprContext ctx)
{
    if(!debugFlag)
        return;
    debug(tag.get(), ctx);
}

private String exprOpText(ExprContext ctx)
{
    return switch(ctx) {
    case ExprBinOpContext ctx_ -> ctx_.o.getText();
    case ExprUnOpContext ctx_ -> ctx_.o.getText();
    default -> "";
    };
}

private void debug(String tag, ExprContext ctx)
{
    if(!debugFlag)
        return;
    debug(tag, exprOpText(ctx), ctx.getText(), ctx);
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
    for(Entry<ParseTree, OperatorState> entry : operatorState.getMap().entrySet()) {
        System.err.printf("%23s %s %s, ctx %s\n", "MAP",
                          objectID(entry.getValue().prec().toString(), entry.getKey()),
                          entry.getValue().textList().toString(),
                          entry.getKey().getText());
    }
}


}
