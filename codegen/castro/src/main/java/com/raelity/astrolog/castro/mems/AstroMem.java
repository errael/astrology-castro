/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeMap;
import com.google.common.collect.TreeRangeSet;

import org.antlr.v4.runtime.Token;

import com.raelity.astrolog.castro.AstroParseResult;
import com.raelity.astrolog.castro.Castro.MemAccum;
import com.raelity.astrolog.castro.CastroIO;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.lib.collect.ValueHashMap;
import com.raelity.lib.collect.ValueMap;

import static com.raelity.astrolog.castro.Util.lookup;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;
import static com.raelity.lib.collect.Util.intersection;
import static com.raelity.lib.collect.Util.intersects;

// TODO: use common method names?
//       getVarCount --> size
//       getVars --> iterator, implement Iterable
//       getVar --> get

/**
 * AstroExpression register/macro/switch memory space: layout, allocation;
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
 * <p>
 * When the space is created, the defined (memory defined in spaces
 * already created) are copied into this space. In this fashion
 * name/address conflict are detected. Allocation occurs at the end of
 * the first pass; when a variable is allocated it is added to the global
 * alloc. At the begining of the allocation routine, the vars in alloc are
 * copied in to avoid conflicts. In addition, at the begining of allocate
 * spin through defined and anything not already in this space is copied in
 * (this accounts for stuff added to defined during pass1 after this
 * compiliation unit was processed).
 * <p>
 * Variable declarations can be read at the beginning,
 * before any files are compiled, they are marked as EXTERN. 
 * A variable can be declared as EXTERN more than once as long as
 * the size is the same. When multiple files are compiled together,
 * any variables defined in a file are added to a defined cache.
 * When a variable is declared, if it appears in the defined cache
 */
