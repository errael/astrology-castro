/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.tables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.Pass3;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprFuncContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Str_exprContext;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Registers;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.astrolog.castro.Error.*;
import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.Util.macroSwitchFuncArgs;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.Util.sizeArgs;
import static com.raelity.astrolog.castro.tables.Functions.reportFuncNargError;
import static com.raelity.astrolog.castro.tables.Functions.reportUserFuncNargError;

/**
 * The base class for any function; codegen and checking.
 * Could be regular Astrolog function, special func like switch/macro,
 * castro function. User defined function (really a macro).
 */
public abstract class Function
{
protected final String funcName;
protected final int narg;

static record FunctionUsage(Token id, int narg) {}

public Function(String funcName, int narg)
{
    this.funcName = funcName;
    this.narg = narg;
}

public final String name()
{
    return funcName;
}

public final int narg()
{
    return narg;
}

/** Invalid means that this Function definition itself has a problem;
 * it can't be used.
 */
public boolean isInvalid()
{
    return false;
}

public boolean isBuiltin()
{
    return true;
}

public boolean isUnknown()
{
    return false;
}

List<Function.FunctionUsage> getReferences()
{
    return Collections.emptyList();
}

public void addReference(Token id, int narg)
{
    // typically don't care about references.
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
public AstroMem targetMemSpace()
{
    return null;
}

/** By default, it's an error if there's a string argument */
public boolean checkReportArgTypes(Func_callContext ctx)
{
    if(!ctx.sargs.isEmpty()) {
        reportError(ctx, "'%s' does not take string arguments", ctx.id.getText());
        return false;
    }
    return true;
}

/**
 * Check for correct number of args and types.
 * This impl assumes expr args; see override in StringFunction,
 * for string args.
 * @return true if nargs is OK, else false if wrong num args or types.
 */
public boolean checkReportArgs(Func_callContext ctx)
{
    // by default, strings not allowed
    if(!checkReportArgTypes(ctx))
        return false;
    if(sizeArgs(ctx) != narg()) {
        reportFuncNargError(ctx, this);
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
public Functions.FunctionConstValue constValue(ExprFuncContext ctx)
{
    return Functions.NOT_CONST_VALUE;
}

/** generate code for this function call */
public abstract StringBuilder genFuncCall(StringBuilder sb,
                                              ExprFuncContext ctx,
                                              List<String> args);

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

    /////////////////////////////////////////////////////////////////////////
    //
    // Several "Function" subclasses
    //

    abstract public static class StringArgsFunction extends Function
    {
    public StringArgsFunction(String funcName, int narg)
    {
        super(funcName, narg);
    }
    
    @Override
    public boolean checkReportArgTypes(Func_callContext ctx)
    {
        boolean someError = false;
        if(!ctx.args.isEmpty())
            someError = true;
        if(!someError)
            // Not all <expr>, it's mixed. Make sure all strings.
            for(Str_exprContext arg : ctx.sargs) {
                if(arg.s == null) {
                    someError = true;
                    break;
                }
            }
        if(!someError)
            return true;
        reportError(ctx, "'%s' only takes string arguments", ctx.id.getText());
        return false;
    }
    } /////////// class StringArgsFunction

    /* ************************************************************* */

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

    /**
     * A UserFunction is basically a macro.
     * The macro name must be followed by "( ... )", zero or more arguments;
     * otherwise it is a simple macro.
     */
    static class UserFunction extends Function
    {
    /** The declared argument names are the variables for the user func. */
    // A sorted set, argument declaration order, would be nice.
    final List<String> argNames;
    
    UserFunction(String funcName, List<String> argNames)
    {
        super(funcName, argNames.size());

        if(new HashSet<>(argNames).size() != argNames.size())
            throw new IllegalArgumentException();

        this.argNames = List.copyOf(argNames);
    }
    
    @Override
    public boolean isBuiltin()
    {
        return false;
    }

    /**
     * Check for correct number of args.
     * A user function must match narg; no warning.
     * @return true if nargs is OK, else false if bad num args.
     */
    @Override
    public boolean checkReportArgs(Func_callContext ctx)
    {
        if(!checkReportArgTypes(ctx))
            return false;
        if(ctx.args.size() != narg()) {
            // TODO: Pass function, not all the other stuff
            reportUserFuncNargError(ctx, ctx.id.getText(), narg(), ctx.args.size());
            return false;
        }
        return true;
    }
    
    /** Assign the arguments to the macro's variables, invoke the macro.
     * Create a list that has the code to initialize macro function arguments;
     * for "m1(a + 13, b + 7)" it looks something like:
     * <br> "= 123 Add @a 13"
     * <br> "= 127 Add @b 7"
     * <br> "Macro 1"
     * <p>
     * Then encapsulate the code into a single Astrolog "DO*" expression.
     */
    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                                           List<String> args)
    {
        if(argNames.size() != args.size()) {

            throw new IllegalArgumentException();
        }

        List<String> callMacro = new ArrayList<>();
        Registers regs = lookup(Registers.class);
        // Set up the macro parameters.
        for(int i = 0; i < args.size(); i++) {
            Var var = regs.getVar(argNames.get(i));
            callMacro.add("= " + var.getAddr() + " " + args.get(i));
        }
        // Add the call to invoke the macro.
        Var macro = lookup(Macros.class).getVar(name());
        callMacro.add("Macro " + macro.getAddr() + " ");
        Pass3.appendDo(sb, callMacro, () -> "UserFunctionCall");

        return sb;
    }

    @Override
    public String toString()
    {
        return String.format("UserFunction{name:%s, args:%s}",
                             name(), argNames);
    }
    
    } /////////// UserFunction

    /* ************************************************************* */

    /**
     * Record name and number of args.
     * May be replaced by a user function.
     * At the end of pass1 these removed if not converted.
     */
    static class UnknownFunction extends Function
    {
    private List<FunctionUsage> references = new ArrayList<>();
    public UnknownFunction(Token id, int narg)
    {
        super(id.getText(), narg);
        references.add(new FunctionUsage(id, narg));
    }

    @Override
    public boolean isUnknown() {
        return true;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    };

    @Override
    List<FunctionUsage> getReferences() {
        return Collections.unmodifiableList(references);
    }

    @Override
    public void addReference(Token id, int narg) {
        references.add(new FunctionUsage(id, narg));
    }

    /**
     * Check for correct number of args.
     * UnknownFunction not handled here; just say it's ok.
     */
    @Override
    public boolean checkReportArgs(Func_callContext ctx)
    {
        return true;
    }

    @Override
    public StringBuilder genFuncCall(StringBuilder sb, ExprFuncContext ctx,
                                                           List<String> args)
    {
        throw new IllegalStateException();
    }
    } /////////// UnknownFunction

    /* ************************************************************* */

    static class SwitchFunction extends SwitchMacroFunction
    {
    public SwitchFunction() { super("Switch"); }

    @Override public AstroMem targetMemSpace() { return lookup(Switches.class); }
    } /////////// SwitchFunction

    static class MacroFunction extends SwitchMacroFunction
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
