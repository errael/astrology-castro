/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;

import com.raelity.astrolog.castro.Castro.RegistersAccum;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

/**
 *
 * @author err
 */
public class Registers extends AstroMem
{
public static final String MEM_REGISTERS = "Registers";
public static final String VAR_CPRINTF_SAVE = "cprintf_save_area";

public Registers()
{
    super(MEM_REGISTERS, lookup(RegistersAccum.class));
    for(char c = 'a'; c <= 'z'; c++)
        declare(String.valueOf(c), 1, c - 'a' + 1, BUILTIN);
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
    if(var.getSize() == 1)
        out.printf("var %s @%d;    // %s %s%s\n", var.getName(),
                   var.getAddr(), var.getState(), f, lino);
    else
        out.printf("var %s[%d] @%d;    // %s %s%s\n", var.getName(),
                   var.getSize(), var.getAddr(), var.getState(), f, lino);
}
            
}
