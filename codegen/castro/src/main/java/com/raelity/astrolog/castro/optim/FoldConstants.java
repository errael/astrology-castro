/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.optim;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.TreeProps;
import com.raelity.astrolog.castro.antlr.AstroParser.Assign_macro_addrContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Assign_switch_addrContext;
import com.raelity.astrolog.castro.antlr.AstroParser.AstroExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.AstroExprStatementContext;
import com.raelity.astrolog.castro.antlr.AstroParser.BaseContstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Brace_blockContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ConstContext;
import com.raelity.astrolog.castro.antlr.AstroParser.CopyContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprAssOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprBinOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprBraceBlockOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprDowhileOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprForOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprIfElseOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprIfOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprQuestOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprRepeatOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprTermOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprUnOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprWhileOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Expr_semiContext;
import com.raelity.astrolog.castro.antlr.AstroParser.FloatContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.IntegerContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LayoutContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Layout_regionContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LimitContstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalIndirectContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Opt_semiContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Paren_exprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ReserveContstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Rsv_locContext;
import com.raelity.astrolog.castro.antlr.AstroParser.RunContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Str_exprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Sw_nameContext;
import com.raelity.astrolog.castro.antlr.AstroParser.SwitchContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermAddressOfContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermParenContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermSingleContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarDefContext;
import com.raelity.astrolog.castro.antlr.AstroParserVisitor;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Function;
import com.raelity.astrolog.castro.tables.Functions.FunctionConstValue;

import static com.raelity.astrolog.castro.Compile.isAllocFrozen;
import static com.raelity.astrolog.castro.Constants.isConstantName;
import static com.raelity.astrolog.castro.Constants.numericConstant;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.parseInt;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.antlr.AstroLexer.*;

/**
 * Fold an expression that only contains integer constants.
 * Calculate with long to easily detect overflow.
 * Note that before name/variable allocation, expressions that involve
 * addresses are considered not constant. This comes into play
 * when a constant expression is used to specify an address.
 * 
 * The polymorphic fold2Int and reportFold2Int are the primary entry points;
 * they all take an ExprContext.
 * The forms that take a default always return a value; the other forms
 * return null if expr is not constant. The report variants generate
 * an error if the expr is not constant.
 * 
 * Results are cached in a TreeProps.
 * Call this from GenPrefixExpr for every expression. If that's OK,
 * could incorporate into there, but that would be a performance optim,
 * so wait until there's a complaint.
 * 
 * WIERDNESS: "Int(1 + 3 + 5)" emits "Int Add 4 5". The "1 + 3" gets folded
 *            into 4. Need to do optimExpr(ctx, s, tag)
 * 
 * Assuming left-right, if the constants are at the end of an expression
 * this can be used, a + 3 - 4 + 6 is a + 5.
 * NOT SURE HOW TO DO THIS
 *      (((a + 3) - 4) + 6)
 * need to look at parent's sibling: 3's parent + has sibling 4.
 * BUT 3 - 4 + 6 + a
 *      (((3 - 4) + 6) + a)
 * handled more easily as 5 + a
 * IN ANY EVENT, insist on all constants, no vars
 */
