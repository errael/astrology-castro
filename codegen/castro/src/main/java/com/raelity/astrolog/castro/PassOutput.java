/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.Castro.CastroOutputOptions;
import com.raelity.astrolog.castro.antlr.AstroParser.AstroExprStatementContext;
import com.raelity.astrolog.castro.antlr.AstroParser.CopyContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.RunContext;
import com.raelity.astrolog.castro.antlr.AstroParser.SwitchContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.antlr.ParseTreeUtil.getOriginalText;
import static com.raelity.astrolog.castro.OutputOptions.*;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.reportError;

/**
 * Output the collect Astrolog code: .as file.
 */
public class PassOutput extends AstroParserBaseListener
{

static PassOutput passOutput()
{
    AstroParseResult apr = lookup(AstroParseResult.class);
    PassOutput passOutput = new PassOutput(apr);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(passOutput, apr.getProgram());
    return passOutput;
}

private final AstroParseResult apr;
//private final Registers registers;
private final Macros macros;
private final Switches switches;
private final PrintWriter out;
//private final PrintWriter err;
private final StringBuilder sb = new StringBuilder(100);
private final EnumSet<OutputOptions> smOutputOpts;
private final EnumSet<OutputOptions> runOutputOpts;

public PassOutput(AstroParseResult apr)
{
    this.apr = apr;
    //this.registers = lookup(Registers.class);
    this.macros = lookup(Macros.class);
    this.switches = lookup(Switches.class);
    this.out = lookup(CastroOut.class).pw();
    //this.err = lookup(CastroErr.class).pw();

    EnumSet<OutputOptions> outputOpts = lookup(CastroOutputOptions.class).outputOpts();

    // TODO: if(!backslash && new_lines) apply width limit

    // output options for switch and macro
    smOutputOpts = EnumSet.copyOf(outputOpts);
    // SM_BACKSLASH implies SM_NEW_LINE
    if(smOutputOpts.contains(SM_BACKSLASH))
        smOutputOpts.add(SM_NEW_LINES);

    // NOTE: the lowest level output routines use the SM_ enums.
    // output options for run
    runOutputOpts = EnumSet.noneOf(OutputOptions.class);
    if(outputOpts.contains(RUN_INDENT))
        runOutputOpts.add(SM_INDENT);
    if(outputOpts.contains(RUN_NEW_LINES))
        runOutputOpts.add(SM_NEW_LINES);
}

@Override
public void exitMacro(MacroContext ctx)
{
    sb.setLength(0);
    sb.append("; MACRO ").append(ctx.id.getText());
    if(ctx.addr != null) {
        sb.append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
    }
    //sb.append('(').append(ctx.bs.size()).append(')');
    out.printf("\n%s\n", sb.toString());
    
    char quote = '"';
    char outerQuote = '\'';
    
    sb.setLength(0);
    sb.append("~M ")
            .append(macros.getVar(ctx.id.getText()).getAddr()).append(' ')
            .append(outerQuote);
    collectMacroStatements(sb, quote, smOutputOpts, apr, ctx.bs);
    endLine(sb, smOutputOpts);
    removeTrailingBlanks(sb).append(outerQuote);
    
    // TODO: Insert additional output info as comments.
    //       Some of the info may be produced by collect*Statements.
    
    out.printf("%s\n", sb.toString());
}

@Override
public void exitSwitch(SwitchContext ctx)
{
    sb.setLength(0);
    sb.append("; SWITCH ").append(ctx.id.getText()).append(' ');
    if(ctx.addr != null) {
        sb.append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
    }
    //sb.append('(').append(ctx.sc.size()).append(')');
    out.printf("\n%s\n", sb.toString());
    
    boolean hasSingleQuote = false;
    boolean hasDoubleQuote = false;
    // TODO: this check could be done in pass2 or pass3
    //       and save the results in a property assoc with SwitchContext.
    
    // first check out the embedded strings for type of quote being used
    for(int i = 0; i < ctx.sc.size(); i++) {
        String s;
        Switch_cmdContext sc_ctx = ctx.sc.get(i);
        if(sc_ctx.string != null) {
            s = sc_ctx.getText();
            if(s.startsWith("'"))
                hasSingleQuote = true;
            else
                hasDoubleQuote = true;
        }
        if(hasSingleQuote && hasDoubleQuote) {
            reportError(sc_ctx, "both \" and ' quotes used in switch '%s'", ctx.id.getText());
            break;
        }
    }
    
    
    char quote = hasSingleQuote ? '\'' : '"';
    char outerQuote = hasSingleQuote ? '"' : '\'';
    
    sb.setLength(0);
    sb.append("-M0 ")
            .append(switches.getVar(ctx.id.getText()).getAddr()).append(' ')
            .append(outerQuote);
    collectSwitchCmds(sb, quote, smOutputOpts, apr, ctx.switch_cmd());
    endLine(sb, smOutputOpts);
    removeTrailingBlanks(sb).append(outerQuote);
    
    // TODO: Insert additional output info as comments.
    //       Some of the info may be produced by collect*Statements.
    // TODO: annotations
    
    out.printf("%s\n", sb.toString());
}

@Override
public void exitRun(RunContext ctx)
{
    sb.setLength(0);
    sb.append("; RUN ");
    
    out.printf("\n%s", sb.toString());
    if(!runOutputOpts.contains(SM_NEW_LINES))
        out.printf("\n");
    
    sb.setLength(0);
    collectSwitchCmds(sb, '"', runOutputOpts, apr, ctx.switch_cmd());
    out.printf("%s\n", sb.toString());
}

@Override
public void exitCopy(CopyContext ctx)
{
    out.printf("\n; COPY\n");
    String s = ctx.getChild(1).getText();
    s = s.replace("\\}", "}");
    out.printf("%s\n", s);
}

private StringBuilder nextLine(StringBuilder sb,
                              EnumSet<OutputOptions> opts)
{
    endLine(sb, opts);
    if(opts.contains(SM_INDENT))
        sb.append("    ");
    return sb;
}

private StringBuilder endLine(StringBuilder sb,
                              EnumSet<OutputOptions> opts)
{
    if(opts.contains(SM_BACKSLASH)
            || opts.contains(SM_NEW_LINES))
        sb.append(' ');
    if(opts.contains(SM_BACKSLASH))
        sb.append("\\");
    if(opts.contains(SM_NEW_LINES))
        sb.append("\n");
    return sb;
}

private StringBuilder removeTrailingBlanks(StringBuilder sb)
{
    // Remove trailing blanks to avoid parse errors seen in Astrolog 7.60.
    // There's typically only one blank.
    int i;
    while((i = sb.length() - 1) >= 0 && sb.charAt(i) == ' ')
        sb.setLength(i);
    return sb;
}

/** Add the macro commands to sb */
private void collectMacroStatements(StringBuilder sb, char quote,
                              EnumSet<OutputOptions> opts,
                              AstroParseResult apr,
                              List<AstroExprStatementContext> laes)
{
    Objects.nonNull(quote); // it's read now
    for(AstroExprStatementContext aes_ctx : laes) {
        // TODO: option (or *.parse file) to see how it's parsed; where each 
        // AstroExpressionStatement has input/output displayed
        // Maybe even a truly verbose output
        if(opts.contains(SM_DEBUG)) {
            String originalText = getOriginalText(aes_ctx, apr.getInput());
            nextLine(sb, opts).append("/// ").append(originalText).append(" \\\\\\");
        }
        String s = apr.prefixExpr.removeFrom(aes_ctx.astroExpr.expr);
        nextLine(sb, opts).append(s);
    }
}

/** Add the switch commands to sb */
private void collectSwitchCmds(StringBuilder sb, char quote,
                              EnumSet<OutputOptions> opts,
                              AstroParseResult apr, List<Switch_cmdContext> lsc)
{
    StringBuilder lsb = new StringBuilder();
    List<String> cmdPart = new ArrayList<>(lsc.size());
    for(int i = 0; i < lsc.size(); i++) {
        lsb.setLength(0);
        Switch_cmdContext sc_ctx = lsc.get(i);
        if(sc_ctx.string != null) {
            lsb.append(sc_ctx.getText());
        } else {
            String astroExpr = apr.prefixExpr.removeFrom(sc_ctx);
            if(sc_ctx.name != null) {
                lsb.append(sc_ctx.name.getText()).append(' ');
                if(!astroExpr.isEmpty()) {
                    lsb.append(quote).append(astroExpr);
                    removeTrailingBlanks(lsb).append(quote);
                }
            } else { // expr_arg
                lsb.append(quote).append("~ ").append(astroExpr);
                removeTrailingBlanks(lsb).append(quote);
            }
        }
        cmdPart.add(lsb.toString());
    }
    for(String s : cmdPart) {
        // Don't put -- "~ AstroExpression " -- on new line
        if(s.startsWith("\"~"))
            sb.append(s).append(' ');
        else {
            nextLine(sb, opts).append(s).append(' ');
        }
    }
}
    
}
