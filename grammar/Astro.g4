/*
BSD License

Copyright (c) 2013, Tom Everett
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
3. Neither the name of Tom Everett nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

grammar Astro;

/*
    http://www.iro.umontreal.ca/~felipe/IFT2030-Automne2002/Complements/tinyc.c
*/
program
    : statement + EOF
    ;

statement
    : expr expr_semi[$expr.fBlock]
    ;

// TODO: Only have one "expr_block", it takes an arg "trailing_fix".
//       from statement/expr trailing_fix = 1, from expr zero.

expr_semi[int fBlock]
    : {$fBlock == 0}? ';'
    | {$fBlock != 0}?
    ;

trailing_expr_block
    : expr {if($expr.fBlock != 0) $expr::fBlock = 1; else $expr::fBlock = 0;}
    ;

expr_block
    : expr expr_semi[$expr.fBlock]
    ;

brace_block
    : '{' (bs+=expr expr_semi[$expr.fBlock])+ '}'
    ;

paren_expr
   : '(' expr ')'
   ;

func_call
    : Identifier '(' (args+=expr (',' args+=expr)*)? ')'
    ;

// TODO: Handle the trailing issue in code. ************************
// TODO: make '*' indirection in expr

expr returns [int fBlock = 0]
    : func_call                     #exprFunc
    | ('!'|'~') expr                #exprUnOp
    | expr ('*'|'/'|'%') expr       #exprBinOp
    | expr ('+'|'-') expr           #exprBinOp
    | expr ('<<'|'>>') expr         #exprBinOp
    | expr ('<'|'<='|'>'|'>=') expr #exprBinOp
    | expr ('=='|'!=') expr         #exprBinOp
    | expr ('&') expr               #exprBinOp
    | expr ('^') expr               #exprBinOp
    | expr ('|') expr               #exprBinOp
    | <assoc=right> expr '?' expr ':' expr      #exprQuestOp
    | lval ('='|'+='|'-=') expr     #exprAssOp
    | term                          #exprTermOp

    | brace_block {$fBlock = 1;}    #exprBraceBlockOp

    | 'if' paren_expr trailing_expr_block                       #exprIfOp
    | 'if' paren_expr expr_block 'else' trailing_expr_block     #exprIfElseOp
    | 'while' paren_expr trailing_expr_block                    #exprWhileOp
    | 'do' expr_block 'while' paren_expr                        #exprDowhileOp
    | 'for' '(' lval '=' expr ';' expr ')' trailing_expr_block  #exprForOp
    ;

term
   : integer        #termSingle
   | paren_expr     #termParen
   | lval           #termSingle
   ;

lval
    : Identifier '[' expr ']'   #lvalArray
    | '@' Identifier            #lvalIndirect
    | Identifier                #lvalMem
    ;

integer : IntegerConstant ;

/*************************************************************
constant
    : integer
    | real
    | chars
    ;

integer : IntegerConstant ;
real : FloatingConstant ;
chars : CharacterConstant ;
**************************************************************/

/*************************************************************
constant
    :   IntegerConstant
    |   FloatingConstant
    |   EnumerationConstant
    |   CharacterConstant
    ;
**************************************************************/

Do : 'do';
Else : 'else';
For : 'for';
If : 'if';
While : 'while';

LeftParen : '(';
RightParen : ')';
LeftBracket : '[';
RightBracket : ']';
LeftBrace : '{';
RightBrace : '}';

Less : '<';
LessEqual : '<=';
Greater : '>';
GreaterEqual : '>=';
LeftShift : '<<';
RightShift : '>>';

Plus : '+';
Minus : '-';
Star : '*';
Div : '/';
Mod : '%';

And : '&';
Or : '|';
Caret : '^';
Not : '!';
Tilde : '~';

Question : '?';
Colon : ':';
Semi : ';';
Comma : ',';

Assign : '=';
// '*=' | '/=' | '%=' | '+=' | '-=' | '<<=' | '>>=' | '&=' | '^=' | '|='
StarAssign : '*=';
DivAssign : '/=';
ModAssign : '%=';
PlusAssign : '+=';
MinusAssign : '-=';
LeftShiftAssign : '<<=';
RightShiftAssign : '>>=';
AndAssign : '&=';
XorAssign : '^=';
OrAssign : '|=';

