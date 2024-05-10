/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.visitors;

import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.antlr.AstroParser.*;
import com.raelity.astrolog.castro.antlr.AstroParserVisitor;

/**
 * This class throws for everything in the interface. Makes it easier
 * to extend and only implement what you want
 */
public class Visitor<T> implements AstroParserVisitor<T>
{
    @Override
    public T visitProgram(ProgramContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitLayout(LayoutContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitLayout_region(Layout_regionContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitBaseContstraint(BaseContstraintContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitLimitContstraint(LimitContstraintContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitReserveContstraint(ReserveContstraintContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitRsv_loc(Rsv_locContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitConst(ConstContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitVar(VarContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitVarDef(VarDefContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitStr_expr(Str_exprContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitCopy(CopyContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitAssign_macro_addr(Assign_macro_addrContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitAssign_switch_addr(Assign_switch_addrContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitMacro(MacroContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitSwitch(SwitchContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitRun(RunContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitSwitch_cmd(Switch_cmdContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitSw_name(Sw_nameContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitAstroExprStatement(AstroExprStatementContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitOpt_semi(Opt_semiContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitAstroExpr(AstroExprContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExpr_semi(Expr_semiContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitBrace_block(Brace_blockContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitParen_expr(Paren_exprContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitFunc_call(Func_callContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprRepeatOp(ExprRepeatOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprForOp(ExprForOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprQuestOp(ExprQuestOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprBraceBlockOp(ExprBraceBlockOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprFunc(ExprFuncContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprIfElseOp(ExprIfElseOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprTermOp(ExprTermOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprBinOp(ExprBinOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprWhileOp(ExprWhileOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprDowhileOp(ExprDowhileOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprAssOp(ExprAssOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprIfOp(ExprIfOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitExprUnOp(ExprUnOpContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitTermSingle(TermSingleContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitTermParen(TermParenContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitTermAddressOf(TermAddressOfContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitLvalArray(LvalArrayContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitLvalIndirect(LvalIndirectContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitLvalMem(LvalMemContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitInteger(IntegerContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitFloat(FloatContext ctx)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visit(ParseTree pt)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitChildren(RuleNode rn)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitTerminal(TerminalNode tn)
    {
        throw new AbortVisiting();
    }

    @Override
    public T visitErrorNode(ErrorNode en)
    {
        throw new AbortVisiting();
    }
}
