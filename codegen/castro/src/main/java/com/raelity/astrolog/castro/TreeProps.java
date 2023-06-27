/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

/**
 *
 * @author err
 */
public class TreeProps<T> extends ParseTreeProperty<T>
{
public int size()
{
    return annotations.size();
}
public Map<ParseTree,T> getMap()
{
    return annotations;
}

}
