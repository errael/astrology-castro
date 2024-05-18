/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.tables;

/**
 * Providers of Astrolog functions implement this.
 * Only one of the providers is executed.
 */
public interface AstrologFunctions
{
void createEntries();
}
