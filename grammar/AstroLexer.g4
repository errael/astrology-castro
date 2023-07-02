/* Copyright © 2023 Ernie Rael. All rights reserved */

lexer grammar AstroLexer;

// TODO: use AssignObj/AssignHouse instead of "=" ?
// Handle these as functions
AssignObj : '=' [Oo] [Bb] [Jj] ~[a-zA-Z_0-9] ;
AssignHouse : '=' [Hh] [Oo] [Uu] [Ss] [Ee] ~[a-zA-Z_0-9] ;

SW_ARG : '{~' ;

Layout : 'layout';
Memory : 'memory';
Switch : 'switch';
Base : 'base';
Stack : 'stack';
Limit : 'limit';
Reserve : 'reserve';

Var : 'var';
Macro : 'macro';

Do : 'do';
Else : 'else';
For : 'for';
If : 'if';
While : 'while';
Repeat : 'repeat';

At : '@';

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
PlusPlus : '++';        // not used
Minus : '-';
MinusMinus : '--';      // not used
Star : '*';
Div : '/';
Mod : '%';

And : '&';
Or : '|';
AndAnd : '&&';          // not used
OrOr : '||';            // not used
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

Arrow : '->';           // not used
Dot : '.';              // not used
Ellipsis : '...';       // not used

UnderBar : '_' ;

String : DoubleQuoteString | SingleQuoteString ;

fragment
DoubleQuoteString
    : '"' .*? '"'
    ;

fragment
SingleQuoteString
    : '\'' .*? '\''
    ;

//STRING
//   : [a-z]+
//   ;

Identifier
    :   IdentifierNondigit
        (   IdentifierNondigit
        |   Digit
        )*
    ;

// see IntegerConstant below
IntegerConstant
    //:   DecimalConstant
    :   Digit+
    ;

IdentifierDigitNondigit
    :   Digit
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
//IntegerConstant
//    :   DecimalConstant
//    :   DecimalConstant IntegerSuffix?
//    |   OctalConstant IntegerSuffix?
//    |   HexadecimalConstant IntegerSuffix?
//    |	BinaryConstant
//    ;

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

