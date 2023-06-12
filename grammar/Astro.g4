
grammar Astro;

program
    : statement + EOF
    ;

statement
    : expr expr_semi[$expr.fBlock]
    ;

expr_semi[int fBlock]
    : {$fBlock == 0}? ';'
    | {$fBlock != 0}?
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

// TODO: make '*' indirection in expr

expr returns [int fBlock = 0]
@after {
    $fBlock = $stop.getType() == RightBrace ? 1 : 0;
}
    : func_call                         #exprFunc
    | ('!'|'~') expr                    #exprUnOp
    | expr ('*'|'/'|'%') expr               #exprBinOp
    | expr ('+'|'-') expr                   #exprBinOp
    | expr ('<<'|'>>') expr                 #exprBinOp
    | expr ('<'|'<='|'>'|'>=') expr         #exprBinOp
    | expr ('=='|'!=') expr                 #exprBinOp
    | expr ('&') expr                       #exprBinOp
    | expr ('^') expr                       #exprBinOp
    | expr ('|') expr                       #exprBinOp
    | <assoc=right> expr '?' expr ':' expr      #exprQuestOp
    | lval ('='|'+='|'-=') expr             #exprAssOp
    | term                                  #exprTermOp

    | brace_block                           #exprBraceBlockOp

    | 'if' paren_expr expr                          #exprIfOp
    | 'if' paren_expr expr 'else' expr              #exprIfElseOp
    | 'while' paren_expr expr                       #exprWhileOp
    | 'do' expr 'while' paren_expr                  #exprDowhileOp
    | 'for' '(' lval '=' expr ';' expr ')' expr     #exprForOp
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
