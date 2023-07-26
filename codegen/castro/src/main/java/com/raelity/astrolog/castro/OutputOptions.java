/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

/**
 * Options used when generating output.
 */
public enum OutputOptions
{
// SM_ is for switch/macro
SM_BACKSLASH("bslash"),  // implies SM_NEW_LINES
SM_NEW_LINES("nl"),
SM_INDENT("indent"),
SM_DEBUG("debug"),
RUN_NEW_LINES("run_nl"),
RUN_INDENT("run_indent"),
GENERAL_MIN("min"),
GENERAL_QFLIP("qflip"),
GENERAL_ANON(null),   // No dates/versions in output; for comparing with golden files.
;

private final String optname;

private OutputOptions(String optname)
{
    this.optname = optname;
}

public static OutputOptions parse(String name)
{
    for(OutputOptions oo : OutputOptions.values()) {
        if(oo.optname.equals(name))
            return oo;
    }
    return null;
}

}
