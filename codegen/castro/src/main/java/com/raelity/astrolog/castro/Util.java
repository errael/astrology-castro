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

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.lib.CentralLookup;
import com.raelity.astrolog.castro.mems.AstroMem.Var;

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

public static PrintWriter getErr()
{
    return lookup(CastroErr.class).pw;
}

// TODO: put the following in Util
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
