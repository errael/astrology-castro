/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.mems;

import java.io.PrintWriter;
import java.util.EnumSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.raelity.astrolog.castro.AstroParseResult;
import com.raelity.astrolog.castro.Util;
import com.raelity.astrolog.castro.mems.AstroMem.OutOfMemory;
import com.raelity.astrolog.castro.mems.AstroMem.Var;

import static java.util.EnumSet.of;
import static org.junit.jupiter.api.Assertions.*;

import static com.raelity.astrolog.castro.mems.AstroMem.Var.VarState.*;

/**
 *
 * @author err
 */
public class AstroMemTest
{

public AstroMemTest()
{
}

static AstroParseResult aprTesting;
@BeforeAll
public static void setUpClass()
{
    aprTesting = AstroParseResult.testingResult();
    Util.addLookup(aprTesting);
}

@AfterAll
public static void tearDownClass()
{
    Util.removeLookup(aprTesting);
}

@BeforeEach
public void setUp()
{
}

@AfterEach
public void tearDown()
{
}

    class AstroMemForTest extends AstroMem
    {
    public AstroMemForTest()
    {
        super("TestingMemorySpace", null);
    }

        @Override
        void dumpVar(PrintWriter out, Var var)
        {
            out.printf("Var dump: %s", var);
        }

    }

@Test
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "ThrowableResultIgnored"})
public void testRegistersMem()
{
    System.out.println("RegistersMem");
    Registers mem = new Registers();

    mem.declare("var0", 1, 30, EXTERN);
    mem.declare("var1", 10, 41);
    mem.declare("var2", 10, 95);
    mem.declare("var3", 10, 195);
    mem.lowerLimit(100);
    mem.upperLimit(201);

    Var var = mem.declare("var4", 1);
    mem.allocate();
    assertFalse(var.hasError());
    assertEquals(105, var.getAddr());
    mem.declare("var5", 31);
    Var var6 = mem.declare("var6", 31);

    Var var8 = mem.declare("var8", 1, 230); // out of bounds
    mem.allocate();
    assertTrue(var8.hasError());

    Var var7 = mem.declare("var7", 31);
    assertThrows(OutOfMemory.class, () -> mem.allocate());
    assertEquals(137, var6.getAddr());
    assertEquals(-1, var7.getAddr());

    mem.dumpAllocation(new PrintWriter(System.out));
    mem.dumpVars(new PrintWriter(System.out), true);
    mem.dumpVars(new PrintWriter(System.out), false);
    mem.dumpErrors(new PrintWriter(System.out));
}

/**
 *      test declare a_state errors
 *      Var constructor a_state errors
 */
@Test
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "ThrowableResultIgnored"})
public void testVarConstruction()
{
    System.out.println("varConstruction");
    AstroMem mem = new AstroMemForTest();

    assertThrows(IllegalArgumentException.class,
                 () -> mem.declare("var1", 1, 101, SIZE_ERR));
    mem.declare("var1", 1, 101);
    assertThrows(IllegalArgumentException.class,
                 () -> mem.declare("var2", 1, -1, BUILTIN));
    assertThrows(IllegalArgumentException.class,
                 () -> mem.declare("var2", 1, -1, EXTERN));
    assertThrows(IllegalArgumentException.class,
                 () -> mem.declare("var2", 1, -1, LIMIT));
    mem.declare("var2", 1, 102, BUILTIN);
    mem.declare("var3", 1, 103, EXTERN);
    mem.declare("var4", 1, 104, LIMIT);
}

/**
 * Test of allocate method, of class AstroMem.
 */
@Test
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public void testAllocate()
{
    System.out.println("allocate");
    AstroMem mem = new AstroMemForTest();
    mem.lowerLimit(100);

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
    AstroMem mem = new AstroMemForTest();
    mem.lowerLimit(100);

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
