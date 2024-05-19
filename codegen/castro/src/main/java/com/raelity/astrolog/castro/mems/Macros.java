/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;


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
DumpDecl dumpVarDecl(Var var)
{
    return new DumpDecl("macro "+var.getName(), "@"+var.getAddr()+';');
}
}
