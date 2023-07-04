/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.RangeSet;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.xpath.XPath;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroOut;
import com.raelity.astrolog.castro.Castro.CastroOutputOptions;
import com.raelity.astrolog.castro.antlr.AstroParser.AstroExprStatementContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.RunContext;
import com.raelity.astrolog.castro.antlr.AstroParser.SwitchContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.lookupAll;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

/**
 * Run the passes. <br>
 * Pass1 - Layout info, var/mem symbol tables, check func name/nargs,
 *         Build LineMap.
 * Pass2 - Check used variables are defined.
 *         Check no blanks in switch cmd, proper switch expression usage.
 * Pass3 - Generate code.
 * Pass4 - Output code. TODO: integrate into pass3?
 * @author err
 */
public class Compile
{
private Compile() { }

static void compile()
{
    AstroParseResult apr = lookup(AstroParseResult.class);

    Pass1.pass1();

    PrintWriter out = lookup(CastroOut.class).pw();
    PrintWriter err = lookup(CastroErr.class).pw();

    if(apr.hasError()) {
        err.printf("Pass1: %d syntax errors\n", apr.getParser().getNumberOfSyntaxErrors());
        err.printf("Pass1: %d other errors\n", apr.errors());
    }

    Registers registers = lookup(Registers.class);
    Macros macros = lookup(Macros.class);
    Switches switches = lookup(Switches.class);


    registers.dumpLayout(out);
    macros.dumpLayout(out);
    switches.dumpLayout(out);

    applyLayoutsAndAllocate();

    if(apr.hasError())
        err.printf("After Allocation: %d errors\n", apr.errors());

    //compile.registers.dumpAllocation(out, EnumSet.of(BUILTIN));
    registers.dumpVars(out, true, EnumSet.of(BUILTIN));
    macros.dumpVars(out, true, EnumSet.of(BUILTIN));
    switches.dumpVars(out, true, EnumSet.of(BUILTIN));

    int pass1ErrorCount = apr.errors();

    Pass2.pass2();

    int pass2ErrorCount = apr.errors() - pass1ErrorCount;
    if(pass2ErrorCount != 0)
        err.printf("Pass2: %d errors\n", pass2ErrorCount);
    if(apr.hasError())
        return;
    out.printf("PROCEEDING TO CODE GENERATION\n");

    Pass3.pass3();

    //////////////////////////////////////////////////////////////////////
    // pass3 generated the code and left it hanging on
    // the related tree node: macro, switch, run

    Set<OutputOptions> _outputOpts = lookup(CastroOutputOptions.class).outputOpts();

    // TODO: if(!backslash && new_lines) apply width limit

    // NOTE: the lowest level routines use the SM_ enums.

    EnumSet<OutputOptions> smOutputOpts = EnumSet.noneOf(OutputOptions.class);
    smOutputOpts.addAll(_outputOpts);

    // SM_BACKSLASH implies SM_NEW_LINE
    if(_outputOpts.contains(OutputOptions.SM_BACKSLASH))
        smOutputOpts.add(OutputOptions.SM_NEW_LINES);

    StringBuilder sb = new StringBuilder(100);
    for(ParseTree tree : XPath.findAll(apr.getProgram(), "//macro", apr.getParser())) {
        MacroContext ctx = (MacroContext)tree;

        sb.setLength(0);
        sb.append("; MACRO ").append(ctx.id.getText());
        if(ctx.addr != null) {
            sb.append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
        }
        sb.append('(').append(ctx.bs.size()).append(')');
        out.printf("\n%s\n", sb.toString());

        char quote = '"';
        char outerQuote = '\'';

        sb.setLength(0);
        sb.append("~M ")
                .append(macros.getVar(ctx.id.getText()).getAddr()).append(' ')
                .append(outerQuote);
        collectMacroStatements(sb, quote, smOutputOpts, apr, ctx.bs);
        endLine(sb, smOutputOpts);
        sb.append(outerQuote);

        // TODO: Insert additional output info as comments.
        //       Some of the info may be produced by collect*Statements.

        out.printf("%s\n", sb.toString());
    }

    for(ParseTree tree : XPath.findAll(apr.getProgram(), "//switch", apr.getParser())) {
        SwitchContext ctx = (SwitchContext)tree;

        sb.setLength(0);
        sb.append("; SWITCH ").append(ctx.id.getText()).append(' ');
        if(ctx.addr != null) {
            sb.append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
        }
        sb.append('(').append(ctx.sc.size()).append(')');
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
        }
        if(hasSingleQuote && hasDoubleQuote)
            Util.reportError(ctx, "both \" and ' quotes used in switch %s", ctx.id.getText());
        
        
        char quote = hasSingleQuote ? '\'' : '"';
        char outerQuote = hasSingleQuote ? '"' : '\'';
        
        sb.setLength(0);
        sb.append("-M0 ")
                .append(switches.getVar(ctx.id.getText()).getAddr()).append(' ')
                .append(outerQuote);
        collectSwitchCmds(sb, quote, smOutputOpts, apr, ctx.switch_cmd());
        endLine(sb, smOutputOpts);
        sb.append(outerQuote);

        // TODO: Insert additional output info as comments.
        //       Some of the info may be produced by collect*Statements.

        out.printf("%s\n", sb.toString());
    }
    
