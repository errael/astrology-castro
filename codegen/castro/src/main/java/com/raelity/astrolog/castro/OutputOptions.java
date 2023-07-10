/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

/**
 * Options used when generating output.
 */
public enum OutputOptions
{
// SM_ is for switch/macro
SM_BACKSLASH,  // implies SM_NEW_LINES
SM_NEW_LINES,
SM_INDENT,
SM_DEBUG,
RUN_NEW_LINES,
RUN_INDENT,
GENERAL_ANON,   // No dates/versions in output; for comparing with golden files.
}