public abstract class AstroMem implements Iterable<Var>
{
private AstroMem defined;
private AstroMem alloc;
private MemAccum accum;
final String fileName;
public final String memSpaceName;
static final String ALLOC_TAG = "#alloc#";
boolean isTest;
/** Allocated memory; value is the variable name.
 * Any key is a closed range. non-empty? Non-assigne/unallocated var could be empty.
 * 
 */
// May not *need* the RangeMap, but it can provide details messages
// about conflicting allocations.
private final RangeMap<Integer, Var> layout = TreeRangeMap.create();
private final ValueMap<String, Var> vars = new ValueHashMap<>((var) -> var.getName());
private final List<Var> varsError = new ArrayList<>();
int nLimit; // use to label limit ranges (may be disjoint)
// probably don't need/want this. Maybe some kind of allocation lock?
private boolean lockMemory;

public AstroMem(String name, int min, int max, MemAccum accum)
{
    if(accum != null) {
        this.accum = accum;
        defined = accum.defined();
        alloc = accum.alloc();
        //extern = accum.extern();
    }
    CastroIO out = lookup(CastroIO.class);
    fileName = out != null ? out.inFile() : null;
    memSpaceName = name;
    layout.put(Range.lessThan(min),
               new Var("#created_min", -1, min, INTERNAL));
    layout.put(Range.atLeast(Integer.MAX_VALUE),
               new Var("#created_max", -1, Integer.MAX_VALUE, INTERNAL));

    // TODO: copy in the externs and then the defined.
    if(defined != null) {
        for(Var var : defined) {
            if(!var.getState().contains(BUILTIN))
                declare(var.getId(), var.getSize(), var.getAddr(), DEFINED);
        }
    }
}

/** Take the variables from this and add them to the gloabal defined pool.
 * Typically done at the end of pass 1 for a file. The next file's pass1
 * copies these into it's memory pool to check against them.
 */
public void addToDefined()
{
    for(Var var : this) {
        if(!var.getState().contains(BUILTIN))
            defined.declare(var.getId(), var.getSize(), var.getAddr(), DEFINED);
    }
}

public void addToGlobal()
{
    EnumSet<VarState> skip = EnumSet.of(BUILTIN, DEFINED);
    for(Var var : this) {
        if(!intersects(var.getState(), skip))
            accum.global().declare(var.getId(), var.getSize(), var.getAddr(), DEFINED);
    }
}

public void lockMemory()
{
    lockMemory = true;
}

public MemAccum getAccum()
{
    return accum;
}
public void clearExtraMems()
{
    defined = null;
    alloc = null;
    accum = null;
}

public AstroMem(String name, MemAccum accum)
{
    this(name, 1, Integer.MAX_VALUE, accum);
}

boolean check()
{
    boolean ok = true;
    for(Var var : layout.asMapOfRanges().values()) {
        if(var.hasError())
            ok = false;
    }
    for(Var var : vars.values()) {
        if(var.hasError())
            ok = false;
    }
    return ok;
}

private Layout layoutRestrictions;
/** return unspecified layout for this space; if the layout is
 * already specified then the returned layout has no back reference to this.
 * The layout must be filled in before invoking {@link #allocate() }.
 */
public Layout getNewLayout()
{
    Layout lr;
    if(layoutRestrictions == null) {
        lr = new Layout(this);
        layoutRestrictions = lr;
    } else
        lr = new Layout(null);
    return lr;
}

public RangeSet<Integer> getLayoutReserve()
{
    return layoutRestrictions != null
           ? ImmutableRangeSet.copyOf(layoutRestrictions.reserve)
           : ImmutableRangeSet.copyOf(Collections.emptyList());
}

public Map<Range<Integer>, Var> getAllocationMap()
{
    return Collections.unmodifiableMap(layout.asMapOfRanges());
}

/** @return variable for name or null if no such variable */
public Var getVar(String name)
{
    Objects.nonNull(name);
    return vars.get(name);
}

/** Number of variables, not including externally specified/builtin.
 * Performance note, this scans the variables.
 */
public int getVarCount()
{
    int nVar = (int)vars.values().stream()
            .filter((var) -> !intersects(Var.externalSpecify, var.getState()))
            .count();
    return nVar + varsError.size();
}

/** @return an iterator of the active variables */
//public Iterator<Var> getVars()
@Override
public Iterator<Var> iterator()
{
    return new VarIter(vars.values().iterator());
}

/** @return an iterator of the variables with errors */
public int getErrorVarsCount()
{
    return varsError.size();
}

/** @return an iterator of the variables with errors */
public Iterator<Var> getErrorVars()
{
    return new VarIter(varsError.iterator());
}

/**
 * Starting from pre-allocated variables, allocate memory for any variable
 * without an assigned memory location.
 * Before allocation the layoutRestrictions, if any, are applied.
 * <p>
 Create a set of allocated ranges, its complement is free memory;
 first fit to allocate from free memory.
 When a var is allocated, update it in the set vars,
 add it to the layout.
 * 
 * @return true if allocation was successful
 * @throws OutOfMemory
 */
public void allocate()
{
    if(Boolean.FALSE)
        if(!varsError.isEmpty())
            throw new IllegalStateException("allocate: have errors");
    if(Boolean.TRUE) // TODO: make false, this is more properly an assert
        for(Var var : layout.asMapOfRanges().values()) {
            if(var.hasError())
                throw new IllegalStateException("Var errors found in layout");
        }

    if(defined != null) {
        // add stuff defined in pass1 after this compilation unit's pass1.
        for(Var var : defined) {
            if(!var.getState().contains(BUILTIN)
                    && !vars.containsKey(var.getName()))
                declare(var.getName(), var.getSize(), var.getAddr(), DEFINED);
        }
    }
    if(alloc != null) {
        // copy in any vars allocated by previous compilation units
        for(Var var : alloc) {
            if(!var.getState().contains(BUILTIN))
                declare(var.getName(), var.getSize(), var.getAddr(), DEFINED);
        }
    }

    if(layoutRestrictions != null) {
        if(layoutRestrictions.base >= 0)
            lowerLimit(layoutRestrictions.base - 1);
        if(layoutRestrictions.limit >= 0)
            upperLimit(layoutRestrictions.limit);
        rangeLimit(layoutRestrictions.reserve);
    }

    //declarationsDone = true; // TODO: probably don't need/want this.
    RangeSet<Integer> used = TreeRangeSet.create(layout.asMapOfRanges().keySet());
    RangeSet<Integer> free = used.complement();
    //System.out.printf("free mem: %s\n", free.toString());

    // Sort by name for easily reproducable results
    List<Var> values = Lists.newArrayList(iterator());
    Collections.sort(values);
    for(Var var : values) {
        if(var.hasError())
            throw new IllegalStateException("Can't allocate variable with errors");
        if(var.isAllocated())
            continue;
        if(var.hasState(DEFINED))
            continue; // Don't try to allocate things from another file
        found: {
            for(Range<Integer> rfree : free.asRanges()) {
                ContiguousSet<Integer> cs = ContiguousSet.create(rfree, DiscreteDomain.integers());
                if(cs.size() >= var.size) {
                    var.setAddr(cs.first());
                    Range<Integer> r = varToRange(var);
                    if(intersects(Var.manualAddr, var.getState()))
                        throw new IllegalStateException("allocating, but manual alloc");
                    layout.put(r, var);
                    used.add(r);
                    var.addState(ALLOC);
                    if(!isTest)
                        declare(alloc, ALLOC_TAG, var.getId(),
                                var.getSize(), var.getAddr());
                    //System.err.printf("allocate var: %s %s\n", var.name, r.toString());
                    break found;
                }
            }
            var.addState(OUT_OF_MEM);
            lookup(AstroParseResult.class).countError();
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

/** Some HACKery to add a variable to a tracking pool with a different name.
 * This is probably only used to put stuff into the alloc pool.
 * The original name is already in a compilation unit's space, but not
 * allocated. So put it into the shared pool with a diffent name. Then it can
 * simply be added to the target without mucking around with
 * the data structures.
 */
private static void declare(AstroMem mem, String tag, Token id, int size, int addr)
{
    String text = tag + id.getText();
    Var var = mem.declare(text, size, addr, DEFINED);
    var.setId(id);
}

public final Var declare(Token id, int size, int addr, VarState... a_state)
{
    Var var = declare(id.getText(), size, addr, a_state);
    var.setId(id);
    return var;
}

public final Var declare(Token id, int size)
{
    Var var = declare(id.getText(), size);
    var.setId(id);
    return var;
}

/** Add the variable without allocating it.
 * @return Var, may have error state.
 */
public final Var declare(String name, int size)
{
    return declare(name, size, -1);
}

/** Allocate the variable at the specified address.
 * @return Var, may have error state.
 */
public final Var declare(String name, int size, int addr, VarState... a_state)
{
    if(name == null || name.isEmpty())
        throw new IllegalArgumentException("Var name null or empty");
    if(lockMemory)
        throw new IllegalStateException("Var declaration after memory locked");

    Var var = new Var(name, size, addr, a_state);
    if(intersects(Var.requiresAddr, var.getState()) && !var.isAllocated())
        throw new IllegalArgumentException("Must specify address for " + Var.requiresAddr);
    boolean addedToVars;
    Range<Integer> r = null;
    setup_var: {
        if(size < 1)
            var.addState(SIZE_ERR);
        addedToVars = !vars.containsKey(var.getName());
        if(addedToVars)
            vars.put(var);
        else
            var.addState(DUP_NAME_ERR, vars.get(var.getName()));
        // Can still check range unless size issue
        if(var.hasState(SIZE_ERR) || !var.isAllocated())
            break setup_var;
        r = varToRange(var);
        if(!layout.subRangeMap(r).asMapOfRanges().isEmpty())
            var.addState(OVERLAP_ERR, r);
    }
    // Some variables shouldn't be in the list of variables.
    if(var.hasState(LIMIT) || var.hasState(INTERNAL))
        vars.remove(var.getName());

    // either add var to allocation map or add it to layout
    if(!var.hasError()) {
        if(var.isAllocated()) {
            layout.put(r, var);
            if(!var.hasState(BUILTIN) && !var.hasState(EXTERN))
                var.addState(ASSIGN);
        }
    } else {
        varsError.add(var);
        if(addedToVars)
            vars.remove(var.getName());
    }
    return var;
}

/** Prevent allocation of address less than or equal to lower.
 * Does not affect something already allocated.
 */
public void lowerLimit(int addr)
{
    RangeSet<Integer> ll = TreeRangeSet.create(List.of(Range.lessThan(addr + 1)));
    fixLimit(ll, "#reserve_limit_" + addr);
}

/** Prevent allocation of address greater than or equal to higher.
 * Does not affect something already allocated.
 */
public void upperLimit(int addr)
{
    RangeSet<Integer> ll = TreeRangeSet.create(List.of(Range.atLeast(addr)));
    fixLimit(ll, "#reserve_limit_" + addr);
}

/** Prevent allocation of address greater than or equal to higher.
 * Does not affect something already allocated.
 */
public void rangeLimit(RangeSet<Integer> reserved)
{
    TreeRangeSet<Integer> ll = TreeRangeSet.create(reserved);
    //RangeSet<Integer> ll = TreeRangeSet.create(List.of(Range.atLeast(addr)));
    fixLimit(ll, "#reserve_limit_range");
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
    dumpAllocation(out, EnumSet.noneOf(VarState.class));
}
public void dumpAllocation(PrintWriter out, EnumSet<VarState> skip)
{
    Map<Range<Integer>, Var> allocationMap = getAllocationMap();
    RangeSet<Integer> used = TreeRangeSet.create(allocationMap.keySet());
    RangeSet<Integer> free = used.complement();
    out.printf("// memSpace: %s %s\n// used %s\n// free %s\n",
               memSpaceName, fileName, used, free);
    for(Entry<Range<Integer>, Var> entry : allocationMap.entrySet()) {
        if(intersects(entry.getValue().getState(), skip))
                continue;
        out.printf("%s\n", entry);
    }
    out.flush();
}


/** It is expected that the output is in a suitable format
 * for input into the compiler.
 */
public void dumpVars(PrintWriter out, boolean byAddr)
{
    dumpVars(out, byAddr, EnumSet.of(BUILTIN));
}

public void dumpVars(PrintWriter out, boolean byAddr, EnumSet<VarState> skip)
{
    Map<Range<Integer>, Var> allocationMap = getAllocationMap();
    ArrayList<Var> varsInError = Lists.newArrayList(getErrorVars());
    RangeSet<Integer> used = TreeRangeSet.create(allocationMap.keySet());
    RangeSet<Integer> free = used.complement();

    out.printf("// Space: %s %s\n// used %s\n// free %s\n// errors: %d\n",
               memSpaceName, fileName, used, free, varsInError.size());

    List<Var> varList = Lists.newArrayList(iterator());
    if(byAddr)
        Collections.sort(varList, (v1, v2) -> v1.getAddr() - v2.getAddr());
    else
        Collections.sort(varList);
    dumpVars(out, varList.iterator(), skip);
}

public void dumpErrors(PrintWriter out)
{
    out.printf("// Space: %s. Variables with errors\n", memSpaceName);
    dumpVars(out, getErrorVars(), EnumSet.noneOf(VarState.class));
}

private void dumpVars(PrintWriter out, Iterator<Var> it, EnumSet<VarState> skip) {
    for(; it.hasNext();) {
        Var var = it.next();
        if(intersects(var.getState(), skip))
            continue;
        dumpVar(out, var);
    }
    out.flush();
}

abstract void dumpVar(PrintWriter out, Var var);

public void dumpLayout(PrintWriter out)
{
    out.printf("// Space: %s. Layout: %s\n", memSpaceName, layoutRestrictions);
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
    public class VarKey implements Comparable<VarKey>
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
    /** Where the variable is defined */
    private Token id;
    ///// private int line;
    ///// private int col;
    ///// private VarInfo info; // Could have map of var:info, if not used much
    private Set<Var> conflicts = Collections.emptySet();
    
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
    DEFINED,    // defined in another file
    EXTERN,     // like from an include, by default should not be output
    ASSIGN,     // var addr at declaration, not BUILTIN or EXTERN
    ALLOC,      // var allocated an addr automatically
    DUMMY,      // e.g. for an undeclared variable found in an expression
    //NOT_ALLOC,  // variable not yet allocated
    DUP_NAME_ERR, SIZE_ERR, OVERLAP_ERR,
    OUT_OF_MEM,
    INTERNAL,   // used internally, like upper/lower range boundaries
    LIMIT,    // user specified limits/restrictions, not a var
    }

    /** Var errors, note that OOM is not a var error. */
    private static final EnumSet<VarState>
            anyError = EnumSet.of(DUP_NAME_ERR, SIZE_ERR, OVERLAP_ERR);
    /** State that can be specified as part of a declaration. */
    private static final EnumSet<VarState>
            canSpecify = EnumSet.of(BUILTIN, DEFINED, EXTERN, INTERNAL, LIMIT, DUMMY);
    /** Var/range that requires a manually specified address. */
    private static final EnumSet<VarState>
            //requiresAddr = EnumSet.of(BUILTIN, DEFINED, EXTERN, LIMIT);
            requiresAddr = EnumSet.of(BUILTIN, EXTERN, LIMIT);
    /** Var manually assigned an address (not automatically allocated). */
    private static final EnumSet<VarState>
            manualAddr = EnumSet.of(BUILTIN, DEFINED, EXTERN, ASSIGN);
    /** Var not specified in the compilation unit. */
    private static final EnumSet<VarState>
            externalSpecify = EnumSet.of(BUILTIN, DEFINED, EXTERN);

    private void addState(VarState s)
    {
        state.add(s);
    }

    private void addState(VarState s, Var conflicting)
    {
        state.add(s);
        addConflict(conflicting);
    }

    private void addState(VarState s, Range<Integer> conflicting)
    {
        state.add(s);
        for(Var overlap : layout.subRangeMap(conflicting).asMapOfRanges().values()) {
            addConflict(overlap);
        }
    }

    private void addConflict(Var conflicting)
    {
        if(conflicts.isEmpty())
            conflicts = new HashSet<>();
        conflicts.add(conflicting);
    }

    public Set<Var> getConflicts()
    {
        return Collections.unmodifiableSet(conflicts);
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

    public Token getId()
    {
        return id;
    }

    public void setId(Token id)
    {
        if(this.id != null)
            throw new IllegalStateException(String.format(
                    "var '%s' already has id; new %s", getName(), id.getText()));
        this.id = id;
        ///// this.line = id.getLine();
        ///// this.col = id.getCharPositionInLine();
    }

    public String getFileName()
    {
        return fileName;
    }

    ///// public int getLine()
    ///// {
    /////     return line;
    ///// }

    ///// public int getCol()
    ///// {
    /////     return col;
    ///// }
    
    ///// public VarInfo getInfo()
    ///// {
    /////     return info;
    ///// }
    ///// 
    ///// public void setInfo(VarInfo info)
    ///// {
    /////     this.info = info;
    ///// }
    
    @Override
    public String toString()
    {
        return "Var{" + "name=" + getName() + ", addr=" + addr + ", size=" + size
                + ", state=" + state + '}';
    }
    
    }

}
