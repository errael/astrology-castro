/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.BUILTIN;

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
}
