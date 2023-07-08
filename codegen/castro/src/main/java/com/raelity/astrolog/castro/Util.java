/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.xpath.XPath;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.lib.CentralLookup;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.mems.Macros;
import com.raelity.astrolog.castro.mems.Switches;

import static com.raelity.antlr.ParseTreeUtil.getNthParent;

/**
 *
 */
public class Util
{
private Util() { }

// Not a convenient way...
// public static LineMap buildLineMap(String fName)
// {
//     new WriteableLineMap(new ArrayList<>(100));
// 
//     //https://stackoverflow.com/questions/14791997/how-to-know-offset-of-a-begining-of-a-line-in-text-file-in-java
//     //offset += strline.getBytes(Charset.defaultCharset()).length + 1;
// 
//     return null;
// }

public static Func_callContext lvalArg2Func(ParserRuleContext ctx)
{
    if(ctx instanceof LvalMemContext
            && (getNthParent(ctx, 3) instanceof Func_callContext fc_ctx))
        return fc_ctx;
    return null;
}

/**
 * If the lval is used in a function call as the name of a switch or macro,
 * return the memory space. The lval must be the sole argument.
 * <p>
 * TODO: honor a global on whether or not to return name or number.
 * @return address space of either switch or macro, else null
 */
public static AstroMem lval2MacoSwitchSpace(ParserRuleContext ctx)
{
    Func_callContext fc_ctx = lvalArg2Func(ctx);
    if(fc_ctx == null)
        return null;
    return func_call2MacoSwitchSpace(fc_ctx);
}

/** Check the func_call is switch() or macro() with one arg.
 * @return address space of either switch or macro, else null
 */
public static AstroMem func_call2MacoSwitchSpace(ParserRuleContext ctx)
{
    if(!(ctx instanceof Func_callContext fc_ctx))
        return null;
    // TODO: Put switches/macro in apr, then pass in apr
    if(fc_ctx.args.size() != 1)
        return null;
    String funcName = fc_ctx.id.getText();
    return "switch".equalsIgnoreCase(funcName)? lookup(Switches.class)
           : "macro".equalsIgnoreCase(funcName) ? lookup(Macros.class)
             : null;
}

private static XPath xpathFuncArgLval;
public static Collection<ParseTree> expr2Lvals(AstroParseResult apr, ParseTree pt)
{
    if(xpathFuncArgLval == null)
        xpathFuncArgLval = new XPath(apr.getParser(), "/expr/term/lval");
    return xpathFuncArgLval.evaluate(pt);
}
public static boolean isLvalExpr(AstroParseResult apr, ParseTree pt)
{
    return !expr2Lvals(apr, pt).isEmpty();
}



public static PrintWriter getErr()
{
    return lookup(CastroErr.class).pw();
}

public static void reportError(ParserRuleContext ctx, Object... msg)
{
    reportError(ctx.start, msg);
}

public static void reportError(Token token, Object... msg)
{
    report(true, token, msg);
}

public static void report(boolean fError, Token token, Object... msg)
{
    String optMsg = msg.length == 0 ? "" : String.format(
            (String)msg[0], Arrays.copyOfRange(msg, 1, msg.length));
    String lineText = getLineText(token);
    if(!lineText.isEmpty())
        lineText = "'" + lineText + "' ";
    getErr().printf("%s %s %s%s\n", (fError ? "Error:" : "Warn:"),
                    tokenLoc(token), lineText, optMsg);
    if(fError)
        lookup(AstroParseResult.class).countError();
}

public static String getLineText(Token token)
{
    // TODO: check return is empty string, try a backup
    return getLineText(token.getLine(), token.getInputStream());
}

//public static String getLineText(int line)
//{
//    return getLineText(line, Castro.getInput());
//}

public static String getLineText(int line, CharStream cs)
{
    LineMap lm = lookup(LineMap.class);

    Interval interval = lm != null ? lm.getInterval(line) : null;
    return interval != null ? cs.getText(interval) : "";
}

/** @return "fname:line:charpos" */
// TODO: return record("fname:line:charpos", "originalline text")
public static String tokenLoc(ParserRuleContext ctx)
{
    return tokenLoc(ctx.start);
}
public static String tokenLoc(Token token)
{
    if(token == null)
        return "";
    String fName = token.getTokenSource().getSourceName();
    if(!fName.equals(IntStream.UNKNOWN_SOURCE_NAME)) {
        Path path = Path.of(fName).toAbsolutePath(); 
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        fName = cwd.relativize(path).toString();
    }
    return String.format("%s:%d:%d",
                         fName,
                         token.getLine(),
                         token.getCharPositionInLine());
}

public static void checkReport(Var var, Object... msg)
{
    if(!var.hasError())
        return;
    String optMsg = msg.length == 0 ? "" : String.format(
            (String)msg[0], Arrays.copyOfRange(msg, 1, msg.length));
    if(!var.getConflicts().isEmpty()) {
        StringBuilder sb = new StringBuilder(optMsg)
                .append(" conflicts with");
        for(Var conflict : var.getConflicts()) {
            sb.append(' ').append(tokenLoc(conflict.getId()));
        }
        optMsg = sb.toString();
    }
    reportError(var.getId(), "%s %s %s", var.getName(), var.getState(), optMsg);
}

public static <T> T lookup(Class<T> clazz)
{
    return CentralLookup.getDefault().lookup(clazz);
}

public static <T> Collection<T> lookupAll(Class<T> clazz)
{
    return Collections.unmodifiableCollection(
            CentralLookup.getDefault().lookupAll(clazz));
}

public static void addLookup(Object instance)
{
    CentralLookup.getDefault().add(instance);
}

public static void removeLookup(Object instance)
{
    CentralLookup.getDefault().remove(instance);
}

public static void replaceLookup(Object instance)
{
    CentralLookup.getDefault().replace(instance);
}
}
