/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import static com.raelity.astrolog.castro.Constants.ConstantType.BUILTIN;

/**
 * Handle constants. There are Astrolog Builtin constants.
 * Some day might have a "define"/"const".
 * <p>
 * For now, do a quick check for possible constant.
 * Would like to capture known constants from Astrolog. 
 */
public class Constants
{
private Constants() { }

private static final Map<String, Info> constants = new HashMap<>();
static {
    constants.put("true", new Info("True", BUILTIN));
    constants.put("false", new Info("False", BUILTIN));
    constants.put("signs", new Info("Signs", BUILTIN));
}
private static String firstLetter = "moahskwz";

public static boolean isConstant(Token token)
{

    return isConstant(token.getText());
}

public static boolean isConstant(String id)
{
    return findConstant(id) != null;
}

private static boolean hasBuiltinPrefix(String id)
{
    // If id is at least 3 chars, and 2nd char is '_' and
    // first char is one of the magic chars, then return the id.
    // TODO: keep a lookup table of all possible constants
    //       captured from Astrolog source.
    return id.length() > 2 && id.charAt(1) == '_'
            && firstLetter.indexOf(Character.toLowerCase(id.charAt(0))) >= 0;
}

// private static String findBuiltinConstant(String id)
// {
//     if(hasBuiltinPrefix(id))
//         return id;
//     Info info = constants.get(id.toLowerCase(Locale.ROOT));
//     return info.type == BUILTIN ? info.val : null;
// }
// private static boolean isBuiltinConstant(String id)
// {
// 
// }

// TODO: should return "best case" value. (there's a pun in there)
private static String findConstant(String id)
{
    if(hasBuiltinPrefix(id))
        return id;
    Info info = constants.get(id.toLowerCase(Locale.ROOT));
    return info != null ? info.val : null;
}

/**
 * For a builtin constant, return the name and let Astrolog handle it.
 * Otherwise retrun the value of the constant. (there is no otherwise for now)
 * @return the value of the constant */
public static String constant(String name)
{
    return findConstant(name);
}

enum ConstantType {
    BUILTIN,
    USER,
}

    private static class Info {
    private final String val;
    private final ConstantType type;

    public Info(String val, ConstantType type)
    {
        this.val = val;
        this.type = type;
    }
    }
}
