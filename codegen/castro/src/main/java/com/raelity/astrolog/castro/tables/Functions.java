/* Copyright © 2023 Ernie Rael. All rights reserved */

// This file is generated by a script: scipts/extract_functions
// from astrolog's express.cpp which is copyrighted by:
// Walter D. Pullen (Astara@msn.com, http://www.astrolog.org/astrolog.htm)

package com.raelity.astrolog.castro.tables;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Error.FUNC_CASTRO;
import static com.raelity.astrolog.castro.Error.FUNC_NARG;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.macroSwitchFuncArgs;
import static com.raelity.astrolog.castro.Util.reportError;

public class Functions
{
public static final String FUNC_ID_SWITCH = "switch";
public static final String FUNC_ID_MACRO = "macro";

/**
 * isConst - False means this given call is not a constant, ignore all else.<br>
 * realVal - constant value of the specific function call<br>
 * displayVal - user specified value of the specific function call<br>
 */
public static record FunctionConstValue(boolean isConst, int realVal, int displayVal){};
public static FunctionConstValue NOT_CONST_VALUE = new FunctionConstValue(false, 0, 0);

    /**
     * The base class for any function; codegen and checking.
     * Could be regular Astrolog function, special func like switch/macro,
     * castro function.
     */
    abstract public static class Function
    {
    protected final String funcName;
    protected final int narg;

    public Function(String funcName, int narg)
    { this.funcName = funcName; this.narg = narg; }

    public final String name() { return funcName; }
    public final int narg() { return narg; }

    /** Invalid means that this Function definition itself has a problem;
     * it can't be used.
     */
    public boolean isInvalid() {
        return false;
    }

    /** Check for special func args; returns true if no further
     * checking needed, doesn't mean there's not an error.
     * Typically meaningfull where unusual arguments
     * are expected, like switch/macro name. A false return doesn't
     * mean there's a problem, only that further checking is expected.
     */
    public boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
    {
        return false;
    }

     /* @return null for variable, else macro/switch memSpace */
    public AstroMem targetMemSpace() {
        return null;
    }

    /**
     * Check for correct number of args.
     * This impl assumes expr args; see override in StringFunction,
     * for string args.
     * @return true if nargs is OK, else false if bad num args.
     */
    public boolean checkReportArgs(Func_callContext ctx)
    {
        if(ctx.args.size() != narg()
                && !Functions.isUnknownFunction(ctx.id.getText())) {
            reportError(FUNC_NARG, ctx,
                        "function '%s' argument count, expect %d not %d",
                        ctx.id.getText(), narg(), ctx.args.size());
            return false;
        }
        return true;
    }

    /**
     * This returns a constant result of a given function invocation.
     * The default is not constant, this may be overriden for functions
     * that are constant given context.
     * <p>
     * NOTE that if being a constant is dependent on
     * an allocated addresses, then this must return false before isAllocFrozen()
     * is true.
     */
    public FunctionConstValue constValue(ExprFuncContext ctx) {
        return NOT_CONST_VALUE;
    }

    /** generate code for this function call */
    public abstract StringBuilder genFuncCall(
            StringBuilder sb, ExprFuncContext ctx, List<String> args);

    @Override
    public String toString()
    {
        return "Function{name:" + name() + '}';
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.funcName);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        final Function other = (Function)obj;
        return Objects.equals(this.funcName, other.funcName);
    }

    } /////////// class Function


    abstract public static class StringArgsFunction extends Function
    {
    public StringArgsFunction(String funcName, int narg)
    {
        super(funcName, narg);
    }

    @Override
    public boolean checkReportArgs(Func_callContext ctx)
    {
        if(ctx.strs.size() != narg()
                && !Functions.isUnknownFunction(ctx.id.getText())) {
            reportError(FUNC_NARG, ctx,
                        "function '%s' argument count, expect %d not %d",
                        ctx.id.getText(), narg(), ctx.strs.size());
            return false;
        }
        return true;
    }

    } /////////// class StringArgsFunction

public static void addFunction(Function f, String... aliases)
{
    if(functions.funcsModifiableMap.putIfAbsent(
            f.name().toLowerCase(Locale.ROOT), f) != null)
        throw new IllegalArgumentException();
    for(String alias : aliases) {
        functions.addAlias(alias, f.name());
    }
}

/** dummyFunction is a singleton DummyFunction */
private static Function dummyFunction = new DummyFunction();

/**
 * @return Function for the name
 */
public static Function get(String funcName)
{
    String lcName = funcName.toLowerCase(Locale.ROOT);
    String realName = functions.aliasFuncs.get(lcName);
    if(realName != null)
        lcName = realName;
    Function f = functions.funcs.get(lcName);
    return f != null ? f : dummyFunction;
}

// TODO: should this be Function.equals(Function)?
public static boolean eqfunc(String s1, String s2)
{
    Objects.nonNull(s1);
    Objects.nonNull(s2);
    return s1.equalsIgnoreCase(s2);
}

