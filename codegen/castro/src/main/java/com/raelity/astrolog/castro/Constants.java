/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.antlr.v4.runtime.Token;

import static com.raelity.astrolog.castro.Constants.ConstantType.*;

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
public static final int FK_F0_KEY_CODE = 200; 
public static final int FK_FIRST = 1; 
public static final int FK_LAST = 48; 

private static final Map<String, Info> constants = new HashMap<>();

/** Most astrolog named constants start with one of the following letters. */
private static String firstLetter = "moahskwz";

static {
    constants.put("true", new Info("True", ASTROLOG));
    constants.put("false", new Info("False", ASTROLOG));
    constants.put("signs", new Info("Signs", ASTROLOG));

    /** the "base" for X function keys. Add 1 for F1... */
    constants.put("fk_f0", new Info("FK_F0", String.valueOf(FK_F0_KEY_CODE), CASTRO));
    // TODO: add all the function keys individually

    //      F0          - 200   (not a function key, but good for math)
    //      F1          - 201
    //      Shift-F1    - 213
    //      Control-F1  - 225
    //      Alt-F1      - 237   (Shift-Control on some systems)
}

public static boolean isConstant(Token token)
{

    return isConstant(token.getText());
}

public static boolean isConstant(String id)
{
    return constant(id) != null;
}

public static String constantName(String _id)
{
    String id = _id.toLowerCase(Locale.ROOT);
    if(hasBuiltinPrefix(id))
        return id;
    Info info = constants.get(id);
    if(Boolean.FALSE) Objects.nonNull(info.type); // avoid not read warning
    return info != null ? info.id : null;
}

/**
 * For a builtin constant, return the name and let Astrolog handle it;
 * Otherwise return the value of the constant.
 * @return the value of the constant */
// TODO: should return "best case" value in all cases. (there's a pun in there)
public static String constant(String _id)
{
    String id = _id.toLowerCase(Locale.ROOT);
    if(hasBuiltinPrefix(id))
        return id;
    Info info = constants.get(id);
    return info != null ? info.val : null;
}

/** id MUST BE LOWER CASE */
private static boolean hasBuiltinPrefix(String id)
{
    // If id is at least 3 chars, and 2nd char is '_' and
    // first char is one of the magic chars, then return the id.
    // TODO: verify all ascii chars
    // TODO: keep a lookup table of all possible constants
    //       captured from Astrolog source.
    return id.length() > 2 && id.charAt(1) == '_'
            && firstLetter.indexOf(id.charAt(0)) >= 0;
}

enum ConstantType {
    ASTROLOG,
    CASTRO,
    USER,
    }

    private static class Info {
    private final String id;
    private final String val;
    private final ConstantType type;

    /** For ASTROLOG, String val gets passed through. */
    public Info(String val, ConstantType type)
    {
        this.id = val;
        this.val = val;
        this.type = type;
    }
    /** For CASTRO/USER, id is generated as val. */
    public Info(String id, String val, ConstantType type)
    {
        this.id = id;
        this.val = val;
        this.type = type;
    }

    }
}
