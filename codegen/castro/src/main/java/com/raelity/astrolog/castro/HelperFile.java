/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.Castro.CastroHelperName;
import com.raelity.astrolog.castro.Castro.CastroMapName;
import com.raelity.astrolog.castro.Castro.CastroPrintUsage;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprStringAssContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalIndirectContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Str_exprContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;
import com.raelity.astrolog.castro.tables.Function;
import com.raelity.astrolog.castro.tables.Function.StringArgsFunction;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Functions.FunctionConstValue;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import static com.raelity.astrolog.castro.Castro.HELPER_EXT;
import static com.raelity.astrolog.castro.Castro.IN_EXT;
import static com.raelity.astrolog.castro.Castro.VAR_CPRINTF_SAVE;
import static com.raelity.astrolog.castro.Castro.getAstrologVersion;
import static com.raelity.astrolog.castro.Compile.getFirstFileCastroIO;
import static com.raelity.astrolog.castro.Compile.isParsePass;
import static com.raelity.astrolog.castro.Compile.reportIOError;
import static com.raelity.astrolog.castro.Constants.FK_FIRST_SLOT;
import static com.raelity.astrolog.castro.Constants.FK_LAST_SLOT;
import static com.raelity.astrolog.castro.Util.isMacroSwitchFuncArgLval;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.Util.sizeArgs;
import static com.raelity.astrolog.castro.tables.Functions.NOT_CONST_VALUE;

/**
 * This is used only from/with Compile.
 */
