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
public Macros()
{
    super("Macros", lookup(MacrosAccum.class));
}

@Override
void dumpVar(PrintWriter out, Var var)
{
    out.printf("macro %s @%d;    // %s\n", var.getName(),
               var.getAddr(), var.getState());
}

}
