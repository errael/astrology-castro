/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;


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
DumpDecl dumpVarDecl(Var var)
{
    return new DumpDecl("switch "+var.getName(), "@"+var.getAddr()+';');
}
            
}