    // NOTE: the lowlevel routines use SM_ enums.
    EnumSet<OutputOptions> runOutputOpts = EnumSet.noneOf(OutputOptions.class);
    if(_outputOpts.contains(OutputOptions.RUN_INDENT))
        runOutputOpts.add(OutputOptions.SM_INDENT);
    if(_outputOpts.contains(OutputOptions.RUN_NEW_LINES))
        runOutputOpts.add(OutputOptions.SM_NEW_LINES);

    for(ParseTree tree : XPath.findAll(apr.getProgram(), "//run", apr.getParser())) {
        RunContext ctx = (RunContext)tree;

        sb.setLength(0);
        sb.append("// RUN ");

        out.printf("\n%s", sb.toString());
        if(!runOutputOpts.contains(OutputOptions.SM_NEW_LINES))
            out.printf("\n");
        
        sb.setLength(0);
        collectSwitchCmds(sb, '"', runOutputOpts, apr, ctx.switch_cmd());
        out.printf("%s\n", sb.toString());
    }
}

private static StringBuilder nextLine(StringBuilder sb,
                              EnumSet<OutputOptions> opts)
{
    endLine(sb, opts);
    if(opts.contains(OutputOptions.SM_INDENT))
        sb.append("    ");
    return sb;
}

private static StringBuilder endLine(StringBuilder sb,
                              EnumSet<OutputOptions> opts)
{
    if(opts.contains(OutputOptions.SM_BACKSLASH)
            || opts.contains(OutputOptions.SM_NEW_LINES))
        sb.append(' ');
    if(opts.contains(OutputOptions.SM_BACKSLASH))
        sb.append("\\");
    if(opts.contains(OutputOptions.SM_NEW_LINES))
        sb.append("\n");
    return sb;
}

/** Add the macro commands to sb */
private static void collectMacroStatements(StringBuilder sb, char quote,
                              EnumSet<OutputOptions> opts,
                              AstroParseResult apr,
                              List<AstroExprStatementContext> laes)
{
    for(AstroExprStatementContext aes_ctx : laes) {
        // TODO: option (or *.parse file) to see how it's parsed; where each 
        // AstroExpressionStatement has input/output displayed
        // Maybe even a truly verbose output
        String s = apr.prefixExpr.removeFrom(aes_ctx.astroExpr.expr);
        nextLine(sb, opts).append(s);
    }
}

/** Add the switch commands to sb */
private static void collectSwitchCmds(StringBuilder sb, char quote,
                              EnumSet<OutputOptions> opts,
                              AstroParseResult apr, List<Switch_cmdContext> lsc)
{
    List<String> cmdPart = new ArrayList<>(lsc.size());
    for(int i = 0; i < lsc.size(); i++) {
        String s;
        Switch_cmdContext sc_ctx = lsc.get(i);
        if(sc_ctx.string != null) {
            s = sc_ctx.getText();
        } else {
            String astroExpr = apr.prefixExpr.removeFrom(sc_ctx);
            if(sc_ctx.name != null) {
                s = sc_ctx.name.getText() + ' ' + (!astroExpr.isEmpty()
                                                       ? quote + astroExpr + quote : "");
            } else { // expr_arg
                s = quote + "~ " + astroExpr + quote;
            }
        }
        cmdPart.add(s);
    }
    for(String s : cmdPart) {
        if(s.startsWith("\"~"))
            sb.append(" ").append(s);
        else {
            nextLine(sb, opts).append(s);
        }
    }
}

static void applyLayoutsAndAllocate()
{
    for(AstroMem mem : lookupAll(AstroMem.class)) {
        if(mem == null)
            continue;
        // warn if ASSIGN in reserve area
        RangeSet<Integer> reserve = mem.getLayoutReserve();
        for(var e : mem.getAllocationMap().entrySet()) {
            Var var = e.getValue();
            if(var.hasState(ASSIGN) && !var.hasState(LIMIT)
                    && reserve.intersects(e.getKey()))
                Util.report(false, var.getId(),
                       "'%s' assigned to reserve area", var.getName());
        }

        mem.allocate();
    }
}
    
}
