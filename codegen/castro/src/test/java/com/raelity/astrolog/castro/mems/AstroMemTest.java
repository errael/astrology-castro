/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.util.EnumSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.raelity.astrolog.castro.mems.AstroMem.Var;

import static java.util.EnumSet.of;
import static org.junit.jupiter.api.Assertions.*;

import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.DUP_NAME_ERR;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.OVERLAP_ERR;
import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.SIZE_ERR;

/**
 *
 * @author err
 */
public class AstroMemTest
{

public AstroMemTest()
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
 * Test of allocate method, of class AstroMem.
 */
@Test
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public void testAllocate()
{
    System.out.println("allocate");
    AstroMem mem = new AstroMem();
    int free;
    Var var;
    // first pre-allocate so there are holes of size 1,2,3,∞
    var = mem.declare("pre1", 1, 101);
    assertFalse(var.hasError());
    free = var.getAddr() + var.getSize();
    var = mem.declare("pre2", 1, free + 1);
    assertFalse(var.hasError());
    free = var.getAddr() + var.getSize();
    var = mem.declare("pre3", 1, free + 2);
    assertFalse(var.hasError());
    free = var.getAddr() + var.getSize();
    var = mem.declare("pre4", 1, free + 3);
    assertFalse(var.hasError());
    
    // [(-∞..0], [1..1], [3..3], [6..6], [10..10], [2147483647..+∞)]

    Var var1 = mem.declare("var1", 4);
    Var var2 = mem.declare("var2", 3);
    Var var3 = mem.declare("var3", 2);
    Var var4 = mem.declare("var4", 1);
    assertFalse(var1.hasError());
    assertFalse(var2.hasError());
    assertFalse(var3.hasError());
    assertFalse(var4.hasError());
    mem.allocate();

    // Following depends on allocating from the beginning of free memory.
    assertEquals(111, var1.getAddr());
    assertEquals(107, var2.getAddr());
    assertEquals(104, var3.getAddr());
    assertEquals(102, var4.getAddr());

    // Check out getVar
    var = mem.getVar("pre1");
    assertNotNull(var);
    var = mem.getVar("pre3");
    assertNotNull(var);
    var = mem.getVar("var4");
    assertNotNull(var);
    var = mem.getVar("aaa");
    assertNull(var);
    var = mem.getVar("ttt"); // between "pre" and "var"
    assertNull(var);
    var = mem.getVar("zzz");
    assertNull(var);
}

/**
 * Test of declare method, of class AstroMem.
 */
@Test
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public void testDeclare()
{
    System.out.println("declare");
    AstroMem mem = new AstroMem();
    Var result = mem.declare("var1", 1, 101);
    assertFalse(result.hasError());
    result = mem.declare("var2", 1, 102);
    assertFalse(result.hasError());
    result = mem.declare("var3", 1, 102);
    assertTrue(result.hasError());
    assertEquals(of(OVERLAP_ERR), result.getErrors());
    result = mem.declare("var2", 1, 103);
    assertTrue(result.hasError());
    assertEquals(EnumSet.of(DUP_NAME_ERR), result.getErrors());
    // the range for the dup var2 should be added
    result = mem.declare("var1", 1, 102);
    assertTrue(result.hasError());
    assertEquals(EnumSet.of(DUP_NAME_ERR, OVERLAP_ERR), result.getErrors());
    result = mem.declare("varArray", 10, 111);
    assertFalse(result.hasError());
    result = mem.declare("var", 1, 116);
    assertTrue(result.hasError());
    assertEquals(EnumSet.of(OVERLAP_ERR), result.getErrors());
    result = mem.declare("var_20_100", 20, 101);
    assertTrue(result.hasError());
    assertEquals(EnumSet.of(OVERLAP_ERR), result.getErrors());
    result = mem.declare("var1", 0, 101);
    assertTrue(result.hasError());
    assertEquals(EnumSet.of(DUP_NAME_ERR, SIZE_ERR), result.getErrors());
    Var var_to_alloc = mem.declare("var_no_addr", 2);
    assertFalse(var_to_alloc.hasError());
    result = mem.declare("var_no_addr_2", 0);
    assertTrue(result.hasError());
    assertEquals(EnumSet.of(SIZE_ERR), result.getErrors());
    assertTrue(mem.check());

    // (-∞..0], [1..100], [101..101], [102..102], [111..120], [2147483647..+∞)
    assertEquals(-1, var_to_alloc.getAddr());
    mem.allocate();
    assertEquals(103, var_to_alloc.getAddr());
}

}