class HelperFile
{
private HelperFile() { }
    
/** Done compiling, add anything needed to the HelperFile */
static void finishHelperFile()
{
    createFunctionKeyCodeAssist();
    createPrintSaveArea();
    createHelperLoad();  // Must be last thing in the file
}

// With 770's unlimited number of switch. We can create some helper switches,
// like for cprintf. The helper can only be created during the parsePass.

static void defineHelperStatement(String statement)
{
    if(!isParsePass())
        throw new IllegalStateException();
    if(getAstrologVersion() < 770
        || statement.isEmpty()
        || !createHelperFile())
        return;
    createPrintSaveArea();
    try {
        helperWriter.append(statement);
    } catch(Exception ex00) {
        if(ex00 instanceof IOException ex)
            reportHelperIOError(ex, helperPath);
        else
            throw ex00;
    }
}

static boolean hasHelperFile()
{
    return helperPath != null;
}

static Path getHelperPath()
{
    return helperPath;
}

private static PrintWriter helperWriter;
private static Path helperPath;
private static boolean helperError;

/** The helper file has the same base name as the map file
 * and is found in the same directory as the map file. */
private static boolean createHelperFile()
{
    if(hasHelperFile())
        return true;
    //if(workingFileData.isEmpty())
    //    return false;
    //CastroIO castroIO = workingFileData.get(0).castroIO();
    CastroIO castroIO = getFirstFileCastroIO();
    if(castroIO == null)
        return false;

    if(castroIO.inPath() == null)
        return false;
    // If the helper name option was set, then use that for the basename,
    // otherwise use the map for the basename. (which might be first file).
    String name = null;
    CastroHelperName castroHelperName = lookup(CastroHelperName.class);
    if(castroHelperName != null)
        name = castroHelperName.name();
    if(name == null)
        name = lookup(CastroMapName.class).mapName();
    //String name = lookup(CastroMapName.class).mapName();
    Path path = castroIO.inPath().resolveSibling(name + HELPER_EXT);
    PrintWriter out;
    try {
        out = new PrintWriter(Files.newBufferedWriter(path, WRITE, TRUNCATE_EXISTING, CREATE));
    } catch(IOException ex) {
        reportHelperIOError(ex, path);
        return false;
    }
    helperPath = path;
    helperWriter = out;
    return true;
}

static void closeHelperFile()
{
    if(!hasHelperFile())
        return;
    try {
        helperWriter.close();
    } catch(Exception ex00) {
        if(ex00 instanceof IOException ex)
            reportHelperIOError(ex, helperPath);
        else
            throw ex00;
    }
    helperWriter = null;
}

private static void reportHelperIOError(IOException ex, Path path)
{
    if(helperError)
        return;
    helperError = true;
    reportIOError(ex, path);
}

private static List<String> loadFiles;
static void setLoadFiles(List<String> files)
{
    loadFiles = new ArrayList<>(files);
}

/**
 * Add load commands to helper, "-i f1.as -i f2.as ...";
 * this goes at the end of the helper file
 */
private static void createHelperLoad()
{

    if(loadFiles == null)
        return;
    if(Castro.isNoHelperLoad())
        return;
    StringBuilder sb = new StringBuilder();
    sb.append("run {\n");
    for(String fn : loadFiles) {
        if(fn == null)
            continue;
        if(fn.endsWith(IN_EXT))
            fn = fn.substring(0, fn.length() - IN_EXT.length());
        sb.append("    -i \"").append(fn).append(".as\"").append('\n');
    }
    sb.append("}\n");
    defineHelperStatement(sb.toString());
    loadFiles = null;
}

private static boolean createdCprintfSaveArea;
static void createPrintSaveArea()
{
    if(createdCprintfSaveArea || !lookup(CastroPrintUsage.class).didPrint())
        return;
    createdCprintfSaveArea = true;
    // If there's already save area, then don't create another one.
    Var var = lookup(Registers.class).getVar(VAR_CPRINTF_SAVE);
    if(var != null)
        return;
    defineHelperStatement("var cprintf_save_area[10];\n");
}


/**
 * If any of the function key code funcs are reference,
 * then define them.
 */
private static void createFunctionKeyCodeAssist()
{
    List<String> kcFuncs = List.of("FK_F0_KC",
                                   "S_FK_F0_KC",
                                   "C_FK_F0_KC",
                                   "A_FK_F0_KC");
    if(!needKeyCodeHelper) {
        for(String kcFunc : kcFuncs) {
            Function func = Functions.get(kcFunc);
            if(func.isUnknown()) {
                needKeyCodeHelper = true;
                break;
            }
        }
    }
    if(needKeyCodeHelper)
        defineHelperStatement(base_function_key_code_code);
}
private static boolean needKeyCodeHelper;

private static final String base_function_key_code_code =
        """
        /* *****************************************************************
         *
         * Windows/linux Function key assist
         */

        /* **************
         *
         * [[SCA]_]FK_F0_KC
         *
         * Return the value for the given function key on the current system
         * Use with the "z" value of ~XQ and ~WQ.
         */
        macro base_function_key_code_code() {
            if(WIN()) {
                if(Version() == 7.70)
                    40148 - 1  //cmdMacro01 - 1
                else
                    40145 - 1;  //cmdMacro01 - 1 
            } else {
                201 - 1;
            }
        }
        macro   FK_F0_KC() { base_function_key_code_code(); }
        macro S_FK_F0_KC() { base_function_key_code_code() + FK_NKEY; }
        macro C_FK_F0_KC() { base_function_key_code_code() + 2 * FK_NKEY; }
        macro A_FK_F0_KC() { base_function_key_code_code() + 3 * FK_NKEY; }

        /* **************
         * 
         * Switch2KeyCode
         * The parameter is in the range 1-48.
         *
         * Convert a switch address to a KeyCode; used with ~XQ and ~WQ.
         * Set the "z" variable to the return of this function and the
         * switch is executed by astrolog's key handler.
         *
         * NOTE: Garbage In, Garbage Out. (parameter checking at compile time).
         */
        macro Switch2KeyCode_Helper(Switch2KeyCode_SwitchAddress_Parameter)
        {
            Switch2KeyCode_SwitchAddress_Parameter + base_function_key_code_code();
        }
        """;

//////////////////////////////////////////////////////////////////////
//
// HelperFileFunctions
//
// Switch2KeyCode
// Macro print and string assign
//
//////////////////////////////////////////////////////////////////////

static void addHelperFileFunctions()
{
    if(getAstrologVersion() >= 770) { // only enable with unlimited switches
        Functions.addFunction(new KeyCode(), "KeyC");
        Functions.addFunction(new Switch2KeyCode(), "Sw2KC");
        Functions.addFunction(new MacroPrintf());
        Functions.addFunction(new MacroCprintf());
    }
}

    /* ************************************************************* */
    private static class KeyCode extends StringArgsFunction
    {
    public KeyCode()
    {
        super("KeyCode", 1);
    }

    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
    {
        boolean isError = true;
        // There's one arg, should have three characters like "a"
        Token charCodeToken = ctx.fc.sargs.get(0).s;
        String charCodeString = charCodeToken.getText();
        if(charCodeString.length() < 3)
            reportError(charCodeToken, "empty string");
        else if(charCodeString.length() > 3)
            reportError(charCodeToken, "'%s' only a single character allowed",
                        charCodeString.substring(1, charCodeString.length()-1));
        else {
            char charCode = charCodeString.charAt(1);
            if(charCode < ' ' || charCode > '~')
                reportError(ctx.fc, "'%c' not in range ' ' to '~'", charCode);
            else {
                sb.append((int)charCode).append(' ');
                isError = false;
            }
        }
        if(isError)
            sb.append("#CHARCODE# ");
        return sb;
    }
    
    @Override
    public boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
    {
        return true;
    }

    } /////////// KeyCode

