/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.tables;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.tables.Function.AstrologFunction;
import com.raelity.astrolog.castro.tables.Function.FunctionUsage;
import com.raelity.astrolog.castro.tables.Function.UnknownFunction;
import com.raelity.astrolog.castro.tables.Function.UserFunction;
import com.raelity.astrolog.castro.tables.Function.MacroFunction;
import com.raelity.astrolog.castro.tables.Function.SwitchFunction;

import static com.raelity.astrolog.castro.Castro.getAstrologVersion;
import static com.raelity.astrolog.castro.Error.*;
import static com.raelity.astrolog.castro.Util.lc;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.Util.sizeArgs;

/**
 * Manage the various types of functions.
 */
public class Functions
{
public static final String FUNC_ID_SWITCH = "switch";
public static final String FUNC_ID_MACRO = "macro";

/**
 * isConst - False means this given call is not a constant, ignore all else.<br>
 * constVal - constant value of the specific function call<br>
 * displayVal - user specified value of the specific function call<br>
 */
public static record FunctionConstValue(boolean isConst, int constVal, int displayVal){};
public static FunctionConstValue NOT_CONST_VALUE = new FunctionConstValue(false, 0, 0);

static void reportFuncNargError(Func_callContext ctx, Function f)
{
    reportError(FUNC_NARG, ctx,
                "function '%s' argument count, expect %d not %d",
                f.name(), f.narg(), sizeArgs(ctx));
}

// Like reportFuncNargError, but can not be converted to warning
static void reportUserFuncNargError(Object ctx_or_token,
                                            String name, int expect, int result)
{
    reportError(ctx_or_token,
                "macro function '%s' argument count, expect %d not %d",
                name, expect, result);
}

///////////////////////////////////////////////////////////////////////////////
//
// public methods to work with functions
//

/**
 * If unknown function is a warning,
 * then convert any unknown functions to AstrologFunctions.
 */
public static void cleanupAfterPass1()
{
    for( Entry<String, Function> e : functions.funcsModifiableMap.entrySet()) {
        Function f = e.getValue();
        if(f.isUnknown()) {
            for(FunctionUsage ref : f.getReferences()) {
                if(f.narg() == ref.narg())
                    reportError(FUNC_UNK, ref.id(), "unknown function '%s'", f.name());
                else
                    reportError(ref.id(),
                                "unknown function '%s', args expect %d not %d",
                                f.name(), f.narg(), ref.narg());
            }
            e.setValue(new AstrologFunction(f.name(), f.narg()));
        }
    }
}

public static void addFunction(Function f, String... aliases)
{
    if(functions.funcsModifiableMap.putIfAbsent(lc(f.name()), f) != null)
        throw new IllegalArgumentException();
    for(String alias : aliases) {
        functions.addAlias(alias, f.name());
    }
}

public static void addUserFunction(String name, List<String> args)
{
    Function fAlready = Functions.get(name);
    if(fAlready.isUnknown()) {
        for(FunctionUsage reference : fAlready.getReferences()) {
            if(reference.narg() != args.size()) {
                // The macro was used with a different arg count than
                // the declaration. Uses of this macro must get an error.
                reportUserFuncNargError(reference.id(),
                                        name, args.size(), fAlready.narg());
            }
        }

        // Take the unknown function out of the table.
        functions.funcsModifiableMap.remove(lc(name));
    }
    UserFunction f = new UserFunction(name, args);
    if(functions.funcsModifiableMap.putIfAbsent(lc(f.name()), f) != null)
        throw new IllegalArgumentException();
}

public static void addUnkownFunction(Token id, int narg)
{
    UnknownFunction f = new UnknownFunction(id, narg);
    if(functions.funcsModifiableMap.putIfAbsent(lc(f.name()), f) != null)
        throw new IllegalArgumentException();
}

/**
 * dummyFunction is a singleton DummyFunction. Used to avoid null return.
 */
private static Function dummyFunction = new DummyFunction();
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

/**
 * @return Function for the name
 */
public static Function get(String funcName)
{
    String lcName = lc(funcName);
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

// singleton
private static final Functions functions = new Functions();

private final Map<String, Function> funcsModifiableMap;
private final Map<String, Function> funcs;
private final Map<String,String> aliasFuncs;

private Functions() {
    // ~500 items, 700 entries, load-factor .72
    // keep modifiable funcs around to add unknown func.
    this.funcsModifiableMap = new HashMap<>(800);
    this.funcs = Collections.unmodifiableMap(funcsModifiableMap);
    this.aliasFuncs = new HashMap<>();

    AstrologFunctions astroFuncs = switch((Integer)getAstrologVersion()) {
    case Integer i when i < 770 ->  new AstrologFunctions_760(this);
    default ->                      new AstrologFunctions_770(this);
    };
    astroFuncs.createEntries();

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
    if(aliasFuncs.putIfAbsent(lc(castroName), lc(astroName)) != null)
        throw new IllegalArgumentException();
}

// Replace an existing function by the param function;
// based on the function name.
private void replaceWith(Function f)
{
    // Something should already be there
    if(funcsModifiableMap.put(lc(f.name()), f) == null)
        throw new IllegalArgumentException();
}

/** key is lower case. Save original name and nargs. */
void add(String funcName, int narg, String types)
{
    Objects.nonNull(types);
    funcsModifiableMap.put(lc(funcName), new AstrologFunction(funcName, narg));
}

}
