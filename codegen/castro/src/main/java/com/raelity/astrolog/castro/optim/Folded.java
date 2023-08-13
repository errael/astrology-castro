/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.optim;

/**
 * Might extend with optype and/or precedence if want to get fancy.
 */
public class Folded
{
    long l;

    public Folded(long l)
    {
        this.l = l;
    }

    /** only used to get the final result. */
    public int val() {
        // TODO: overflow if doesn't fit in an int
        return (int)l;
    }

    @Override
    public String toString()
    {
        return "Folded{" + "l=" + l + '}';
    }

}
