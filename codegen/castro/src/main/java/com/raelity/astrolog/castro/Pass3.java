/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.util.List;
import java.util.Objects;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.astrolog.castro.antlr.AstroParser.ExprAssOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprBinOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprBraceBlockOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprDowhileOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprForOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprIfElseOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprIfOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprQuestOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprRepeatOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprUnOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprWhileOpContext;
import com.raelity.astrolog.castro.antlr.AstroParser.FloatContext;
import com.raelity.astrolog.castro.antlr.AstroParser.IntegerContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalIndirectContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermAddressOfContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Ops;
import com.raelity.astrolog.castro.tables.Ops.Flow;

import static com.raelity.astrolog.castro.Error.*;
import static com.raelity.astrolog.castro.Util.isBuiltinVar;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.antlr.AstroParser.Assign;
import static com.raelity.astrolog.castro.antlr.AstroParser.Minus;
import static com.raelity.astrolog.castro.antlr.AstroParser.Plus;
import static com.raelity.astrolog.castro.Util.lval2MacoSwitchSpace;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.antlr.AstroLexer.Not;
import static com.raelity.astrolog.castro.antlr.AstroLexer.PlusAssign;
import static com.raelity.astrolog.castro.antlr.AstroLexer.Tilde;
import static com.raelity.astrolog.castro.antlr.AstroParser.MinusAssign;
import static com.raelity.astrolog.castro.tables.Ops.astroCode;
import static com.raelity.astrolog.castro.Util.expr2constInt;
import static com.raelity.astrolog.castro.Util.parseInt;
import static com.raelity.astrolog.castro.antlr.AstroParser.IntegerConstant;

/**
 * Generate the AstroExpression code.
 */
public class Pass3 extends GenPrefixExpr
{

static Pass3 pass3()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    Pass3 pass3 = new Pass3(apr);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(pass3, apr.getProgram());
    return pass3;
}

private final Registers registers;
// final TreeProps<String> annotations = new TreeProps<>();
// /** for switch/macro enter clear sb_an*, exit save sb_an* */
// final StringBuilder sb_annotation = new StringBuilder();

private Pass3(AstroParseResult apr)
{
    super(apr);
    this.registers = lookup(Registers.class);
}

StringBuilder sb = new StringBuilder();

private String astroControlOp(Flow op)
{
    return Ops.astroCode(op.key());
}

private String astroOp(int token)
{
    return Ops.astroCode(token);
}

private String astroAssignOp(int token)
{
    // remove the trailing '='
    String assOp = Ops.astroCode(token);
    return assOp.substring(0, assOp.length() - 1);
}

@Override
String genSw_cmdExpr_arg(Switch_cmdContext ctx, List<String> bs)
{
    sb.setLength(0);
    for(String s : bs) {
        sb.append(s).append(' ');
    }
    return sb.toString();
}

@Override
String genSw_cmdName(Switch_cmdContext ctx, String name, List<String> bs)
{
    // just return the expr, ignore the name
    sb.setLength(0);
    for(String s : bs) {
        sb.append(s).append(' ');
    }
    return sb.toString();
}

@Override
String genSw_cmdStringAssign(Switch_cmdContext sc_ctx, String name)
{
    // name is the lval string, ignore it. Instead convert
    // the sc_ctx lval into the index and plug it back into apr.prefixExpr.

    sb.setLength(0);
    lvalVarAddr(sb, sc_ctx.l);
    apr.prefixExpr.put(sc_ctx.l, sb.toString());

    return "#AssignString#";
}

@Override
String genIfOp(ExprIfOpContext ctx, String condition, String if_true)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.IF)).append(' ')
            .append(condition).append(if_true);
    return sb.toString();
}
        
@Override
String genIfElseOp(ExprIfElseOpContext ctx,
                   String condition, String if_true, String if_false)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.IF_ELSE)).append(' ')
            .append(condition).append(if_true).append(if_false);
    return sb.toString();
}

@Override
String genRepeatOp(ExprRepeatOpContext ctx, String count, String statement)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.REPEAT)).append(' ')
            .append(count).append(statement);
    return sb.toString();
}

@Override
String genWhileOp(ExprWhileOpContext ctx, String condition, String statement)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.WHILE)).append(' ')
            .append(condition).append(statement);
    return sb.toString();
    
}

@Override
String genDoWhileOp(ExprDowhileOpContext ctx, String statement, String condition)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.DO_WHILE)).append(' ')
            .append(condition).append(statement);
    return sb.toString();
}


@Override
String genForOp(ExprForOpContext ctx,
                String counter, String begin, String end, String statement)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.FOR)).append(' ');
            lvalWriteVar(sb, ctx.l, false)
            .append(begin)
            .append(end)
            .append(statement);
    return sb.toString();
}

