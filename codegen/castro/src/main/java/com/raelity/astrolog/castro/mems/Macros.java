/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;

import com.raelity.astrolog.castro.Castro.MacrosAccum;

import static com.raelity.astrolog.castro.Util.lookup;

/**
 *
 * @author err
 */
public class Macros extends AstroMem
{
public static final String MEM_MACROS = "Macros";
public Macros()
{
    super(MEM_MACROS, lookup(MacrosAccum.class));
}

@Override
void dumpVar(PrintWriter out, Var var, boolean includeFileName)
{
    String f = includeFileName ? var.getFileName() : "";
    out.printf("macro %s @%d;    // %s %s\n", var.getName(),
               var.getAddr(), var.getState(), f);
}

}
