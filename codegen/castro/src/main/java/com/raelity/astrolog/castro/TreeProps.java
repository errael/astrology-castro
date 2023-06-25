/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

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

}