public static boolean funcSwitchOrMacro(String funcName)
{
    return eqfunc(FUNC_ID_SWITCH, funcName) || eqfunc(FUNC_ID_MACRO, funcName);
}

// Would like a read only unioun of regular maps and unknown maps.
// https://stackoverflow.com/questions/66428518/java-read-only-view-of-the-union-of-two-maps;
// But, unknown only needs to be a set; since unkown rest of info is derived.

/** Track a special function (unknown or magic);
 * further inquiries about this
 * name will not return null and narg returns 0. 
 * Keep a set of special function names. And add the unknown
 * to the main map to keep the rest of the code simple.
 */
public static void recordUnknownFunction(String funcName)
{
    String lc = funcName.toLowerCase(Locale.ROOT);
    if(functions.funcs.containsKey(lc) || functions.unknownFuncs.containsKey(lc))
        throw new IllegalArgumentException(funcName);
    functions.unknownFuncs.put(lc, null);
    functions.add(lc, 0, "R_");
}

public static boolean isUnknownFunction(String funcName)
{
    return functions.unknownFuncs.containsKey(funcName.toLowerCase(Locale.ROOT));
}

// singleton
private static final Functions functions = new Functions();

private final Map<String, Function> funcsModifiableMap;
private final Map<String, Function> funcs;
private final Map<String,String> aliasFuncs;
private final Map<String,Object> unknownFuncs;

private Functions() {
    // ~500 items, 700 entries, load-factor .72
    // keep modifiable funcs around to add unknown func.
    this.funcsModifiableMap = new HashMap<>(700);
    this.funcs = Collections.unmodifiableMap(funcsModifiableMap);
    this.unknownFuncs = new HashMap<>();
    this.aliasFuncs = new HashMap<>();
    new AstrologFunctions(this).createEntries();

    // TODO: Replace Macro()/Switch()
    replaceWith(new MacroFunction());
    replaceWith(new SwitchFunction());

    // Provide "evaluate both sides" semantics for "?:" if wanted.
    addAlias("QuestColon", "?:");
    addAlias("AssignObj", "=Obj");
    addAlias("AssignHou", "=Hou");
}

private void addAlias(String castroName, String astroName)
{
    if(aliasFuncs.putIfAbsent(castroName.toLowerCase(Locale.ROOT),
                              astroName.toLowerCase(Locale.ROOT)) != null)
        throw new IllegalArgumentException();
}

private void replaceWith(Function f)
{
    // Something should already be there
    if(funcsModifiableMap.put(f.name().toLowerCase(Locale.ROOT), f) == null)
        throw new IllegalArgumentException();
}

/** key is lower case. Save original name and nargs. */
void add(String funcName, int narg, String types)
{
    Objects.nonNull(types);
    funcsModifiableMap.put(funcName.toLowerCase(Locale.ROOT),
                           new AstrologFunction(funcName, narg));
}

    private static class DummyFunction extends Function
    {
    public DummyFunction()
    { super("#DummyFunction#", 0); }

    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
        { sb.append("#DummyFunctionCall#");  return sb;}
    @Override public boolean isInvalid() { return true; }
    } /////////// DummyFunction

    /** Handles almost all Astrolog builtin functions */
    static class AstrologFunction extends Function
    {

    AstrologFunction(String funcName, int narg)
    {
        super(funcName, narg);
    }

    @Override
    public AstroMem targetMemSpace()
    {
        return null;
    }

    @Override
    public StringBuilder genFuncCall(
            StringBuilder sb, ExprFuncContext ctx, List<String> args)
    {
        if(Ops.isAnyOp(funcName))
            reportError(FUNC_CASTRO, ctx.fc.id,
                        "using castro operator '%s' as a function",
                        ctx.fc.id.getText());
        sb.append(funcName).append(' ');

        for(String arg : args) {
            sb.append(arg);
        }
        return sb;
    }
    } /////////// AstrologFunction

    /* ************************************************************* */
    private static class SwitchFunction extends SwitchMacroFunction
    {
    public SwitchFunction() { super("Switch"); }

    @Override public AstroMem targetMemSpace() { return lookup(Switches.class); }
    } /////////// SwitchFunction

    private static class MacroFunction extends SwitchMacroFunction
    {
    public MacroFunction() { super("Macro"); }

    @Override public AstroMem targetMemSpace() { return lookup(Macros.class); }
    } /////////// MacroFunction

    /* ************************************************************* */
    private static abstract class SwitchMacroFunction extends Function
    {

    public SwitchMacroFunction(String name)
    {
        super(name, 1);
    }

    @Override
    public boolean isDoneReportSpecialFuncArgs(Func_callContext ctx)
    {
        return macroSwitchFuncArgs(ctx, targetMemSpace());
    }

    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                     List<String> args)
    {
        if(args.size() == 1)
            sb.append(name()).append(' ').append(args.get(0));
        else
            sb.append("#").append(name()).append(":args# ");
        return sb;
    }

    } /////////// SwitchMacroFunction

}
