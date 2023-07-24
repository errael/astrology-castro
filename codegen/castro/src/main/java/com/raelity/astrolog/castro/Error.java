/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

// TODO: could have the printf string in here.

/**
 * Errors that are reported (maybe not all of them);
 * option to make it a warning.
 */
public enum Error
{
FUNC_UNK("func-unk"),           // unknown function
FUNC_NARG("func-narg"),         // wrong number of function arguments
FUNC_CASTRO("func-castro"),     // internal function, used in code generation
VAR_RSV("var-rsv"),             // assigning variable to reserved area
ARRAY_OOB("array-oob"),         // array index out of bounds
OCTAL_CONST("octal-const"),     // octal constant
;
private String name;
Error(String name) {
    this.name = name;
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

@Override
public String toString()
{
    return name;
}

}
