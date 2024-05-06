/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;


import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Range;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.raelity.astrolog.castro.Castro.CastroLineMaps;
import com.raelity.astrolog.castro.LineMap.WriteableLineMap;
import com.raelity.astrolog.castro.antlr.AstroParserBaseListener;
import com.raelity.astrolog.castro.antlr.AstroParser;
import com.raelity.astrolog.castro.antlr.AstroParser.Assign_macro_addrContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Assign_switch_addrContext;
import com.raelity.astrolog.castro.antlr.AstroParser.BaseContstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ConstContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ConstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LayoutContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Layout_regionContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LimitContstraintContext;
import com.raelity.astrolog.castro.antlr.AstroParser.MacroContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ProgramContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Rsv_locContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Str_exprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.SwitchContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarDefContext;
import com.raelity.astrolog.castro.antlr.AstroParser.VarContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Layout;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Functions.Function;

import static com.raelity.antlr.ParseTreeUtil.hasErrorNode;
import static com.raelity.astrolog.castro.Error.*;
import static com.raelity.astrolog.castro.LineMap.WriteableLineMap.createLineMap;
import static com.raelity.astrolog.castro.Util.checkReport;
import static com.raelity.astrolog.castro.Util.isBuiltinVar;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.optim.FoldConstants.reportFold2Int;
import static com.raelity.astrolog.castro.Util.isReportDupSym;
import static com.raelity.astrolog.castro.Util.replaceLookup;


/** Pass1, handle constant definitions, variable declarations, layout,
 * valid function name; parse pre-pass build line index map to interval.
 * <p>
 * Actually two passes. Parse pass only builds line map,
 * then vist for pass1 goal.
 * TODO: There are ways to build line map without parsing the file.
 * Publish {@link AstroMem}s and {@link LineMap} to lookup.
 */
class Pass1 extends AstroParserBaseListener
{
/** use line number as index, first entry is null. */
private final Registers registers;
private final Macros macros;
private final Switches switches;
private Layout workingLayout;

    private static class BuildLineMap implements ParseTreeListener
    {
    private final WriteableLineMap wLineMap;
    private final String fileName;

    public BuildLineMap()
    {
        this.fileName = lookup(CastroIO.class).inFile();
        this.wLineMap = createLineMap(fileName);
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx)
    {
        wLineMap.extendLine(ctx.start, ctx.stop);
    }
    