    /* ************************************************************* */

private static String SWITCH2KEYCODE_NAME = "Switch2KeyCode_Helper";

    private static class Switch2KeyCode extends Function
    {
    public Switch2KeyCode()
    {
        super("Switch2KeyCode", 1);
    }
    
    @Override
    public boolean checkReportArgs(Func_callContext ctx)
    {
        // This is called during pass1, flag that it's been used.
        needKeyCodeHelper = true;
        return super.checkReportArgs(ctx);
    }
    
    @Override
    public boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
    {
        isMacroSwitchFuncArgLval(ctx, targetMemSpace());
        return true;
    }

    @Override public AstroMem targetMemSpace() { return lookup(Switches.class); }

    @Override
    public FunctionConstValue constValue(ExprFuncContext ctx)
    {
        return NOT_CONST_VALUE;
    }

    /** Abuse the FunctionConstValue */
    private FunctionConstValue findSwitchAddress(ExprFuncContext ctx)
    {
        ExprContext sw = ctx.fc.args.get(0);
        boolean ok = true;
        int addr = targetMemSpace().getVar(sw.getText()).getAddr();
        if(addr < FK_FIRST_SLOT || addr > FK_LAST_SLOT)
            ok = false;
        //return new FunctionConstValue(ok, FK_F0_KEY_CODE + addr, addr);
        return new FunctionConstValue(ok, addr, addr);
    }


    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
    {
        FunctionConstValue swAddr = findSwitchAddress(ctx);
        if(!swAddr.isConst()) {
            ExprContext sw = ctx.fc.args.get(0);
            reportError(sw, "Switch '%s' @%d is not a function key address",
                        sw.getText(), swAddr.displayVal());
            return sb.append("#internalSwitch2KeyCodeError");
        }
        Function func = Functions.get(SWITCH2KEYCODE_NAME);
        func.genFuncCall(sb, null, List.of(""+swAddr.constVal()+" "));
        return sb;
    }

    } /////////// Switch2KeyCode

//////////////////////////////////////////////////////////////////////
//
// Helper code generation for macro string assignment.
// and see "class MacroCprintf extends Function" below
//

private static String MACRO_SETSTR_BASE_NAME = "castroHelperString_";

private record MacroSetStringProp(String switchName) {}
private static TreeProps<MacroSetStringProp> macroSetStringProps = new TreeProps<>();

static void macroStringAss(ExprStringAssContext ctx)
{
    if(ctx.l instanceof LvalIndirectContext)
        reportError(ctx, "'%s' not allowed for string assignment", ctx.l.getText());

    // The names unique part of the name is encounter order.
    // Derived from the number of entries in the map.
    MacroSetStringProp prop = new MacroSetStringProp(
            MACRO_SETSTR_BASE_NAME + (macroSetStringProps.size() + 1));
    macroSetStringProps.put(ctx, prop);

    // Assemble a switch statement cprintf helper for the macro string assign.
    @SuppressWarnings("ReplaceStringBufferByString")
    StringBuilder sb = new StringBuilder("switch ")
            .append(prop.switchName).append(" {\n")
            .append("    SetString ")
            .append(ctx.l.getText()).append(' ')        // variable
            .append(ctx.s.getText()).append("\n}\n");   // value
    defineHelperStatement(sb.toString());
}

static String genMacroStringAss(ExprStringAssContext ctx, String lhs, String rhs)
{
    MacroSetStringProp props = macroSetStringProps.get(ctx);
    Var var = lookup(Switches.class).getVar(props.switchName);
    if(var == null) {
        reportError(ctx, "'%s': '%s' is not defined, INTERNAL ERROR",
                                 ctx.getText(), props.switchName);
        return "#internalMacroStringAssError";
    }
    return "Switch " + var.getAddr() + ' ';
}

//////////////////////////////////////////////////////////////////////
//
// Helper code generation for macro string assignment.
// and see "class MacroCprintf extends Function" below
//

/* ************************************************************* */

private static String MACRO_PRINT_BASE_NAME = "castroHelperPrint_";

/** More than one context can share a macrocPrintProp. */
private static TreeProps<MacroPrintProp> macroPrintProps = new TreeProps<>();
private record MacroPrintProp(String switchName) {}

/**
 * Map fmt to switchName for sharing; distinguish cprintf/printf.
 * The key looks like "message:cprintf" or "message:printf".
 * For print statements that are string only, ie only a format string,
 * can share the generated switch.
 * <p>
 * Note, maybe later share if arguments are also the same.
 */
private static Map<String, MacroPrintProp> sharePrint = new HashMap<>();
private static String sharePrintKey(Function func, Func_callContext ctx)
{
    String fmt = ctx.sargs.get(0).getText();
    // strip the quotes, the same format string might use different
    // quotes in different uses.
    String without_quotes = fmt.length() < 3
                            ? "" : fmt.substring(1, fmt.length()-2);
    return without_quotes + ":" + func.name();
}
/**
 * Save a generated print switch that can be reused.
 * can be used for the specified print statement.
 */
private static void savePrintSwitch(Function func, Func_callContext ctx,
                                                   MacroPrintProp printProp)
{
    if(ctx.sargs.size() != 1)   // Currently only handle fmt only
        return;
    sharePrint.put(sharePrintKey(func, ctx), printProp);
}
/**
 * Find a switch that has already been generated tname that
 * can be used for the specified print statement.
 */
private static MacroPrintProp findPrintSwitch(Function func, Func_callContext ctx)
{
    if(ctx.sargs.size() != 1)   // Currently only handle fmt only
        return null;
    return sharePrint.get(sharePrintKey(func, ctx));
}