/** Append the first n items onto the StringBuilder */
private void appendSubBlock(StringBuilder sb, Flow doOp,
                                              int n, List<String> statements)
{
    sb.append(astroControlOp(doOp)).append(' ');
    for(int i = 0; i < n; i++)
        sb.append(statements.remove(0));
}

@Override
String genBraceBlockOp(ExprBraceBlockOpContext ctx, List<String> statements)
{
    sb.setLength(0);
    if(statements.size() == 1)
        sb.append(statements.remove(0)).append(' ');
    // The interesting case is when there are 5 or more statements;
    // that requires chaining Do* instructions;
    // use Do3, add 3 statements, the 4th statement is another Do*.
    while(!statements.isEmpty()) {
        switch(statements.size()) {
        case 1 -> throw new IllegalStateException(ctx.getText());
        case 2 -> appendSubBlock(sb, Flow.XDO, 2, statements);
        case 3 -> appendSubBlock(sb, Flow.XDO2, 3, statements);
        case 4 -> appendSubBlock(sb, Flow.XDO3, 4, statements);
        default -> appendSubBlock(sb, Flow.XDO3, 3, statements);
        }
    }
    return sb.toString();
}

@Override
String genFuncCallOp(ExprFuncContext ctx, String _funcName, List<String> args)
{
    sb.setLength(0);
    // TODO: send funcname(narg) to annotations stream
    String funcName = Functions.translate(_funcName);
    if(Ops.isAnyOp(funcName))
        reportError(FUNC_CASTRO, ctx.func_call().id,
                    "using castro operator '%s' as a function",
                    ctx.func_call().id.getText());
    sb.append(Functions.translate(funcName)).append(' ');
    for(String arg : args) {
        sb.append(arg);
    }
    return sb.toString();
}

@Override
String genUnOp(ExprUnOpContext ctx, Token opToken, String expr)
{
    sb.setLength(0);
    int opType = opToken.getType();
    switch(opType) {
    case Minus -> sb.append("Neg ");
    case Tilde, Not -> sb.append(astroCode(opType)).append(' ');
    case Plus -> { break; }
    default -> sb.append("#getUnOp_InternalError");
    }
    sb.append(expr);
    return sb.toString();
}

@Override
String genQuestColonOp(ExprQuestOpContext ctx,
                       String condition, String if_true, String if_false)
{
    sb.setLength(0);
    // For "C" semantics, use if-else since Astro's "?:" evaluates both sides
    sb.append(astroControlOp(Flow.IF_ELSE)).append(' ')
            .append(condition).append(if_true).append(if_false);
    return sb.toString();
}

@Override
String genBinOp(ExprBinOpContext ctx, Token opToken, String lhs, String rhs)
{
    sb.setLength(0);
    sb.append(astroOp(opToken.getType())).append(' ')
            .append(lhs).append(rhs);
    return sb.toString();
}


/** lval must be a fixed location, no expr involved. */
private StringBuilder lvalVarAddr(StringBuilder lsb, LvalContext lval_ctx)
{
    boolean resolved = false;
    String lvalName = lval_ctx.id.getText();
    switch(lval_ctx) {
    case LvalMemContext ctx -> {
        if(Boolean.FALSE) Objects.nonNull(ctx);
        if(isBuiltinVar(lvalName))
            lsb.append('%').append(lvalName).append(' ');
        else
            lsb.append(registers.getVar(lvalName).getAddr()).append(' ');
        resolved = true;
    }
    case LvalIndirectContext ctx -> { if(Boolean.FALSE) Objects.nonNull(ctx); }
    case LvalArrayContext ctx -> {
        Integer constVal = expr2constInt(ctx.idx);
        if(constVal != null) {
            lsb.append(registers.getVar(lvalName).getAddr() + constVal).append(' ');
            resolved = true;
        }
    }
    case null, default -> throw new IllegalStateException(lval_ctx.getText());
    }
    if(!resolved)
        Util.reportError(lval_ctx, "'%s' must be a fixed location", lval_ctx.getText());
    return lsb;
}

