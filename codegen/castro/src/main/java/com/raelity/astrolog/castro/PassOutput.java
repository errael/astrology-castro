/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.raelity.astrolog.castro.Castro.CastroOutputOptions;
import com.raelity.astrolog.castro.antlr.AstroParser.AstroExprStatementContext;
import com.raelity.astrolog.castro.antlr.AstroParser.CopyContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalArrayContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.RunContext;
import com.raelity.astrolog.castro.antlr.AstroParser.SwitchContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarDefContext;
import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.antlr.ParseTreeUtil.getOriginalText;
import static com.raelity.astrolog.castro.Error.*;
import static com.raelity.astrolog.castro.GenPrefixExpr.switchCommandExpressions;
import static com.raelity.astrolog.castro.OutputOptions.*;
import static com.raelity.astrolog.castro.Util.cleanString;
import static com.raelity.astrolog.castro.Util.collectAssignStrings;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.Util.writeRegister;
import static com.raelity.astrolog.castro.mems.Registers.VAR_CPRINTF_SAVE;
import static com.raelity.astrolog.castro.visitors.FoldConstants.fold2Int;

/**
 * Generate and Output the Astrolog code, as a .as file,
 * for the top level macro/switch/run/copy; variable initialization.
 * Use the AstroExpression compiled in pass3.
 * For the switch command expecially there is some
 * data massage going on to meet the Astrolog input requirements.
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
private final Registers registers;
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
    this.registers = lookup(Registers.class);
    this.macros = lookup(Macros.class);
    this.switches = lookup(Switches.class);
    this.out = lookup(CastroIO.class).pw();
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
    if(!smOutputOpts.contains(GENERAL_MIN)) {
        sb.setLength(0);
        sb.append("; MACRO ").append(ctx.id.getText());
        if(ctx.addr != null) {
            sb.append(' ').append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
        }
        out.printf("\n%s\n", sb.toString());
    }

    // NOTE: a macro never has a string in it (AFAICT)
    
    // Can change the quote preference to facilite diff
    char quote;
    char outerQuote;
    if(smOutputOpts.contains(GENERAL_QFLIP)) {
        quote = '\'';
        outerQuote = '\"';
    } else {
        quote = '"';
        outerQuote = '\'';
    }
    
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
    if(!smOutputOpts.contains(GENERAL_MIN)) {
        sb.setLength(0);
        sb.append("; SWITCH ").append(ctx.id.getText());
        if(ctx.addr != null) {
            sb.append(' ').append("@").append(apr.prefixExpr.removeFrom(ctx.addr));
        }
        out.printf("\n%s\n", sb.toString());
    }
    
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
        // MIXED_QUOTES
        // if(hasSingleQuote && hasDoubleQuote) {
        //     reportError(sc_ctx, "both \" and ' quotes used in switch '%s'", ctx.id.getText());
        //     break;
        // }
    }
    
    
    char quote;
    char outerQuote;
    if(smOutputOpts.contains(GENERAL_QFLIP)) {
        quote = hasDoubleQuote ? '"' : '\'';
        outerQuote = hasDoubleQuote ? '\'' : '"';
    } else {
        quote = hasSingleQuote ? '\'' : '"';
        outerQuote = hasSingleQuote ? '"' : '\'';
    }
    
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
    if(!smOutputOpts.contains(GENERAL_MIN)) {
        sb.setLength(0);
        sb.append("; RUN ");
        
        out.printf("\n%s", sb.toString());
        if(!runOutputOpts.contains(SM_NEW_LINES))
            out.printf("\n");
    }
    
    sb.setLength(0);
    collectSwitchCmds(sb, '"', runOutputOpts, apr, ctx.switch_cmd());
    out.printf("%s\n", sb.toString());
}

@Override
public void exitCopy(CopyContext ctx)
{
    if(!smOutputOpts.contains(GENERAL_MIN)) {
        out.printf("\n; COPY\n");
    }
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
            lsb.append(quote).append(cleanString(sc_ctx.string)).append(quote);
        } else {
            String sc_text = apr.prefixExpr.removeFrom(sc_ctx);
            if(sc_ctx.name != null) {
                // It's a regualar switch command
                lsb.append(sc_ctx.name.getText()).append(' ');
                if(!sc_text.isEmpty()) {
                    lsb.append(quote).append(sc_text);
                    removeTrailingBlanks(lsb).append(quote);
                }
            } else if(sc_ctx.expr_arg != null) {
                // AstroExpr as param to regular switch command
                lsb.append(quote).append("~ ").append(sc_text);
                removeTrailingBlanks(lsb).append(quote);
            } else if(sc_ctx.assign != null) {
                // ~2 or ~20 AstroExpr hooks
                List<String> strings = collectAssignStrings(sc_ctx);
                Var var = registers.getVar(sc_ctx.l.id.getText());
                // check if strings fit in allocated space.
                int room = 1;   // assume there's room for one string
                int offset = 0;
                if(sc_ctx.l instanceof LvalArrayContext arr_ctx) {
                    // can only check if there's a constant index
                    Integer constVal = fold2Int(arr_ctx.idx);
                    if(constVal != null) {
                        offset = constVal;
                        int size = var.getSize();
                        room = size - offset;
                    }
                }
                if(strings.size() > room)
                    reportError(ARRAY_OOB, sc_ctx.l,
                                "'%s' array index out of bounds", sc_ctx.l.getText());
                createStringAssignmenCommand(lsb, var.getAddr() + offset,
                                             strings, quote);
            } else
                throw new IllegalArgumentException(sc_ctx.getText());
        }
        cmdPart.add(lsb.toString());
    }

    // NOTE that lsc and cmdPart are in a 1-1 correspondance

    // check/fixup for special castro functions
    for(int pIdx = 0; pIdx < lsc.size(); pIdx++) {
        if(lsc.get(pIdx).name != null && lsc.get(pIdx).getText().equalsIgnoreCase("cprintf"))
            hackPrintf(quote, lsc.subList(pIdx, lsc.size()),
                       cmdPart.subList(pIdx, cmdPart.size()));
    }

    for(String s : cmdPart) {
        // Don't put -- "~ AstroExpression " -- on new line even with OutputOpt
        if(s.startsWith("\"~"))
            sb.append(s).append(' ');
        else {
            nextLine(sb, opts).append(s).append(' ');
        }
    }
}

/**
 * Modify cmdPart to do the printf.
 * The list args are typically sublists.
 * <p>
 * There must be at least 2 elements, the third is optional: <br>
 * 0 - cprintf <br>
 * 1 - format string <br>
 * 2 - optional expression array
 */
