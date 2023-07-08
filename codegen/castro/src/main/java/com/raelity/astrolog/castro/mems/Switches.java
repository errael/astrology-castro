/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;

import com.raelity.astrolog.castro.Castro.SwitchesAccum;

import static com.raelity.astrolog.castro.Util.lookup;

/**
 *
 * @author err
 */
public class Switches extends AstroMem
{
public Switches()
{
    super("Switches", lookup(SwitchesAccum.class));
}

@Override
void dumpVar(PrintWriter out, Var var)
{
    out.printf("switch %s @%d;    // %s\n", var.getName(),
               var.getAddr(), var.getState());
}
            
}
