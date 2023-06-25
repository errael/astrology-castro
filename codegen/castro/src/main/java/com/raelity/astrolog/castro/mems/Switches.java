/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;

/**
 *
 * @author err
 */
public class Switches extends AstroMem
{
public Switches()
{
    super("Switches");
}

@Override
void dumpVar(PrintWriter out, Var var)
{
    out.printf("switch %s @%d;    // %s\n", var.getName(),
               var.getAddr(), var.getState());
}
            
}
