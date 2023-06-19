/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.TreeRangeSet;

import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.DUP_NAME_ERR;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.NOT_ALLOC;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.OVERLAP_ERR;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.SIZE_ERR;

// TODO: preallocate builtin variables [a-z]
// TODO: preallocate limit

/**
 * AstroExpression register/memory space: layout, allocation;
 * an allocated variable has an addres {@literal >= 0}.
 * 
 * With declare() first build a set of all variables,
 * some of which may be allocated; allocated variables are
 * added to a RangeMap.
 */
class AstroMem
{
/** Allocated memory; value is the variable name.
 * Any key is a closed range. non-empty? Non-assigne/unallocated var could be empty.
 * 
 */
private final RangeMap<Integer, Var> layout = TreeRangeMap.create();
private final SortedSet<Var> vars = new TreeSet<>();
private final List<Var> varsError = new ArrayList<>();
// probably don't need/want this. Maybe some kind of allocation lock.
private boolean declarationsDone;

public AstroMem()
{
    layout.put(Range.lessThan(1), new Var("reserved_lessThan_1", -1, 0));
    layout.put(Range.atLeast(Integer.MAX_VALUE), new Var("reserved_atMost", -1, Integer.MAX_VALUE));

    // TODO: implement setBase(addr), and get rid of the following
    Var var = new Var("reserved_1_100", 100, 1);
    layout.put(varToRange(var), var);
}

boolean check()
{
    boolean ok = true;
    for(Var var : layout.asMapOfRanges().values()) {
        if(var.hasError())
            ok = false;
    }
    for(Var var : vars) {
        if(var.hasError())
            ok = false;
    }
    return ok;
}

/** @return variable info for name or null if no such variable */
Var getVar(String name)
{
    Objects.nonNull(name);
    Var var = null;
    // Note that Var equals/hash is based only on name
    SortedSet<Var> varLookup = vars.tailSet(new Var(name));
    if(!varLookup.isEmpty())
        var = varLookup.first();
    return var != null && name.equals(var.name) ? var : null;
}

/** @return an iterator of the active variables */
Iterator<Var> getVars()
{
    return new VarIter(vars.iterator());
}

/** @return an iterator of the variables with errors */
Iterator<Var> getErrorVars()
{
    return new VarIter(varsError.iterator());
}

/**
 * Starting with pre-allocated variables, allocate memory for any variable
 * without an assigned memory location.
 * <p>
 * Create a set of allocated ranges, its complement is free memory;
 * first fit to allocate from free memory.
 * When a var is allocated, update it in the set vars,
 * add it to the layout.
 * 
 * @return true if allocation was successful
 */
void allocate()
{
    for(Var var : layout.asMapOfRanges().values()) {
        if(var.hasError())
            throw new IllegalStateException("Variable with errors found in layout");
    }
    declarationsDone = true; // probably don't need/want this.
    //if(!varsError.isEmpty())
    //    throw new IllegalStateException("allocate: have errors");
    RangeSet<Integer> used = TreeRangeSet.create(layout.asMapOfRanges().keySet());
    RangeSet<Integer> free = used.complement();
    //System.out.printf("used mem: %s\n", used.toString());
    //System.out.printf("free mem: %s\n", free.toString());
    for(Var var : vars) {
        if(var.hasError())
            throw new IllegalStateException("Can't allocate variable with errors");
        if(var.isAllocated())
            continue;
        for(Range<Integer> rfree : free.asRanges()) {
            ContiguousSet<Integer> cs = ContiguousSet.create(rfree, DiscreteDomain.integers());
            if(cs.size() >= var.size) {
                var.addr = cs.first();
                Range<Integer> r = varToRange(var);
                layout.put(r, var);
                used.add(r);
                //System.err.printf("allocate var: %s %s\n", var.name, r.toString());
                break;
            }
        }
        //System.out.printf("used mem: %s\n", used.toString());
        //System.out.printf("free mem: %s\n", free.toString());
    }
    //System.err.println("allocate done");
}

/** Add the variable without allocating it.
 * @return Var, may have error state.
 */
Var declare(String name, int size)
{
    return declare(name, size, -1);
}

/** Add the variable at the specified address.
 * @return Var, may have error state.
 */
Var declare(String name, int size, int addr, VarState... state)
{
    if(name == null || name.isEmpty())
        throw new IllegalArgumentException("Var name null or empty");
    if(declarationsDone) // TODO: get rid of this
        throw new IllegalStateException("Var declaration after allocation");

    Var var = new Var(name, size, addr);
    boolean addedToVars;
    Range<Integer> r = null;
    setup_var: {
        if(size < 1)
            var.addState(SIZE_ERR);
        if(!(addedToVars = vars.add(var)))
            var.addState(DUP_NAME_ERR);
        // Can still check range unless size issue
        if(var.hasError(SIZE_ERR) || !var.isAllocated())
            break setup_var;
        r = varToRange(var);
        if(!layout.subRangeMap(r).asMapOfRanges().isEmpty())
            var.addState(OVERLAP_ERR);
    }

    if(!var.hasError()) {
        if(var.isAllocated())
            layout.put(r, var);
    } else {
        varsError.add(var);
        if(addedToVars)
            vars.remove(var);
    }
    return var;
}

private Range<Integer> varToRange(Var var)
{
    return Range.closedOpen(var.addr, var.addr + var.size);
}

