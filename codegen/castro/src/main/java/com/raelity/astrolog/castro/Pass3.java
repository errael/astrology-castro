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
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Ops;
import com.raelity.astrolog.castro.tables.Ops.Flow;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.antlr.AstroParser.Minus;
import static com.raelity.astrolog.castro.antlr.AstroParser.Plus;

/**
 * Generate the code.
 */
public class Pass3 extends GenPrefixExpr
{

static void pass3()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    Pass3 pass3 = new Pass3(apr);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(pass3, apr.getProgram());
}

private Pass3(AstroParseResult apr)
{
    super(apr);
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
String genFuncCallOp(ExprFuncContext ctx, String funcName, List<String> args)
{
    sb.setLength(0);
    // TODO: CLEAN
    sb.append("FUNC(").append(args.size()).append(") ");
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
            .append(lhs).append(' ')
            .append(rhs);
    return sb.toString();
}

@Override
String genAssOp(ExprAssOpContext ctx, Token opToken, String lhs, String rhs)
{
    sb.setLength(0);
    sb.append(opToken.getText()).append(' ')
            .append(lhs).append(' ')
            .append(rhs);
    return sb.toString();
}

@Override
String genLval(LvalContext ctx01, String... expr)
{
    sb.setLength(0);
    return switch(ctx01) {
    case LvalMemContext ctx -> ctx.Identifier().getText();
    case LvalArrayContext ctx -> {
        sb.append("INDEX").append(' ').append(ctx.Identifier()).append(' ')
                .append(expr[0]);
        yield sb.toString();
    }
    case LvalIndirectContext ctx -> {
        sb.append("INDIR").append(' ').append(ctx.Identifier().getText());
        yield sb.toString();
    }
    default -> throw new IllegalArgumentException("unknown class");
    };
}

@Override
String genAddr(TermAddressOfContext ctx)
{
    sb.setLength(0);
    sb.append("ADDR").append(' ').append(ctx.Identifier().getText());
    return sb.toString();
}

}