public class FoldConstants implements AstroParserVisitor<Folded>
{
private static TreeProps<Folded> folded = new TreeProps<>();
private static int hit;
private static int miss;
// registers is not initialized until after alloc is frozen.
private static Registers registers;

private static FoldConstants INSTANCE;
private static FoldConstants get()
{
    if(INSTANCE == null)
        INSTANCE = new FoldConstants();
    if(registers == null && isAllocFrozen()) {
        registers = lookup(Registers.class);
    }
    return INSTANCE;
}

private FoldConstants() { }

public static void outputStats(PrintWriter pw) {
    int n = folded.getMap().entrySet().stream()
            .filter(e -> e.getValue().isConstant())
            .collect(Collectors.counting()).intValue();
    pw.printf("Constant Folding\n");
    pw.printf("    Expr %d, constantExpr %d\n", folded.size(), n);
    pw.printf("    hit %d, miss %d\n", hit, miss);

}

/** 
 * Convert the expression to a constant int.
 * Only simply operators are handled.
 * 
 * @return constant int value of expression, or null if not constant.
 */
public static Integer fold2Int(ExprContext ctx)
{
    return fold2Int(ctx, false);
}

// record ExprString(ExprContext e, String s){}
/** 
 * Try to constant fold expr, return default if expr is not constant.
 * @return String of expr value, or default
 */
public static String fold2Int(ExprContext e, String dflt)
{
    Integer f = fold2Int(e);
    return f != null ? f.toString() + ' ' : dflt;
}

/**
 * Convert the expression to a constant int; report error on token
 * that is not an int.
 * 
 * @return constant int value of expression, or null if not constant.
 */
public static Integer reportFold2Int(ExprContext ctx)
{
    return fold2Int(ctx, true);
}

/**
 * Convert the expression to a constant int; report error on token
 * that is not an int. Note: does not return an object.
 */
public static int reportFold2Int(ExprContext ctx, int dflt)
{
    Integer i = fold2Int(ctx, true);
    return i == null ? dflt : i;
}

private static Folded checkCache(ExprContext ctx)
{
    Folded f = folded.get(ctx);
    //System.err.printf("fold2Int: %s\n", ctx.getText());
    if(f != null)
        hit++;
    else
        miss++;
    return f;
}

/** Only save "not constant" if after alloc frozen */
private static void addCache(ExprContext ctx, Folded f)
{
    Folded cur;
    if(f.isConstant() || isAllocFrozen())
        if((cur = folded.getMap().putIfAbsent(ctx, f)) != null
                && cur.lval() != f.lval())
            throw new IllegalStateException();
}

/**
 * return null if expr can not be folded.
 */
private static Integer fold2Int(ExprContext ctx, boolean report)
{
    Folded f = get().expr2constInt(ctx, report);
    return f.isConstant() ? f.val() : null;
}

private Folded expr2constInt(ExprContext ctx, boolean report)
{
    try {
        Folded f = visitExpr(ctx);
        if(f.isVariable() && report) {
            if(f.ctx() != null)
                reportError(f.ctx(), "'%s' is not a constant", f.ctx().getText());
            else
                reportError(f.token(), "'%s' is not a constant", f.token().getText());
        }
        return f;
    } catch(AbortFolding ex) {
        throw ex;
    }
}

private Folded visitExpr(ExprContext ctx)
{
    Folded f = checkCache(ctx);
    if(f != null)
        return f;
    f = switch(ctx) {
    case ExprUnOpContext ctx1 -> visitExprUnOp(ctx1);
    case ExprBinOpContext ctx1 -> visitExprBinOp(ctx1);
    case ExprTermOpContext ctx1 -> visitExprTermOp(ctx1);
    case ExprFuncContext ctx1 -> visitExprFunc(ctx1);
    case null,default -> Folded.get(ctx);
    };
    if(f.isOverflow())
        reportError(ctx, "integer overflow");
    addCache(ctx, f);
    return f;
}

@Override
public Folded visitExprBinOp(ExprBinOpContext ctx)
{
    Folded l = visitExpr(ctx.l);
    Folded r = visitExpr(ctx.r);
    if(!l.isConstant())
        return l;
    if(!r.isConstant())
        return r;
    return switch(ctx.o.getType()) {
    case Star ->        Folded.get(l.lval() * r.lval());
    case Div ->         Folded.get(l.lval() / r.lval());
    case Mod ->         Folded.get(l.lval() % r.lval());
    case Plus ->        Folded.get(l.lval() + r.lval());
    case Minus ->       Folded.get(l.lval() - r.lval());
    case LeftShift ->   Folded.get(l.lval() << r.lval());
    case RightShift ->  Folded.get(l.lval() >> r.lval());
        
    case And ->         Folded.get(l.lval() & r.lval());
    case Or ->          Folded.get(l.lval() | r.lval());
    case Caret ->       Folded.get(l.lval() ^ r.lval());
        
        // Don't handle logical TODO:
    case Less ->        Folded.get(ctx.o);
    case LessEqual ->   Folded.get(ctx.o);
    case Greater ->     Folded.get(ctx.o);
    case GreaterEqual -> Folded.get(ctx.o);

        // TODO:
    case Equal ->       Folded.get(ctx.o);
    case NotEqual ->    Folded.get(ctx.o);
        //
    case AndAnd ->      Folded.get(l.boolval() && r.boolval());
    case OrOr ->        Folded.get(l.boolval() || r.boolval());
        
    default -> throw new AbortFolding("Unknown BinOp: " + ctx.o.getText());
    };
}

@Override
public Folded visitExprUnOp(ExprUnOpContext ctx)
{
    Folded e = visitExpr(ctx.e);
    if(!e.isConstant())
        return e;
    return switch(ctx.o.getType()) {
    case Plus ->  Folded.get(e.lval());
    case Minus -> Folded.get(- e.lval());
    case Tilde -> Folded.get(~ e.lval());

    // Don't handle logical
    case Not -> Folded.get(ctx.o);

    default -> throw new AbortFolding("unknown UnOp");
    };
}

@Override
public Folded visitExprFunc(ExprFuncContext ctx)
{
    Function f = Functions.get(ctx.fc.id.getText());
    FunctionConstValue val = f.constValue(ctx);
    return val.isConst() ? Folded.get(val.realVal()) : Folded.get(ctx.fc.id);
}

@Override
public Folded visitExprTermOp(ExprTermOpContext ctx)
{
    return switch(ctx.t) {
    case TermSingleContext ts -> {
        if(ts.i != null)
            yield visitInteger(ts.i);
        if(ts.l != null)
            yield visitLval(ts.l);
        // skip float
        yield Folded.get(ctx.t);
    }
    case TermParenContext tp -> visitExpr(tp.p.e);
    case TermAddressOfContext ta -> visitTermAddressOf(ta);

    case null,default -> Folded.get(ctx);
    };
}

@Override
public Folded visitInteger(IntegerContext ctx)
{
    return Folded.get(parseInt(ctx.i));
}

private Folded visitLval(LvalContext ctx)
{
    return switch(ctx) {
    case LvalMemContext ctx0 -> {
        if(isConstantName(ctx0.lvid)) {
            Integer constant = numericConstant(ctx0.lvid);
            if(constant != null)
                yield Folded.get(constant);
        }
        yield Folded.get(ctx0.lvid);
    }
    case null,default -> Folded.get(ctx);
    };
}

@Override
public Folded visitTermAddressOf(TermAddressOfContext ctx)
{
    if(!isAllocFrozen())
        throw new AbortFolding(ctx.lv.id.getText());

    if(isConstantName(ctx.lv.id))
        throw new AbortFolding(ctx.lv.id.getText());
    return switch(ctx.lv) {
    case LvalMemContext ctx0 ->
        Folded.get(registers.getVar(ctx0.lvid.getText()).getAddr());
    case LvalArrayContext ctx0 -> {
        // TODO: 
        Folded idx = visitExpr(ctx0.idx);
        if(!idx.isConstant())
            yield idx;
        yield Folded.get(registers.getVar(ctx0.lvid.getText()).getAddr()
                + idx.lval());
    }
    case LvalIndirectContext ctx0 -> {
        if(Boolean.FALSE) Objects.nonNull(ctx0);
        throw new AbortFolding("Internal Error");
    }
    case null, default -> throw new IllegalArgumentException();
    };
}

@Override
public Folded visitProgram(ProgramContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitLvalMem(LvalMemContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitLvalArray(LvalArrayContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitLvalIndirect(LvalIndirectContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitAssign_macro_addr(Assign_macro_addrContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitAssign_switch_addr(Assign_switch_addrContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitLayout(LayoutContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitLayout_region(Layout_regionContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitBaseContstraint(BaseContstraintContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitLimitContstraint(LimitContstraintContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitReserveContstraint(ReserveContstraintContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitRsv_loc(Rsv_locContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitVar(VarContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitVarDef(VarDefContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitStr_expr(Str_exprContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitCopy(CopyContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitMacro(MacroContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitSwitch(SwitchContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitRun(RunContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitSwitch_cmd(Switch_cmdContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitSw_name(Sw_nameContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitAstroExprStatement(AstroExprStatementContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitOpt_semi(Opt_semiContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitAstroExpr(AstroExprContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExpr_semi(Expr_semiContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitBrace_block(Brace_blockContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitParen_expr(Paren_exprContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitFunc_call(Func_callContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprRepeatOp(ExprRepeatOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprForOp(ExprForOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprQuestOp(ExprQuestOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprIfElseOp(ExprIfElseOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprWhileOp(ExprWhileOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprDowhileOp(ExprDowhileOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprAssOp(ExprAssOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitExprIfOp(ExprIfOpContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitTermSingle(TermSingleContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitTermParen(TermParenContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitFloat(FloatContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visit(ParseTree pt)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitChildren(RuleNode rn)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitTerminal(TerminalNode tn)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitErrorNode(ErrorNode en)
{
    throw new AbortFolding("No trespassing.");
}

@Override
public Folded visitConst(ConstContext ctx)
{
    throw new AbortFolding("No trespassing.");
}

}