private void hackPrintf(char quote, List<Switch_cmdContext> lsc, List<String> cmdPart)
{
    if(lsc.size() < 2    // must be room for "formatstr, exprs are optional
            || lsc.get(1).string == null) {
        reportError(lsc.get(0), "cprintf must be followed by a format string");
        return;
    }
    // The expr_arg is optional, since "printf 'foo'" should work.
    List<String> eArgs = Collections.emptyList();
    // Note that expr_arg is never an empty list
    if(lsc.size() >= 3 && lsc.get(2).expr_arg != null)
        eArgs = switchCommandExpressions.get(lsc.get(2));

    // Convert the format string to something -YYT understands
    // and count the number of args in the format string while doing it.
    StringBuilder sb_tmp = new StringBuilder();
    String fmt = lsc.get(1).string.getText();
    int fmtArgs = 0;
    int state = 1;
    for(int i = 0; i < fmt.length(); i++) {
        char c = fmt.charAt(i);
        char fmtSpec = 0;
        switch(state) {
        case 1 -> {
            if(c == '%') {
                state = 2;
                continue;   // skipping the initial '%'
            }
            if(c == '\'' || c == '"')
                continue;   // discard quotes, can't generally have them in -YYT?
            sb_tmp.append(c);
        }
        case 2 -> {
            switch(c) {
            case '%' -> sb_tmp.append(c);
            case 's' -> fmtSpec = (char)('a' + fmtArgs);
            case 'd','i','f','g' -> fmtSpec = (char)('A' + fmtArgs);
            default ->
                reportError(lsc.get(1), "'%%%c' invalid format string", c);
            }
            if(fmtSpec != 0) {
                fmtArgs++;
                sb_tmp.append('\\').append(fmtSpec);
            }
            state = 1;
        }
        }
        if(fmtArgs > 10) {
            reportError(lsc.get(1), "'%d' too many cprintf arguments, limit 10",
                                    fmtArgs);
            return;
        }
    }
    if(fmtArgs  > 0 && eArgs.isEmpty()) {
        reportError(lsc.get(1),
                    "cprintf format string, '%s', needs arguments in '{~ }'", fmt);
        return;
    }

    if(fmtArgs != eArgs.size()) {
        reportError(lsc.get(1), "cprintf arg count mismatch: fmt %d, expr %d",
                                fmtArgs, eArgs.size());
        return;
    }
    sb_tmp.append(' ');
    String yytFormatString = sb_tmp.toString();

    sb_tmp.setLength(0);

    Var save_area = null;
    if(fmtArgs > 0) {
        sb_tmp.append("~1 ").append(quote);

        // save/restore variables used by printf if there's a save area
        save_area = registers.getVar(VAR_CPRINTF_SAVE);
        if(save_area != null) {
            if(fmtArgs > save_area.getSize())
            {
                reportError(lsc.get(0), "too many cprintf args '%d' for %s",
                            fmtArgs, VAR_CPRINTF_SAVE);
                save_area = null;
            } else {
                int addr = save_area.getAddr();
                for(int i = 0; i < fmtArgs; i++) {
                    sb_tmp.append("= ").append(addr + i).append(' ')
                            .append("@").append((char)('a' + i)).append(' ');
                }
            }
        }

        for(int i = 0; i < eArgs.size(); i++)
            sb_tmp.append('=').append((char)('a' + i))
                    .append(' ').append(eArgs.get(i));

        removeTrailingBlanks(sb_tmp).append(quote).append(' ');
    }

    //There are either 2 or 3 things to overwrite, depending on nArgs

    // load the printf args
    cmdPart.set(0, sb_tmp.toString());

    // execute the output
    sb_tmp.setLength(0);
    sb_tmp.append("-YYT ").append(quote).append(yytFormatString);
    removeTrailingBlanks(sb_tmp).append(quote);

    cmdPart.set(1, sb_tmp.toString());
    sb_tmp.setLength(0);


    if(save_area != null && fmtArgs != 0) {
        // restore the registers
        sb_tmp.append("~1 ").append(quote);
        int addr = save_area.getAddr();
        for(int i = 0; i < fmtArgs; i++) {
            sb_tmp.append("=").append((char)('a' + i)).append(' ')
                    .append("@").append(addr + i).append(' ');
        }
        removeTrailingBlanks(sb_tmp).append(quote).append(' ');
    }

    // If there were args, then clear 3rd item
    if(fmtArgs != 0)
        cmdPart.set(2, sb_tmp.toString());
}

