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
public static final String MEM_SWITCHES = "Switches";
public Switches()
{
    super(MEM_SWITCHES, lookup(SwitchesAccum.class));
}

@Override
void dumpVar(PrintWriter out, Var var, boolean includeFileName)
{
    String f = "";
    String lino = "";
    if(includeFileName) {
        f = var.getFileName();
        lino = ":" + var.getId().getLine();
    }
    out.printf("switch %s @%d;    // %s %s%s\n", var.getName(),
               var.getAddr(), var.getState(), f, lino);
}
            
}
