/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.TreeRangeSet;

import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

// TODO: preallocate limit

/**
 * AstroExpression register/memory space: layout, allocation;
 * an allocated variable has an addres {@literal >= 0}.
 * <p>
 * With declare() first build a set of all variables,
 * some of which may be allocated; allocated variables are
 * added to a RangeMap.
 * <p>
 * A memory space must be defined in this order
 * <br>1 - create the space
 * <br>2 - declare builtin/pre-allocated locations
 * <br>3 - declare remaining variables; some may require allocation
 * <br>4 - set limits; may cover locations from step 2 and 3
 * <br>5 - allocate
 */
abstract class AstroMem
{
public final String memSpaceName;
/** Allocated memory; value is the variable name.
 * Any key is a closed range. non-empty? Non-assigne/unallocated var could be empty.
 * 
 */
// May not *need* the RangeMap, but it can provide details messages
// about conflicting allocations.
private final RangeMap<Integer, Var> layout = TreeRangeMap.create();
private final NavigableSet<VarKey> vars = new TreeSet<>();
private final List<Var> varsError = new ArrayList<>();
// probably don't need/want this. Maybe some kind of allocation lock.
private boolean declarationsDone;
int nLimit; // use to label limit ranges (may be disjoint)

/** return v1 {@literal &} v2 */
public static <T extends Enum<T>> EnumSet<T> intersection(EnumSet<T> v1, EnumSet<T> v2)
{
    EnumSet<T> v0 = EnumSet.copyOf(v1);
    v0.retainAll(v2);
    return v0;
}

public static <T extends Enum<T>> boolean intersects(EnumSet<T> v1, EnumSet<T> v2)
{
    return !intersection(v1, v2).isEmpty();
}

public AstroMem(String name, int min, int max)
{
    memSpaceName = name;
    layout.put(Range.lessThan(min),
               new Var("#created_min", -1, min, INTERNAL));
    layout.put(Range.atLeast(Integer.MAX_VALUE),
               new Var("#created_max", -1, Integer.MAX_VALUE, INTERNAL));
}

public AstroMem(String name)
{
    this(name, 1, Integer.MAX_VALUE);
}

boolean check()
{
    boolean ok = true;
    for(Var var : layout.asMapOfRanges().values()) {
        if(var.hasError())
            ok = false;
    }
    for(VarKey var : vars) {
        if(((Var)var).hasError())
            ok = false;
    }
    return ok;
}

Map<Range<Integer>, Var> getAllocationMap()
{
    return Collections.unmodifiableMap(layout.asMapOfRanges());
}

/** @return variable for name or null if no such variable */
public Var getVar(String name)
{
    Objects.nonNull(name);
    Var var = (Var)vars.floor(new VarKey(name));
    return var != null && name.equals(var.getName()) ? var : null;
}

/** @return an iterator of the active variables */
public Iterator<Var> getVars()
{
    return new VarIter(vars.iterator());
}

/** @return an iterator of the variables with errors */
public Iterator<Var> getErrorVars()
{
    return new VarIter(varsError.iterator());
}

/**
 * Starting from pre-allocated variables, allocate memory for any variable
 * without an assigned memory location.
 * <p>
 * Create a set of allocated ranges, its complement is free memory;
 * first fit to allocate from free memory.
 * When a var is allocated, update it in the set vars,
 * add it to the layout.
 * 
 * @return true if allocation was successful
 * @throws OutOfMemory
 */
void allocate()
{
    if(Boolean.FALSE)
        if(!varsError.isEmpty())
            throw new IllegalStateException("allocate: have errors");
    if(Boolean.TRUE) { // TODO: make false, this is more properly an assert
        for(Var var : layout.asMapOfRanges().values()) {
            if(var.hasError())
                throw new IllegalStateException("Var errors found in layout");
        }
    }
    declarationsDone = true; // TODO: probably don't need/want this.
    RangeSet<Integer> used = TreeRangeSet.create(layout.asMapOfRanges().keySet());
    RangeSet<Integer> free = used.complement();
    //System.out.printf("free mem: %s\n", free.toString());
    for(VarKey varKey : vars) {
        Var var = (Var)varKey;
        if(var.hasError())
            throw new IllegalStateException("Can't allocate variable with errors");
        if(var.isAllocated())
            continue;
        found: {
            for(Range<Integer> rfree : free.asRanges()) {
                ContiguousSet<Integer> cs = ContiguousSet.create(rfree, DiscreteDomain.integers());
                if(cs.size() >= var.size) {
                    var.setAddr(cs.first());
                    Range<Integer> r = varToRange(var);
                    if(intersects(Var.manualAlloc, var.getState()))
                        throw new IllegalStateException("allocating, but manual alloc");
                    layout.put(r, var);
                    used.add(r);
                    var.addState(ALLOC);
                    //System.err.printf("allocate var: %s %s\n", var.name, r.toString());
                    break found;
                }
            }
            var.addState(OUT_OF_MEM);
            throw new OutOfMemory(var, free);
        }
        //System.out.printf("free mem: %s\n", free.toString());
    }
}

@SuppressWarnings("serial")
public final class OutOfMemory extends RuntimeException
{
OutOfMemory(Var var, RangeSet<Integer> free)
{
    super(String.format("%s: name %s, size %d, free %s",
                        AstroMem.this.getClass().getSimpleName(),
                        var.getName(), var.getSize(), free));
}
}

/** Add the variable without allocating it.
 * @return Var, may have error state.
 */
Var declare(String name, int size)
{
    return declare(name, size, -1);
}

/** Allocate the variable at the specified address.
 * @return Var, may have error state.
 */
Var declare(String name, int size, int addr, VarState... a_state)
{
    if(name == null || name.isEmpty())
        throw new IllegalArgumentException("Var name null or empty");
    //if(declarationsDone) // TODO: get rid of this
    //    throw new IllegalStateException("Var declaration after allocation");

    Var var = new Var(name, size, addr, a_state);
    if(intersects(Var.requiresAddr, var.getState()) && !var.isAllocated())
        throw new IllegalArgumentException("Must specify address for " + Var.requiresAddr);
    boolean addedToVars;
    Range<Integer> r = null;
    setup_var: {
        if(size < 1)
            var.addState(SIZE_ERR);
        if(!(addedToVars = vars.add(var)))
            var.addState(DUP_NAME_ERR);
        // Can still check range unless size issue
        if(var.hasState(SIZE_ERR) || !var.isAllocated())
            break setup_var;
        r = varToRange(var);
        if(!layout.subRangeMap(r).asMapOfRanges().isEmpty())
            var.addState(OVERLAP_ERR);
    }
    if(var.hasState(LIMIT) || var.hasState(INTERNAL))
        vars.remove(var);

    if(!var.hasError()) {
        if(var.isAllocated()) {
            layout.put(r, var);
            if(!var.hasState(BUILTIN) && !var.hasState(PRE_ASSIGN))
                var.addState(ASSIGN);
        }
    } else {
        varsError.add(var);
        if(addedToVars)
            vars.remove(var);
    }
    return var;
}

/** Prevent allocation of address less than or equal to lower.
 * Does not affect something already allocated.
 */
public void lowerLimit(int addr)
{
    RangeSet<Integer> ll = TreeRangeSet.create(List.of(Range.lessThan(addr + 1)));
    fixLimit(ll, "#reserver_limit_" + addr);
}

/** Prevent allocation of address greater than or equal to higher.
 * Does not affect something already allocated.
 */
public void upperLimit(int addr)
{
    RangeSet<Integer> ll = TreeRangeSet.create(List.of(Range.atLeast(addr)));
    fixLimit(ll, "#reserver_limit_" + addr);
}

private void fixLimit(RangeSet<Integer> limit, String varBaseName)
{
    // remove allocated items from the limit
    limit.removeAll(layout.asMapOfRanges().keySet());
    for(Range<Integer> r : limit.asRanges()) {
        ContiguousSet<Integer> rset = ContiguousSet.create(r, DiscreteDomain.integers());
        if(!rset.isEmpty())
            declare(varBaseName + "#" + ++nLimit,
                    rset.size(), rset.first(), LIMIT);
    }
}

private Range<Integer> varToRange(Var var)
{
    return Range.closedOpen(var.addr, var.addr + var.size);
}

public void dumpAllocation(PrintWriter out)
{
    Map<Range<Integer>, Var> allocationMap = getAllocationMap();
    RangeSet<Integer> used = TreeRangeSet.create(allocationMap.keySet());
    RangeSet<Integer> free = used.complement();
    out.printf("// memSpace: %s\n// used %s\n// free %s\n",
               memSpaceName, used, free);
    for(Entry<Range<Integer>, Var> entry : allocationMap.entrySet()) {
        out.printf("%s\n", entry);
    }
    out.flush();
}

