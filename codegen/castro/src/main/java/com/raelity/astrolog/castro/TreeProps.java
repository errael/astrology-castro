/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.util.IdentityHashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Taken from ParseTreeProperty<V>.
 */
public class TreeProps<V>
{
protected final Map<ParseTree, V> annotations;

public V get(ParseTree node) { return annotations.get(node); }
public void put(ParseTree node, V value) { annotations.put(node, value); }
public V removeFrom(ParseTree node) { return annotations.remove(node); }

public TreeProps()
{
    annotations = new IdentityHashMap<>();
}

public TreeProps(int capacity)
{
    annotations = new IdentityHashMap<>(capacity);
}

public int size()
{
    return annotations.size();
}
public Map<ParseTree,V> getMap()
{
    return annotations;
}

}
