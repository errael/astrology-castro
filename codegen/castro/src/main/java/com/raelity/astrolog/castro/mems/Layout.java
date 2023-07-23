/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

/**
 * Corresponds to the "layout" in the grammar.
 * All values are positive integers; a negative
 * indicate value not set.
 * @author err
 */
public class Layout
{
    public int base;
    //int stack;
    public int limit;
    public final RangeSet<Integer> reserve;
    private final AstroMem mem;

    /** The backref, mem, may be null if the layout is un-associated.
     */
    Layout(final AstroMem mem)
    {
        base = -1;
        limit = -1;
        this.mem = mem;
        this.reserve = TreeRangeSet.create();
    }

    public Layout()
    {
        this(null);
    }

    public AstroMem getMem()
    {
        return mem;
    }

    @Override
    public String toString()
    {
        return "Layout{" + "base=" + base + ", limit=" + limit + ", reserve=" +
                reserve + '}';
    }
    
}
