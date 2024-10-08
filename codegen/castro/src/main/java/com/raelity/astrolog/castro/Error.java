/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

// TODO: could have the printf string in here.

/**
 * Errors that are reported (maybe not all of them);
 * option to make it a warning.
 */
public enum Error
{
FUNC_UNK("func-unk",          "unknown function"),
FUNC_NARG("func-narg",        "wrong number of function arguments"),
FUNC_CASTRO("func-castro",    "function used internally for code generation"),
VAR_RSV("var-rsv",            "assign variable to reserved area"),
ARRAY_OOB("array-oob",        "array index out of bounds"),
INNER_QUOTE("inner-quote",    "inner quote in string stripped"),
CONST_AMBIG("const-ambig",    "constant id is ambiguous"),
SWITCH_BASE("switch-base",    "switch base adjusted to after function keys"),
// ONLY_WARN("only-warn",        ""),

///// MIXED_QUOTES("mixed-quotes"),   // mixed quotes in command
/////                               see MIXED_QUOTES in PassOutput
;
private String name;
private String help;
Error(String name, String help) {
    this.name = name;
    this.help = help;
}

public record EParse(Error error, boolean negated){};
public static EParse parseErrorName(String _name)
{
    String name = _name;
    boolean negated = false;
    if(_name.startsWith("no-") || _name.startsWith("not-")) {
        negated = true;
        name = _name.substring(_name.indexOf('-') + 1);
    }
    for(Error e : Error.values()) {
        if(e.name.equals(name))
            return new EParse(e, negated);
    }
    return null;
}

public String help() { return help; }

@Override
public String toString()
{
    return name;
}

}
