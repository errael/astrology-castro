/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.nio.file.Path;
import java.util.Arrays;

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
    String s = getLineText(var.getId());
    lookup(CastroErr.class).pw
            .printf("%s '%s' %s %s %s\n", tokenLoc(var.getId()),
                    s, var.getName(), var.getState(), optMsg);
    lookup(AstroParseResult.class).countError();
}

public static <T> T lookup(Class<T> clazz)
{
    return CentralLookup.getDefault().lookup(clazz);
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
