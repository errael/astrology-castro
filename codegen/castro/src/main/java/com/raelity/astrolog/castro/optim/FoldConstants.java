/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.optim;

import java.util.Objects;

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
import com.raelity.astrolog.castro.tables.Functions.Function;
import com.raelity.astrolog.castro.tables.Functions.FunctionConstValue;

import static com.raelity.astrolog.castro.Compile.isAllocFrozen;
import static com.raelity.astrolog.castro.Constants.isConstantName;
import static com.raelity.astrolog.castro.Constants.numericConstant;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.parseInt;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.antlr.AstroLexer.*;

/**
 * Fold an expression that only contains constants.
 * Note that before name/variable allocation, expressions that involve
 * addresses are considerered not constant. This comes into play
 * when a constant expression is used to specify an address.
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
 * 
 * TODO: assume that pass3 or later, CHECK IT.
 * 
 * TODO: want two styles:
 *          - general folding optim
 *          - must be constant - this produces specific errors
 * probably two different entry points.
 *       
 */
public class FoldConstants implements AstroParserVisitor<Folded>
{
private static TreeProps<Folded> folded = new TreeProps<>();
// registers is not initialized until after alloc is frozen.
private static Registers registers;

private FoldConstants()
{
    if(registers == null && isAllocFrozen()) {
        registers = lookup(Registers.class);
    }
}

public static Integer reportFold2Int(ExprContext ctx)
{
    return fold2Int(ctx, true);
}

public static Integer fold2Int(ExprContext ctx)
{
    return fold2Int(ctx, false);
}

private static Integer fold2Int(ExprContext ctx, boolean report)
{
    Folded f = folded.get(ctx);
    if(f != null)
        return f.val();
    FoldConstants fc = new FoldConstants();
    f = fc.expr2constInt(ctx, report);
    if(f != null) {
        folded.put(ctx, f);
        return f.val();
    }
    return null;
}

private Folded expr2constInt(ExprContext ctx, boolean report)
{
    try {
        return visitExpr(ctx);
        // TODO catch overflow, report error, return null;
    } catch(CancelFolding ex) {
        if(report) {
            if(ex.ctx != null)
                reportError(ex.ctx, "'%s' is not a constant", ex.ctx.getText());
            else
                reportError(ex.token, "'%s' is not a constant", ex.token.getText());
        }
        return null;
    } catch(AbortFolding ex) {
        throw ex;
    }
}

private Folded visitExpr(ExprContext ctx)
{
    return switch(ctx) {
    case ExprUnOpContext ctx1 -> visitExprUnOp(ctx1);
    case ExprBinOpContext ctx1 -> visitExprBinOp(ctx1);
    case ExprTermOpContext ctx1 -> visitExprTermOp(ctx1);
    case ExprFuncContext ctx1 -> visitExprFunc(ctx1);
    case null,default -> throw new CancelFolding(ctx);
    };
}

@Override
public Folded visitExprBinOp(ExprBinOpContext ctx)
{
    return switch(ctx.o.getType()) {
    case Star -> new Folded(visitExpr(ctx.l).l * visitExpr(ctx.r).l);
    case Div -> new Folded(visitExpr(ctx.l).l / visitExpr(ctx.r).l);
    case Mod -> new Folded(visitExpr(ctx.l).l % visitExpr(ctx.r).l);
    case Plus -> new Folded(visitExpr(ctx.l).l + visitExpr(ctx.r).l);
    case Minus -> new Folded(visitExpr(ctx.l).l - visitExpr(ctx.r).l);
    case LeftShift -> new Folded(visitExpr(ctx.l).l << visitExpr(ctx.r).l);
    case RightShift -> new Folded(visitExpr(ctx.l).l >> visitExpr(ctx.r).l);

    case And -> new Folded(visitExpr(ctx.l).l & visitExpr(ctx.r).l);
    case Or -> new Folded(visitExpr(ctx.l).l | visitExpr(ctx.r).l);
    case Caret -> new Folded(visitExpr(ctx.l).l ^ visitExpr(ctx.r).l);
        
    // Seems no reason to handle this
    case Less -> throw new CancelFolding(ctx.o);
    case LessEqual -> throw new CancelFolding(ctx.o);
    case Greater -> throw new CancelFolding(ctx.o);
    case GreaterEqual -> throw new CancelFolding(ctx.o);
    case Equal -> throw new CancelFolding(ctx.o);
    case NotEqual -> throw new CancelFolding(ctx.o);
    
    default -> throw new AbortFolding("Unknown BinOp: " + ctx.o.getText());
    };
}

@Override
public Folded visitExprUnOp(ExprUnOpContext ctx)
{
    return switch(ctx.o.getType()) {
    case Plus -> new Folded(visitExpr(ctx.e).l);
    case Minus -> new Folded(- visitExpr(ctx.e).l);
    case Tilde -> new Folded(~ visitExpr(ctx.e).l);

    case Not -> throw new CancelFolding(ctx.o);

    default -> throw new AbortFolding("unknown UnOp");
    };
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
        throw new CancelFolding(ts.f);
    }
    case TermParenContext tp -> visitExpr(tp.p.e);
    case TermAddressOfContext ta -> visitTermAddressOf(ta);
    case null,default -> throw new CancelFolding(ctx);
    };
}

@Override
public Folded visitInteger(IntegerContext ctx)
{
    return new Folded(parseInt(ctx.i));
}

private Folded visitLval(LvalContext ctx)
{
    return switch(ctx) {
    case LvalMemContext ctx0 -> {
        if(isConstantName(ctx0.lvid)) {
            Integer constant = numericConstant(ctx0.lvid);
            if(constant != null)
                yield new Folded(constant);
        }
        throw new CancelFolding(ctx0.lvid);
    }
    case null,default -> throw new CancelFolding(ctx);
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
        new Folded(registers.getVar(ctx0.lvid.getText()).getAddr());
    case LvalArrayContext ctx0 ->
        new Folded(registers.getVar(ctx0.lvid.getText()).getAddr()
                + visitExpr(ctx0.idx).l);
    case LvalIndirectContext ctx0 -> {
        if(Boolean.FALSE) Objects.nonNull(ctx0);
        throw new AbortFolding("Internal Error");
    }
    case null, default -> throw new IllegalArgumentException();
    };
}

@Override
public Folded visitExprFunc(ExprFuncContext ctx)
{
    Function f = Functions.get(ctx.fc.id.getText());
    FunctionConstValue constAddr = f.constValue(ctx);
    if(constAddr.isConst())
        return new Folded(constAddr.realAddr());
    throw new CancelFolding(ctx);
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
