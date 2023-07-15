/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.tables;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static com.raelity.astrolog.castro.antlr.AstroLexer.*;

/**
 *
 * @author err
 */
public class OpsTest
{

public OpsTest()
{
}

@BeforeAll
public static void setUpClass()
{
}

@AfterAll
public static void tearDownClass()
{
}

@BeforeEach
public void setUp()
{
}

@AfterEach
public void tearDown()
{
}

/**
 * Test of binFunc method, of class Ops.
 */
@Test
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public void testBinFunc()
{
    System.out.println("binFunc");
    
    String func = Ops.astroCode(LeftShift);
    assertEquals("<<", func);
    func = Ops.astroCode(LeftShiftAssign);
    assertEquals("<<=", func);
}

}