Equal : '==';
NotEqual : '!=';

//STRING
//   : [a-z]+
//   ;

Identifier
    :   IdentifierNondigit
        (   IdentifierNondigit
        |   Digit
        )*
    ;

fragment
IdentifierNondigit
    :   Nondigit
//    |   UniversalCharacterName
    //|   // other implementation-defined characters...
    ;

fragment
Nondigit
    :   [a-zA-Z_]
    ;

fragment
Digit
    :   [0-9]
    ;

//fragment
IntegerConstant
    :   DecimalConstant
//    :   DecimalConstant IntegerSuffix?
//    |   OctalConstant IntegerSuffix?
//    |   HexadecimalConstant IntegerSuffix?
//    |	BinaryConstant
    ;

fragment
BinaryConstant
	:	'0' [bB] [0-1]+
	;

fragment
DecimalConstant
    :   NonzeroDigit Digit*
    ;

fragment
OctalConstant
    :   '0' OctalDigit*
    ;

fragment
HexadecimalConstant
    :   HexadecimalPrefix HexadecimalDigit+
    ;

fragment
HexadecimalPrefix
    :   '0' [xX]
    ;

fragment
NonzeroDigit
    :   [1-9]
    ;

fragment
OctalDigit
    :   [0-7]
    ;

fragment
HexadecimalDigit
    :   [0-9a-fA-F]
    ;

//fragment
//IntegerSuffix
//    :   UnsignedSuffix LongSuffix?
//    |   UnsignedSuffix LongLongSuffix
//    |   LongSuffix UnsignedSuffix?
//    |   LongLongSuffix UnsignedSuffix?
//    ;

//fragment
FloatingConstant
    :   DecimalFloatingConstant
    |   HexadecimalFloatingConstant
    ;

fragment
DecimalFloatingConstant
    :   FractionalConstant ExponentPart? FloatingSuffix?
    |   DigitSequence ExponentPart FloatingSuffix?
    ;

fragment
HexadecimalFloatingConstant
    :   HexadecimalPrefix (HexadecimalFractionalConstant | HexadecimalDigitSequence) BinaryExponentPart FloatingSuffix?
    ;

fragment
FractionalConstant
    :   DigitSequence? '.' DigitSequence
    |   DigitSequence '.'
    ;

fragment
ExponentPart
    :   [eE] Sign? DigitSequence
    ;

fragment
Sign
    :   [+-]
    ;

DigitSequence
    :   Digit+
    ;

fragment
HexadecimalFractionalConstant
    :   HexadecimalDigitSequence? '.' HexadecimalDigitSequence
    |   HexadecimalDigitSequence '.'
    ;

fragment
BinaryExponentPart
    :   [pP] Sign? DigitSequence
    ;

fragment
HexadecimalDigitSequence
    :   HexadecimalDigit+
    ;

fragment
FloatingSuffix
    :
//    :   [flFL]
    ;

//fragment
CharacterConstant
    :   '\'' CCharSequence '\''
//    |   'L\'' CCharSequence '\''
//    |   'u\'' CCharSequence '\''
//    |   'U\'' CCharSequence '\''
    ;

fragment
CCharSequence
    :   CChar+
    ;

fragment
CChar
    :   ~['\\\r\n]
//    |   EscapeSequence
    ;

//fragment
//EscapeSequence
//    :   SimpleEscapeSequence
//    |   OctalEscapeSequence
//    |   HexadecimalEscapeSequence
//    |   UniversalCharacterName
//    ;



//INT
//   : [0-9] +
//   ;

WS
   : [ \r\n\t] -> skip
   ;

BlockComment
    :   '/*' .*? '*/'
        -> skip
    ;

LineComment
    :   '//' ~[\r\n]*
        -> skip
    ;

//BLOCK_COMMENT
//	: '/*' .*? '*/' -> channel(HIDDEN)
//	;
//
//LINE_COMMENT
//	: '//' ~[\r\n]* -> channel(HIDDEN)
//	;
