/*
 * Portions created by Ernie Rael are
 * Copyright (C) 2023 Ernie Rael.  All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * Contributor(s): Ernie Rael <errael@raelity.com>
 */

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