    /** iterate Vars with no remove */
    private class VarIter implements Iterator<Var>
    {
        final Iterator<Var> it;

        public VarIter(Iterator<Var> it)
        {
            this.it = it;
        }

        @Override
        public boolean hasNext()
        {
            return it.hasNext();
        }

        @Override
        public Var next()
        {
            return it.next();
        }
    
    }

    /**
     * A variable, it's size, it's addr; addr {@literal <} 0 means
     * not yet allocated.
     * <p>
     * <b>NOTE:</b> equals and hash are based only on name.
     */
    class Var implements Comparable<Var>
    {
    private final String name;
    private final int size;
    private int addr; // -1 means not allocated
    private EnumSet<VarState> state;

    private Var(String name)
    {
        this.name = name;
        this.size = 0;
    }
    
    Var(String name, int size, int addr)
    {
        this.name = name;
        this.size = size;
        this.addr = addr;
    }
    
    enum VarState
    {
    BUILTIN,    // like [a-z] for variables
    PRE_ALLOC,  // from an include, should not be output
    NOT_ALLOC,  // variable not yet allocated
    DUP_NAME_ERR, SIZE_ERR, OVERLAP_ERR,
    }
    private static final EnumSet<VarState>anyError
            = EnumSet.of(VarState.DUP_NAME_ERR, VarState.SIZE_ERR, VarState.OVERLAP_ERR);
    private void addState(VarState info)
    {
        if(state == null)
            state = EnumSet.of(info);
        else
            state.add(info);
    }
    
    boolean hasError()
    {
        if(state == null)
            return false;
        EnumSet<VarState> tmp = EnumSet.copyOf(state);
        tmp.retainAll(anyError);
        return !state.isEmpty();
    }
    
    boolean hasError(VarState s)
    {
        if(state == null)
            return false;
        return state.contains(s);
    }
    
    EnumSet<VarState> getErrors()
    {
        if(state == null)
            return EnumSet.noneOf(VarState.class);
        EnumSet<VarState> tmp = EnumSet.copyOf(state);
        tmp.retainAll(anyError);
        return tmp;
    }

    boolean isAllocated()
    {
        return addr > 0;
    }

    EnumSet<VarState> getState()
    {
        EnumSet<VarState> tmp = state == null ? EnumSet.noneOf(VarState.class)
                                              : EnumSet.copyOf(state);
        if(!isAllocated())
            tmp.add(NOT_ALLOC);
        return tmp;
    }
    
    public String getName()
    {
        return name;
    }
    
    public int getSize()
    {
        return size;
    }
    
    public int getAddr()
    {
        return addr;
    }
    
    @Override
    public int compareTo(Var o)
    {
        return name.compareTo(o.name);
    }
    
    @Override
    public String toString()
    {
        return "Var{" + "name=" + name + ", addr=" + addr + ", size=" + size
                + ", state=" + state + '}';
    }
    
    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.name);
        return hash;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        final Var other = (Var)obj;
        return Objects.equals(this.name, other.name);
    }
    
    }

}