    /* ************************************************************* */

    private static class MacroCprintf extends MacroOutput
    {
    public MacroCprintf()
    {
        super("cprintf");
    }
    } /////////// MacroCprintf

    /* ************************************************************* */

    private static class MacroPrintf extends MacroOutput
    {
    public MacroPrintf()
    {
        super("printf");
    }
    } /////////// MacroPrintf

    /* ************************************************************* */

    /**
     * Output base for cprintf/printf use in a macro.
     */
    private abstract static class MacroOutput extends Function
    {
        public MacroOutput(String funcName)
        {
            super(funcName, -1);
        }
        
        @Override
        public boolean checkReportArgTypes(Func_callContext ctx)
        {
            if(sizeArgs(ctx) == 0 || !ctx.sargs.isEmpty())
                return true;
            reportError(ctx, "'%s' no string in arguments", ctx.id.getText());
            return false;

        }

        @Override
        public boolean checkReportArgs(Func_callContext ctx)
        {
            if(sizeArgs(ctx) == 0) {
                reportError(ctx, "'%s' requires at least one argument", ctx.id.getText());
                return false;
            }
            if(!checkReportArgTypes(ctx))
                return false;
            List<Str_exprContext> sargs = ctx.sargs;
            if(sargs.get(0).s == null) {
                reportError(ctx, "'%s' format must be a string", ctx.id.getText());
                return false;
            }
            // Split the values from the format.
            List<Str_exprContext> fArgs = sargs.subList(1, sargs.size());
            // Only the 1st arg may be a string, the rest must be e, not s.
            for(Str_exprContext arg : fArgs) {
                if(arg.s != null) {
                    reportError(ctx, "'%s': %s literal strings not allowed as arg",
                                     ctx.id.getText(), arg.s.getText());
                    return false;
                }
            }
            if(macroPrintProps.getMap().containsKey(ctx))
                throw new IllegalStateException();

            lookup(CastroPrintUsage.class).macroPrint(name());

            // Look for an already generated switch statement that can be used.
            MacroPrintProp printProp = findPrintSwitch(this, ctx);
            boolean canShare = printProp != null;

            if(!canShare) {
                // The names unique part of the name is encounter order.
                // Derived from the number of entries in the map.
                printProp = new MacroPrintProp(
                        MACRO_PRINT_BASE_NAME + (macroPrintProps.size() + 1));
            }
            macroPrintProps.put(ctx, printProp);
            savePrintSwitch(this, ctx, printProp);

            if(canShare)
                return true;

            assert printProp != null;

            // Assemble a switch statement cprintf helper for the macro cprintf.
            StringBuilder sb = new StringBuilder()
                    .append("switch ")
                    .append(printProp.switchName).append(" {\n    ")
                    .append(name()).append(' ')
                    .append(sargs.get(0).getText())     // the format string
                    .append(' ');
             

            if(fArgs.isEmpty()) {
                sb.append('\n');
            } else {
                sb.append("{~ ");
                for(Str_exprContext arg : fArgs)
                    sb.append(arg.e.getText()).append("; ");    // each expr
                sb.append("}\n");
            }
            sb.append("}\n");
            defineHelperStatement(sb.toString());
            return true;
        }

        @Override
        public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                         List<String> args)
        {
            MacroPrintProp prop = macroPrintProps.get(ctx.fc);
            Var var = lookup(Switches.class).getVar(prop.switchName);
            if(var == null) {
                reportError(ctx, "'%s': '%s' is not defined, INTERNAL ERROR",
                                 ctx.getText(), prop.switchName);
                return sb.append("#internalCprintfError");
            }
            return sb.append("Switch ").append(var.getAddr()).append(' ');
        }

    } /////////// MacroOutput

}
