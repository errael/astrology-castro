/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;


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

public Registers()
{
    super(MEM_REGISTERS, lookup(RegistersAccum.class));
    for(char c = 'a'; c <= 'z'; c++)
        declare(String.valueOf(c), 1, c - 'a' + 1, BUILTIN);
}


@Override
DumpDecl dumpVarDecl(Var var)
{
    sbTmp.setLength(0);
    sbTmp.append("var ").append(var.getName());
    // Add the array size, "[###]".
    if(var.getSize() != 1)
        sbTmp.append('[').append(var.getSize()).append(']');
    return new DumpDecl(sbTmp.toString(), "@" + var.getAddr() + ';');
}
            
}
