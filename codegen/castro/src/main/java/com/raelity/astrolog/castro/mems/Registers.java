/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;

import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

/**
 *
 * @author err
 */
public class Registers extends AstroMem
{
public Registers()
{
    super("Registers");
    for(char c = 'a'; c <= 'z'; c++)
        declare(String.valueOf(c), 1, c - 'a' + 1, BUILTIN);
}


@Override
void dumpVar(PrintWriter out, Var var)
{
    if(var.getSize() == 1)
        out.printf("var %s @%d;    // %s\n", var.getName(),
                   var.getAddr(), var.getState());
    else
        out.printf("var %s[%d] @%d;    // %s\n", var.getName(),
                   var.getSize(), var.getAddr(), var.getState());
}
            
}