    @Override public void visitTerminal(TerminalNode tn) { }
    @Override public void visitErrorNode(ErrorNode en) { }
    @Override public void enterEveryRule(ParserRuleContext prc) { }
    }

static void pass1() {
    AstroParseResult apr = lookup(AstroParseResult.class);

    // Build the line map
    BuildLineMap blm = new BuildLineMap();
    apr.getParser().addParseListener(blm);
    // parse the file/program
    ProgramContext program = apr.getParser().program();
    // save result
    apr.setContext(program);

    // Install the line map for use during pass1
    replaceLookup(lookup(CastroLineMaps.class).lineMaps().get(blm.fileName));

    // Now run the first pass
    Pass1 pass1 = new Pass1();
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(pass1, apr.getProgram());
}

public Pass1()
{
    this.registers = lookup(Registers.class);
    this.macros = lookup(Macros.class);
    this.switches = lookup(Switches.class);
}

/** Check builtin variable declaration is an initialization.
 * @return true if token is builtin, otherwise false
 */
private boolean checkBuiltin(ParserRuleContext ctx, Token id,
                             ExprContext addr, Str_exprContext init)
{
        if(isBuiltinVar(id)) {
            if(addr != null)
                reportError(ctx, "'%s' Can not specify address of builtin variable", id.getText());
            else if(init == null)
                reportError(ctx, "'%s' builtin variable declaration requires initializer", id.getText());
            return true;
        }
        return false;
}

@Override
public void exitConst(ConstContext ctx)
{
    if(!isReportDupSym(ctx.id, true)) {
        Integer val = reportFold2Int(ctx.e);
        if(val != null)
            Constants.declareConst(ctx.id, val);
    }
}

/** Add a variable to the symbol table.
 * Allow builtin declarations that initialize: "var p {expr}", "var p[] {e1,e2}"
 */
void declareVar(VarDefContext ctx)
{
    TerminalNode idNode;
    ExprContext addrNode;
    int size;
    if(checkBuiltin(ctx, ctx.id, ctx.addr, ctx.init.isEmpty() ? null : ctx.init.get(0)))
        return;

    idNode = ctx.Identifier();
    addrNode = ctx.addr;
    if(ctx.arr == null)
        size = 1;
    else {
        if(ctx.size == null && ctx.init.isEmpty()) {
            reportError(ctx, "must specify array size if no initializers");
            size = 1; // avoid another error
        } else {
            if(ctx.size != null)
                size = reportFold2Int(ctx.size, -1);
            else
                size = ctx.init.size();
        }
    }
    if(ctx.init.size() > size)
        reportError(ctx, "too many initializers");
    if(hasErrorNode(idNode) || hasErrorNode(addrNode))
        return;
    Token id = idNode.getSymbol();
    if(isReportDupSym(id, false))
        return;
    int addr = addrNode == null ? -1 : reportFold2Int(addrNode, -1);
    Var var = registers.declare(id, size, addr);
    checkReport(var);

    // if any, check the initializer list.
    // TODO: put this into Util?
    int nStr = 0;
    int nExpr = 0;
    for(Str_exprContext se_ctx : ctx.init) {
        if(se_ctx.s != null)
            nStr++;
        if(se_ctx.e != null)
            nExpr++;
    }
    if(nStr > 0 && nExpr > 0)
        reportError(ctx.init.get(0),
                    "'%s' mixed numeric and string initialization", id.getText());
}

@Override
public void exitVar(VarContext ctx)
{
    ParseTree child = ctx.getChild(0);
    if(child != null && !(child instanceof ErrorNode))
        declareVar(ctx.v);
}

private void declareSwithOrMacro(AstroMem mem, ExprContext ctx, Token id)
{
    int addr;
    if(ctx == null || hasErrorNode(ctx))
        addr = -1;
    else
        addr = reportFold2Int(ctx, -1);
    Var var = mem.declare(id, 1, addr);
    checkReport(var);
};

@Override
public void exitMacro(MacroContext ctx)
{
    Function f = Functions.get(ctx.id.getText());
    // If a macro is declared with arguments,
    // then it must have a unique function name.
    if(ctx.has_paren == null || f.isInvalid()) {
        if(ctx.has_paren != null) {
            List<String> args = ctx.args.stream()
                    .filter((token) -> {
                        // Declare this argument variable; discard if error.
                        Var var = registers.declare(token, 1, -1);
                        checkReport(var);
                        return !var.hasError();
                    })
                    .map((token) -> token.getText())
                    .collect(Collectors.toList());
            Functions.addUserFunction(ctx.id.getText(), args);
        }
        declareSwithOrMacro(macros, ctx.addr, ctx.id);
    } else {
        if(f.isBuiltin())
            reportError(ctx, "'%s' is a builtin function", f.name());
        else
            reportError(ctx, "'%s' already declared as a function", f.name());
    }
}

@Override
public void exitSwitch(SwitchContext ctx)
{
    declareSwithOrMacro(switches, ctx.addr, ctx.id);
}

@Override
public void exitAssign_switch_addr(Assign_switch_addrContext ctx)
{
    declareSwithOrMacro(switches, ctx.addr, ctx.id);
}

@Override
public void exitAssign_macro_addr(Assign_macro_addrContext ctx)
{
    declareSwithOrMacro(macros, ctx.addr, ctx.id);
}

@Override
public void enterLayout(LayoutContext ctx)
{
    if(registers.getVarCount() > 0 || macros.getVarCount() > 0 ||
            switches.getVarCount() > 0)
        // TODO: the following doesn't work to print the line,
        //       see comment in exitEveryRule
        reportError(ctx,
            "layout must be first, before 'var' or 'macro' or 'switch'");
}

@Override
public void exitLayout(LayoutContext ctx)
{
    workingLayout = null;
}

@Override
public void exitLayout_region(Layout_regionContext ctx)
{
    Layout layout;
    if(hasErrorNode(ctx))
        layout = new Layout();
    else {
        layout = getAstroMem(ctx.start.getType()).getNewLayout();
        if(layout.getMem() == null)
            reportError(ctx, "'%s' layout already specified",
                         ctx.getText());
    }
    workingLayout = layout;
}

AstroMem getAstroMem(int region)
{
    return switch(region) {
    case AstroParser.Memory -> registers;
    case AstroParser.Macro -> macros;
    case AstroParser.Switch -> switches;
    default -> null;
    };
}

private int checkReportSimpleConstraint(ConstraintContext ctx,
                                        ExprContext i_ctx, int curVal)
{
    if(hasErrorNode(ctx))
        return -1;
    if(curVal >= 0) {
        reportError(ctx, "'%s' already set", ctx.start.getText());
        return -1;
    }
    return reportFold2Int(i_ctx, 0);
}

@Override
public void exitBaseContstraint(BaseContstraintContext ctx)
{
    int newVal = checkReportSimpleConstraint(ctx, ctx.i, workingLayout.base);
    if(newVal >= 0)
        workingLayout.base = newVal;
}

@Override
public void exitLimitContstraint(LimitContstraintContext ctx)
{
    int newVal = checkReportSimpleConstraint(ctx, ctx.i, workingLayout.limit);
    if(newVal >= 0)
        workingLayout.limit = newVal;
}

@Override
public void exitRsv_loc(Rsv_locContext ctx)
{
    if(hasErrorNode(ctx))
        return;
    int r1 = reportFold2Int(ctx.range.get(0), 0);
    int r2 = ctx.range.size() == 1 ? r1 + 1 : reportFold2Int(ctx.range.get(1), 0);
    if(r1 > r2) {
        reportError(ctx, "'%s' %d is greater than %d", ctx.getText(), r1, r2);
        int t = r1;
        r1 = r2;
        r2 = t;
    }
    workingLayout.reserve.add(Range.closedOpen(r1, r2));
}

@Override
public void exitExprFunc(ExprFuncContext ctx)
{
    Func_callContext fc_ctx = ctx.fc;
    Function f = Functions.get(fc_ctx.id.getText());
    if(f.isInvalid()) {
        reportError(FUNC_UNK, fc_ctx.id, "unknown function '%s'", fc_ctx.id.getText());
        Functions.recordUnknownFunction(fc_ctx.id.getText());
    }
    f.checkReportArgs(fc_ctx);
}
    
}
