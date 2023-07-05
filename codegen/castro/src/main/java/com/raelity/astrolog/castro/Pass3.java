/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.util.List;

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
import com.raelity.astrolog.castro.antlr.AstroParser.LvalArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalIndirectContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.antlr.AstroParser.TermAddressOfContext;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Ops;
import com.raelity.astrolog.castro.tables.Ops.Flow;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.report;
import static com.raelity.astrolog.castro.antlr.AstroParser.Assign;
import static com.raelity.astrolog.castro.antlr.AstroParser.Minus;
import static com.raelity.astrolog.castro.antlr.AstroParser.Plus;

/**
 * Generate the code.
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
final TreeProps<String> annotations = new TreeProps<>();
/** for switch/macro enter clear sb_an*, exit save sb_an* */
final StringBuilder sb_annotation = new StringBuilder();

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

private String assignToLval(LvalContext ctx)
{
    return "#LVAL#" + ctx.id.getText();
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
String genIfOp(ExprIfOpContext ctx, String condition, String if_true)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.IF)).append(' ')
            .append(condition).append(' ')
            .append(if_true);
    return sb.toString();
}
        
@Override
String genIfElseOp(ExprIfElseOpContext ctx,
                   String condition, String if_true, String if_false)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.IF_ELSE)).append(' ')
            .append(condition).append(' ')
            .append(if_true).append(' ')
            .append(if_false);
    return sb.toString();
}

@Override
String genRepeatOp(ExprRepeatOpContext ctx, String count, String statement)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.REPEAT)).append(' ')
            .append(count).append(' ')
            .append(statement);
    return sb.toString();
}

@Override
String genWhileOp(ExprWhileOpContext ctx, String condition, String statement)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.WHILE)).append(' ')
            .append(condition).append(' ')
            .append(statement);
    return sb.toString();
    
}

@Override
String genDoWhileOp(ExprDowhileOpContext ctx, String statement, String condition)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.DO_WHILE)).append(' ')
            .append(statement).append(' ')
            .append(condition);
    return sb.toString();
}

@Override
String genForOp(ExprForOpContext ctx,
                String counter, String begin, String end, String statement)
{
    sb.setLength(0);
    sb.append(astroControlOp(Flow.FOR)).append(' ')
            .append(assignToLval(ctx.lval())).append(' ')
            .append(begin).append(' ')
            .append(end).append(' ')
            .append(statement);
    return sb.toString();
}

/** Append the first n items onto the StringBuilder */
private void appendSubBlock(StringBuilder sb, Flow doOp,
                                              int n, List<String> statements)
{
    //if(doOp != null)
    //    sb.append(astroControlOp(doOp)).append(' ');
    sb.append(astroControlOp(doOp)).append(' ');
    for(int i = 0; i < n; i++)
        sb.append(statements.remove(0)).append(' ');
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
        case 1 -> throw new IllegalStateException();
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
    // TODO: CLEAN
    // sb.append("FUNC(").append(args.size()).append(") ");
    String funcName = Functions.translate(_funcName);
    if(Ops.isAnyOp(funcName))
        report(false, ctx.func_call().id, "using castro operator '%s' as a function", ctx.func_call().id.getText());
    sb.append(Functions.translate(funcName)).append(' ');
    for(String arg : args) {
        sb.append(arg).append(' ');
    }
    return sb.toString();
}

@Override
String genUnOp(ExprUnOpContext ctx, Token opToken, String expr)
{
    sb.setLength(0);
    int opType = opToken.getType();
    String text = opType == Minus ? "u-" : opType == Plus ? "u+"
                                   :opToken.getText();
    sb.append(text).append(' ').append(expr);
    return sb.toString();
}

@Override
String genQuestColonOp(ExprQuestOpContext ctx,
                       String condition, String if_true, String if_false)
{
    sb.setLength(0);
    // For "C" semantics, use if-else since Astro's "?:" evaluates both sides
    sb.append(astroControlOp(Flow.IF_ELSE)).append(' ')
            .append(condition).append(' ')
            .append(if_true).append(' ')
            .append(if_false);
    return sb.toString();
}

@Override
String genBinOp(ExprBinOpContext ctx, Token opToken, String lhs, String rhs)
{
    sb.setLength(0);
    sb.append(astroOp(opToken.getType())).append(' ')
            .append(lhs)
            .append(rhs);
    return sb.toString();
}

private StringBuilder lvalAssignment(StringBuilder lsb, LvalContext lval_ctx)
{
    switch(lval_ctx) {
    case LvalMemContext ctx -> {
        String lvalVar = ctx.id.getText();
        if(lvalVar.length() == 1)
            lsb.append('=').append(lvalVar).append(' ');
        else
            lsb.append("= ").append(registers.getVar(lvalVar).getAddr())
                    .append(' ');
    }
    case LvalIndirectContext ctx ->
        lsb.append("= @").append(lvalReadVar(ctx.id.getText())).append(' ');
    case LvalArrayContext ctx -> {
        lsb.append("= Add ");
        String lvalVar = ctx.id.getText();
        if(lvalVar.length() == 1)
            lsb.append('%').append(lvalVar);
        else
            lsb.append(registers.getVar(lvalVar).getAddr());
        lsb.append(' ').append(lvalArrayIndex.get(ctx));
    }
    case null, default -> throw new IllegalStateException();
    }
    return lsb;
}

@Override
String genAssOp(ExprAssOpContext ctx, Token opToken, String lhs, String rhs)
{
    sb.setLength(0);
    if(opToken.getType() == Assign) {
        // Just a simple assign
        // Ignore the lhs, it is an lvalRead.
        // Need an assign target.
        lvalAssignment(sb, ctx.l)
                .append(rhs);
    } else {
        // actual assign op
        // rewrite: Assign lvalAssign <op> lhs rhs
        lvalAssignment(sb, ctx.l)
                .append(astroAssignOp(opToken.getType())).append(' ')
                .append(lhs)
                .append(rhs);
    }
    return sb.toString();
}


private String lvalReadVar(String lval)
{
    // length 1 is 'a' - 'z'
    return lval.length() == 1 ? lval
           : String.valueOf(registers.getVar(lval).getAddr());
}

@Override
String genLval(LvalMemContext ctx)
{
    sb.setLength(0);
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
    sb.append("Var ").append("Add ");
    String lval = ctx.id.getText();
    if(lval.length() == 1)
        sb.append('%').append(lval);
    else
        sb.append(registers.getVar(lval).getAddr());
    sb.append(' ').append(expr);
    return sb.toString();
}

@Override
String genAddr(TermAddressOfContext ctx)
{
    sb.setLength(0);
    sb.append("ADDR").append(' ').append(ctx.Identifier().getText());
    return sb.toString();
}

}
