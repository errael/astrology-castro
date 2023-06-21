/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro.tables;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.raelity.astrolog.castro.antlr.AstroLexer.*;

/**
 *
 * @author err
 */
public class Ops
{
private Ops(){}

/** Map token number to AstroExpression operator function name */
private static Map<Integer, String> ops = null;

/** @return AstroExpression function name corresponding to token as operator */
public static String binFunc(int token)
{
    if(ops == null) {
        // ~30 items, 40 entries, load-factor .75
        ops = new HashMap<>(45);
        createEntries();
        ops = Collections.unmodifiableMap(ops);
    }
    return ops.get(token);
}

private static void createEntries()
{
    ops.put(Less, "Lt");
    ops.put(LessEqual, "Lte");
    ops.put(Greater, "Gt");
    ops.put(GreaterEqual, "Gte");
    ops.put(LeftShift, "<<");
    ops.put(RightShift, ">>");
    ops.put(Plus, "Add");
    //ops.put(PlusPlus, "");    // not used
    ops.put(Minus, "Sub");
    //ops.put(MinusMinus, "");       // not used
    ops.put(Star, "Mul");
    ops.put(Div, "Div");
    ops.put(Mod, "Mod");
    ops.put(And, "And");
    ops.put(Or, "Or");
    //ops.put(AndAnd, "");       // not used
    //ops.put(OrOr, "");     // not used
    ops.put(Caret, "Xor");
    ops.put(Not, "Not");
    ops.put(Tilde, "Inv");
    //ops.put(Question, "");
    //ops.put(Colon, "");
    //ops.put(Semi, "");
    //ops.put(Comma, "");
    ops.put(Equal, "Equ");
    ops.put(NotEqual, "Neq");
    ops.put(Assign, "=");   // Could use "Assign"
    // The assign ops are rewriten to use assign: a += b ---> a = a + b
    ops.put(StarAssign, "Mul");
    ops.put(DivAssign, "Div");
    ops.put(ModAssign, "Mod");
    ops.put(PlusAssign, "Add");
    ops.put(MinusAssign, "Sub");
    ops.put(LeftShiftAssign, "<<");
    ops.put(RightShiftAssign, ">>");
    ops.put(AndAssign, "And");
    ops.put(XorAssign, "Xor");
    ops.put(OrAssign, "Or");
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
