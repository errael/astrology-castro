/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.tables;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

import static com.raelity.astrolog.castro.antlr.AstroLexer.*;

/**
 * This is a list of control/operation AstroExpression functions;
 * are primarily used for flow control. Th
 */
public class Ops
{

public static enum Flow {
DO_WHILE(Do), IF_ELSE(Else),
FOR(For), IF(If),
WHILE(While), REPEAT(Repeat),
VAR   (Var),
XDO   (1000002),
XDO2  (1000003),
XDO3  (1000004),
MACRO (1000005),
SWITCH(1000006),
ASSIGN(1000007),
;
private final int key;
private Flow(int key) { this.key = key; }
public int key() { return key; }
}

/** @return AstroExpression function name corresponding to token as operator */
public static String astroCode(int token)
{
    return operations.ops.get(token);
}

public static boolean isAnyOp(String astroCode)
{
    return operations.ops.inverse().containsKey(astroCode);
}

public static boolean isAssignOp(int token)
{
    String code = astroCode(token);
    return code != null && code.length() > 1 && code.endsWith("=");
}

/** Map token number to AstroExpression operator function name */
private static final Ops operations = new Ops();
private final BiMap<Integer, String> ops;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private Ops()
{
    ImmutableBiMap.Builder<Integer,String> builder = ImmutableBiMap.builder();
    createEntries(builder);
    BiMap<Integer, String> ops00;
    try {
        ops00 = builder.buildOrThrow();
    } catch (Exception ex) {
        System.err.println("Ops Initialization: " + ex.getMessage());
        throw new RuntimeException(ex);
    }
    ops = ops00;
}

private void createEntries(ImmutableBiMap.Builder<Integer,String> builder)
{
    builder
            // Where it makes sense the Flow enum's key is the same
            // name as a related token
            .put(Do, "DoWhile")
            .put(Else, "IfElse")
            .put(For, "For")
            .put(If, "If")
            .put(While, "While")
            .put(Repeat, "DoCount")
            
            // Want all the AstroExpr control/code-gen functions,
            // on the right; make up some int that are not defined tokens.
            .put(Var, "Var")
            .put(Flow.XDO.key(), "Do")
            .put(Flow.XDO2.key(), "Do2")
            .put(Flow.XDO3.key(), "Do3")
            // .put(Macro, "Macro")         NOT USED FOR CODE GENERATION
            // .put(Switch, "Switch")       NOT USED FOR CODE GENERATION
            .put(Flow.ASSIGN.key(), "Assign")

            // Operators use the token to pick up the AstroExpr function
            
            .put(Less, "Lt")
            .put(LessEqual, "Lte")
            .put(Greater, "Gt")
            .put(GreaterEqual, "Gte")
            .put(LeftShift, "<<")
            .put(RightShift, ">>")
            .put(Plus, "Add")
            //ops.put(PlusPlus, "")    // not used
            .put(Minus, "Sub")
            //ops.put(MinusMinus, "")       // not used
            .put(Star, "Mul")
            .put(Div, "Div")
            .put(Mod, "Mod")
            .put(And, "And")
            .put(Or, "Or")
            //ops.put(AndAnd, "")       // not used
            //ops.put(OrOr, "")     // not used
            .put(Caret, "Xor")
            .put(Not, "Not")
            .put(Tilde, "Inv")
            //ops.put(Question, "")
            //ops.put(Colon, "")
            //ops.put(Semi, "")
            //ops.put(Comma, "")
            .put(Equal, "Equ")
            .put(NotEqual, "Neq")
            .put(Assign, "=")   // Could use "Assign"
            
            // The assign ops are rewriten to use assign: a += b ---> a = a + b
            .put(StarAssign, "Mul=")
            .put(DivAssign, "Div=")
            .put(ModAssign, "Mod=")
            .put(PlusAssign, "Add=")
            .put(MinusAssign, "Sub=")
            .put(LeftShiftAssign, "<<=")
            .put(RightShiftAssign, ">>=")
            .put(AndAssign, "And=")
            .put(XorAssign, "Xor=")
            .put(OrAssign, "Or=");
}

}

// Less : '<';
// LessEqual : '<=';
// Greater : '>';
// GreaterEqual : '>=';
// LeftShift : '<<';
// RightShift : '>>';
// 
// Plus : '+';
// PlusPlus : '++';        // not used
// Minus : '-';
// MinusMinus : '--';      // not used
// Star : '*';
// Div : '/';
// Mod : '%';
// 
// And : '&';
// Or : '|';
// AndAnd : '&&';          // not used
// OrOr : '||';            // not used
// Caret : '^';
// Not : '!';
// Tilde : '~';
// 
// Question : '?';
// Colon : ':';
// Semi : ';';
// Comma : ',';
// 
// Assign : '=';
// // '*=' | '/=' | '%=' | '+=' | '-=' | '<<=' | '>>=' | '&=' | '^=' | '|='
// StarAssign : '*=';
// DivAssign : '/=';
// ModAssign : '%=';
// PlusAssign : '+=';
// MinusAssign : '-=';
// LeftShiftAssign : '<<=';
// RightShiftAssign : '>>=';
// AndAssign : '&=';
// XorAssign : '^=';
// OrAssign : '|=';
// 
// Equal : '==';
// NotEqual : '!=';
