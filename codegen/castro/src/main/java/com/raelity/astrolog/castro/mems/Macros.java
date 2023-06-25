/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;

/**
 *
 * @author err
 */
public class Macros extends AstroMem
{
public Macros()
{
    super("Macros");
}

@Override
void dumpVar(PrintWriter out, Var var)
{
    out.printf("macro %s @%d;    // %s\n", var.getName(),
               var.getAddr(), var.getState());
}

}