private void createStringAssignmenCommand(StringBuilder lsb, int addr,
                                          List<String> strings, char quote)
{
    // Find the character to use for AstroExpr string separator.
    // Glom together all the strings, for easier search.
    char separator = (char)-1;
    if(strings.size() != 1) {
        String tString = String.join("", strings);
        String tryTheseChars = ";:,.?!@#$%^*&<>";
        found: {
            for(int i = 0; i < tryTheseChars.length(); i++) {
                separator = tryTheseChars.charAt(i);
                if(tString.indexOf(separator) < 0)
                    break found;
            }
            throw new IllegalArgumentException("Can't find a separator to use");
        }
    }
    if(strings.size() == 1)
        lsb.append("~2 ");
    else
        lsb.append("~20 ");
    lsb.append(addr).append(' ').append(quote);
    // found a char not in string, use it as the separator
    if(strings.size() == 1)
        lsb.append(strings.get(0));
    else
        lsb.append(separator)
                .append(String.join(String.valueOf(separator), strings));
    lsb.append(quote);
}

@Override
public void exitVarDef(VarDefContext ctx)
{
    if(ctx.init.isEmpty())
        return;
    sb.setLength(0);
    int addr = registers.getVar(ctx.id.getText()).getAddr();

    record ExprString(ExprContext e, String s){}

    char quote = '\'';
    if(ctx.init.get(0).s != null) {
        ctx.init.get(0);
        List<String> strings = ctx.init.stream()
                .map((e) -> cleanString(e.s))
                .collect(Collectors.toList());
        createStringAssignmenCommand(sb, addr, strings, quote);
        removeTrailingBlanks(sb).append("\n");
    } else {
        List<ExprString> les
                = ctx.init.stream()
                        .map((e) -> new ExprString(e.e, apr.prefixExpr.removeFrom(e)))
                        .collect(Collectors.toList());
        for(ExprString es : les) {
            sb.append("~1 '");
            writeRegister(sb, addr).append(' ').append(fold2Int(es.e, es.s));
            removeTrailingBlanks(sb).append("'\n");
            addr++;
        }
    }
    out.printf(sb.toString());
}

}