private StringBuilder lvalWriteVar(StringBuilder lsb, LvalContext lval_ctx,
                                    boolean varForAssignment)
{
    String lvalName = lval_ctx.id.getText();
    switch(lval_ctx) {
    case LvalMemContext ctx -> {
        if(Boolean.FALSE) Objects.nonNull(ctx);
        if(isBuiltinVar(lvalName)) {
            if(!varForAssignment)
                lsb.append('%');
            lsb.append(lvalName).append(' ');
        }else
            lsb.append(registers.getVar(lvalName).getAddr()).append(' ');
    }
    case LvalIndirectContext ctx -> {
        if(Boolean.FALSE) Objects.nonNull(ctx);
        lsb.append("@").append(lvalReadVar(lvalName)).append(' ');
    }
    case LvalArrayContext ctx -> {
        Integer constVal = expr2constInt(ctx.idx);
        // Don't need to do a runtime add if idx is constant
        if(constVal != null)
            lsb.append(registers.getVar(lvalName).getAddr() + constVal).append(' ');
        else {
            lsb.append("Add ");
            if(isBuiltinVar(lvalName))
                lsb.append('%').append(lvalName);
            else
                lsb.append(registers.getVar(lvalName).getAddr());
            lsb.append(' ').append(lvalArrayIndex.get(ctx));
        }
    }
    case null, default -> throw new IllegalStateException(lval_ctx.getText());
    }
    return lsb;
}

private StringBuilder lvalAssignment(StringBuilder lsb, LvalContext lval_ctx)
{
    lsb.append('=');
    // In the case of "a = 3" ==> "=a 3".
    // All the other cases have a space after the "=".
    if(!(lval_ctx instanceof LvalMemContext) || !isBuiltinVar(lval_ctx.id))
        lsb.append(' ');
    lvalWriteVar(lsb, lval_ctx, true);
    return lsb;
}

@Override
String genAssOp(ExprAssOpContext ctx, Token opToken, String lhs, String rhs)
{
    sb.setLength(0);
    int opType = opToken.getType();
    if(opType == Assign) {
        // Just a simple assign
        // Ignore the lhs, it was generated as an lvalRead;
        // need an assign target.
        lvalAssignment(sb, ctx.l)
                .append(rhs);
    } else {
        boolean canOptim = false;
        if(opType == PlusAssign || opType == MinusAssign) {
            Integer constVal = expr2constInt(ctx.e);
            if(constVal != null && 1 == constVal)
                canOptim = true;
        }
        // actual assign op
        // rewrite: Assign lvalAssign <op> lhs rhs
        lvalAssignment(sb, ctx.l);
        if(!canOptim)
            sb.append(astroAssignOp(opType)).append(' ')
                    .append(lhs).append(rhs);
        else {
            // "a += 1" to "=a Inc @a"; instead of "=a Add @a 1"
            String op = opType == PlusAssign ? "Inc " : "Dec ";
            sb.append(op).append(lhs);
        }
    }
    return sb.toString();
}

private String lvalReadVar(String lvalName)
{
    // length 1 is 'a' - 'z'
    return isBuiltinVar(lvalName) ? lvalName
           : String.valueOf(registers.getVar(lvalName).getAddr());
}

@Override
String genLval(LvalMemContext ctx)
{
    sb.setLength(0);
    
    AstroMem space = lval2MacoSwitchSpace(ctx);
    if(space != null)
        sb.append(String.valueOf(space.getVar(ctx.id.getText()).getAddr())).append(' ');
    else
        sb.append('@').append(lvalReadVar(ctx.id.getText())).append(' ');
    return sb.toString();
}

@Override
String genLval(LvalIndirectContext ctx)
{
    sb.setLength(0);
    sb.append("Var ").append('@').append(lvalReadVar(ctx.id.getText())).append(' ');
    return sb.toString();
}

@Override
String genLval(LvalArrayContext ctx, String expr)
{
    sb.setLength(0);
    String lvalName = ctx.id.getText();
    Integer constVal = expr2constInt(ctx.idx);
    // Don't need to do a runtime add if idx is constant
    if(constVal != null)
        sb.append('@').append(registers.getVar(lvalName).getAddr() + constVal).append(' ');
    else {
        sb.append("Var Add ");
        if(isBuiltinVar(lvalName))
            sb.append('%').append(lvalName);
        else
            sb.append(registers.getVar(lvalName).getAddr());
        sb.append(' ').append(expr);
    }
    return sb.toString();
}

/** return a decimal string */
@Override
String genInteger(IntegerContext ctx)
{
    sb.setLength(0);
    String s = ctx.i.getText();
    if(ctx.i.getType() == IntegerConstant) {
        sb.append(s);
    } else {
        int i = parseInt(ctx.i);
        sb.append(i);
    }
    sb.append(' ');
    return sb.toString();
}

@Override
String genFloat(FloatContext ctx)
{
    sb.setLength(0);
    sb.append(ctx.f.getText()).append(' ');
    return sb.toString();
}

@Override
String genAddr(TermAddressOfContext ctx)
{
    sb.setLength(0);
    sb.append(registers.getVar(ctx.id.getText()).getAddr()).append(' ');
    return sb.toString();
}

}