    private class VarIter implements Iterator<Var>
    {
        final Iterator<? extends VarKey> it;

        public VarIter(Iterator<? extends VarKey> it)
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
            return (Var)it.next();
        }
    }

    /** For stuff like file/lino, ... */
    public interface VarInfo {}

    /** VarKey uniquely identifies a variable for equals,hashcode.
     */
    private class VarKey implements Comparable<VarKey>
    {
    private final String name;
    
    public VarKey(String name)
    {
        this.name = name;
    }
    
    public String getName()
    {
        return name;
    }

    @Override
    public int compareTo(VarKey o)
    {
        return name.compareTo(o.name);
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
        final VarKey other = (Var)obj;
        return Objects.equals(this.name, other.name);
    }
    }

    /**
     * A variable, it's size, it's addr; addr {@literal <} 0 means
     * not yet allocated.
     * <p>
     * <b>NOTE:</b> equals and hash are based only on name.
     */
    public class Var extends VarKey
    {
    //private final String name;
    private final int size;
    private int addr; // -1 means not allocated
    private final EnumSet<VarState> state;
    private VarInfo info; // Could have map of var:info, if not used much
    
    private Var(String name, int size, int addr, VarState... a_state)
    {
        super(name);
        //this.name = name;
        this.size = size;
        this.addr = addr;
        if(a_state.length > 0) {
            EnumSet<VarState> tmp = EnumSet.of(a_state[0], a_state);
            if(!canSpecify.containsAll(tmp))
                throw new IllegalArgumentException("Can't construct var with " + Arrays.asList(a_state));
            state = tmp;
        } else
            state = EnumSet.noneOf(VarState.class);
    }
    
    public enum VarState
    {
    BUILTIN,    // like [a-z] for variables
    PRE_ASSIGN, // like from an include, by default should not be output
    ASSIGN,     // var addr at declaration, not BUILTIN or PRE_ASSIGN
    ALLOC,      // var allocated an addr automatically
    //NOT_ALLOC,  // variable not yet allocated
    DUP_NAME_ERR, SIZE_ERR, OVERLAP_ERR,
    OUT_OF_MEM,
    INTERNAL,   // used internally, like upper/lower range boundaries
    LIMIT,    // user specified limits/restrictions, not a var
    }

    private static final EnumSet<VarState>
            anyError = EnumSet.of(DUP_NAME_ERR, SIZE_ERR, OVERLAP_ERR);
    private static final EnumSet<VarState>
            canSpecify = EnumSet.of(BUILTIN, PRE_ASSIGN, INTERNAL, LIMIT);
    private static final EnumSet<VarState>
            requiresAddr = EnumSet.of(BUILTIN, PRE_ASSIGN, LIMIT);
    private static final EnumSet<VarState>
            manualAlloc = EnumSet.of(BUILTIN, PRE_ASSIGN, ASSIGN);

    private void addState(VarState s)
    {
        state.add(s);
    }
    
    /** @return true if any error */
    public boolean hasError()
    {
        return intersects(Var.anyError, state);
    }
    
    /** @return true if specified state bit */
    public boolean hasState(VarState s)
    {
        return state.contains(s);
    }
    
    public EnumSet<VarState> getErrors()
    {
        return intersection(Var.anyError, state);
    }

    boolean isAllocated()
    {
        return addr > 0;
    }

    public EnumSet<VarState> getState()
    {
        return EnumSet.copyOf(state);
    }
    
    public int getSize()
    {
        return size;
    }

    public int getAddr()
    {
        return addr;
    }
    
    private void setAddr(int addr)
    {
        this.addr = addr;
    }
    
    public VarInfo getInfo()
    {
        return info;
    }
    
    public void setInfo(VarInfo info)
    {
        this.info = info;
    }
    
    @Override
    public String toString()
    {
        return "Var{" + "name=" + getName() + ", addr=" + addr + ", size=" + size
                + ", state=" + state + '}';
    }
    
    }

}
