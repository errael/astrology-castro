/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

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

/** It is expected that the output is in a suitable format
 * for input into the compiler.
 */
public void dumpVars(PrintWriter out, boolean byAddr)
{
    Map<Range<Integer>, Var> allocationMap = getAllocationMap();
    ArrayList<Var> varsInError = Lists.newArrayList(getErrorVars());
    RangeSet<Integer> used = TreeRangeSet.create(allocationMap.keySet());
    RangeSet<Integer> free = used.complement();
    out.printf("// memSpace: %s\n// used %s\n// free %s\n// errors: %d\n",
               memSpaceName, used, free, varsInError.size());
    List<Var> vars = Lists.newArrayList(getVars());
    if(byAddr)
        Collections.sort(vars, (v1, v2) -> v1.getAddr() - v2.getAddr());
    else
        Collections.sort(vars);
    dumpVars(out, vars.iterator(), true);
}

public void dumpErrors(PrintWriter out)
{
    out.printf("// memSpace: %s. Variables with errors\n", memSpaceName);
    dumpVars(out, getErrorVars(), true);
}

private void dumpVars(PrintWriter out, Iterator<Var> it, boolean filter) {
    Objects.nonNull(filter); // it's used now
    for(; it.hasNext();) {
        Var var = it.next();
        //if(filter && (var.hasState(LIMIT) || var.hasState(INTERNAL)))
        //    continue;
        // PRE_ASSIGN comes from another file, don't necessarily output
        //if(var.hasState(PRE_ASSIGN))
        //    continue;
        if(var.getSize() == 1)
            out.printf("var %s @%d;    // %s\n", var.getName(),
                       var.getAddr(), var.getState());
        else
            out.printf("var %s[%d] @%d;    // %s\n", var.getName(),
                       var.getSize(), var.getAddr(), var.getState());
    }
    out.flush();
}

}
