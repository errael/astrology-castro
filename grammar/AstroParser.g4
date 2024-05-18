/* Copyright © 2023 Ernie Rael. All rights reserved */

parser grammar AstroParser;

options { tokenVocab=AstroLexer; }

// The restriction of layout before anything else is
// implemented in code.
program
    : (layout | const | var | copy | run
            | switch | macro | assign_switch_addr | assign_macro_addr)+
        EOF
    ;

layout
    : 'layout' layout_region '{' (constraint ';')+ '}'
    ;

layout_region
    : 'memory'
    | 'macro'
    | 'switch'
    ;

constraint
    : 'base' i=expr                      #baseConstraint
    // | 'stack' i=expr                  #stackConstraint
    | 'limit' i=expr                     #limitConstraint
    | 'reserve' rsv_loc (',' rsv_loc)*      #reserveConstraint
    ;

rsv_loc
    : range+=expr
    | range+=expr ':' range+=expr
    ;

const
    : 'const' id=Identifier '{' e=expr '}' ';'
    //: 'const' id=Identifier (e=expr | s=String) ';'
    ;

var
    : v=varDef
    ;

/*
 * Handle var definition
 *      optionally array, optionally assign address,
 *      optionally initialize, optionally strings.
 *
 * var pp;
 * var pp @101;
 * var pp { 4 };
 *
 * var pp[3];
 * var pp[3] @100 { 4, 3 }    // pp is size 3, starts at 100, pp[2] is not init.
 * var pp[] { 4, 3 }          // pp is size 2
 *
 * Allow trailing comma on initializer list
 */
varDef
    : 'var' id=Identifier (arr='[' (size=expr)? ']')? ('@' addr=expr)?
                    ( '{' init+=str_expr (',' init+=str_expr)* ','? '}' )? ';'
    ;

str_expr
    : e=expr
    | s=String
    ;

/** copy stuff verbatim to output */
copy : COPY_START COPY_STUFF COPY_STOP ;

// following like "extern"
assign_macro_addr : 'macro' id=Identifier '@' addr=expr ';' ;
assign_switch_addr : 'switch' id=Identifier '@' addr=expr ';' ;

macro
    : 'macro' id=Identifier ('@' addr=expr)?
            ( has_paren='(' (args+=Identifier (',' args+=Identifier)*)? ')' )?
            '{' bs+=astroExprStatement + '}'
    ;

/*
 * Define/declare a "switch" command
 * which is a squence of astrolog command switches.
 * Note that "~M <index> 'expression'" and "~2[0] <index> 'vals'"
 * are not supported. Use macro definition and string initializes.
 * Can always do copy { ... } to manually generate these.
 */

switch
    : 'switch' id=Identifier ('@' addr=expr)? '{' sc+=switch_cmd+ '}'
    ;

/** top level switch commands (not part of switch/macro). */
run
    : 'run' '{' sc+=switch_cmd+ '}'
    ;

switch_cmd
    : expr_arg=SW_ARG bs+=astroExprStatement + '}'   // express arg to regular switch
    | name=sw_name b='{' bs+=astroExprStatement + '}'
    | name=sw_name
    | string=String
    | assign=AssignString l=lval  str+=String + 
    ;

// No blanks in sw_name, check-report in codegen.
// "-YYT" (not "- YYT"), "-80" (not "- 80"), "~FA" (not "~ FA"), ...

sw_name
    : pre=('-' | '=' | '_' )?
            ( id=IdentifierDigitNondigit |  id=Identifier
                | id=IntegerConstant | id=BinaryConstant
                | id=HexadecimalConstant | id=OctalConstant
                | id=DecimalFloatingConstant )
    | pre=('-' | '=' | '_' )? tilde='~'
            (id=IdentifierDigitNondigit |  id=Identifier | id=IntegerConstant
                | id=IntegerConstant | id=BinaryConstant
                | id=HexadecimalConstant | id=OctalConstant
                | id=DecimalFloatingConstant )
    | tilde='~'
    ;

/*
 * After top level '} allow optional ';' to avoid conusion.
 * "if(a){b;} *r = 2;" parses like "if(a){b;} * (r = 2);"
 * TODO: Give warning if line starts with '*', '+', '-'
 */
astroExprStatement
    : e=astroExpr opt_semi[($astroExpr.stop.getType() == Semi) ? 1 : 0]
    ;

opt_semi[int fHasSemi]
    : {$fHasSemi == 0}? ';'
    |
    ;

astroExpr
    //: expr expr_semi[$expr.fBlock]
    : e=expr expr_semi[$expr.stop.getType() == RightBrace ? 1 : 0]
    ;

expr_semi[int fBlock]
    : {$fBlock == 0}? ';'
    | {$fBlock != 0}?
    ;

brace_block
    : '{' bs+=astroExprStatement + '}'
    ;

paren_expr
   : '(' e=expr ')'
   ;

// args is for all arguments are expr
// margs, mixed args, is for at least one arguments is a string.

func_call
    : id=Identifier '(' (args+=expr (',' args+=expr)*)? ')'
    | id=Identifier '(' (sargs+=str_expr (',' sargs+=str_expr)*)? ')'
    // Note no '(' in following rule, it's embedded in TrickyFunc
    | id=TrickyFunc (args+=expr (',' args+=expr)*)? ')'
    ;

// TODO: make '*' indirection in expr

// NOTE: it doesn't look the <assoc=right> for assOp does anything
//       might be because of the way the rule is written;
//       the rule is "lval assOp expr".

expr returns [int fBlock = 0]
//@after {
//    $fBlock = $stop.getType() == RightBrace ? 1 : 0;
//}
    : fc=func_call                      #exprFunc
    | o=('+'|'-'|'!'|'~') e=expr          #exprUnOp
    | l=expr o=('*'|'/'|'%') r=expr               #exprBinOp
    | l=expr o=('+'|'-') r=expr                   #exprBinOp
    | l=expr o=('<<'|'>>') r=expr                 #exprBinOp
    | l=expr o=('<'|'<='|'>'|'>=') r=expr         #exprBinOp
    | l=expr o=('=='|'!=') r=expr                 #exprBinOp
    | l=expr o='&' r=expr                         #exprBinOp
    | l=expr o='^' r=expr                         #exprBinOp
    | l=expr o='|' r=expr                         #exprBinOp
    | l=expr o='&&' r=expr                        #exprBinOp
    | l=expr o='||' r=expr                        #exprBinOp
    | <assoc=right> ec=expr '?' et=expr ':' ef=expr     #exprQuestOp
    | <assoc=right>
        l=lval ao=( '=' | '+=' | '-=' | '*=' | '/=' | '%='
                            | '<<=' | '>>=' | '&=' | '^=' | '|=' )
                    e=expr                          #exprAssOp

    | t=term                                        #exprTermOp
    | bb=brace_block                                   #exprBraceBlockOp

    | 'if' p=paren_expr e=expr                              #exprIfOp
    | 'if' p=paren_expr et=expr 'else' ef=expr              #exprIfElseOp
    | 'repeat' p=paren_expr e=expr                          #exprRepeatOp
    | 'while' p=paren_expr e=expr                           #exprWhileOp
    | 'do' e=expr 'while' p=paren_expr                      #exprDowhileOp
    | 'for' '(' l=lval '=' low=expr ';' up=expr ')' e=expr  #exprForOp
    ;

term
   : i=integer              #termSingle
   | f=float                #termSingle
   | p=paren_expr       #termParen
   | l=lval                 #termSingle
   | '&' lv=lval        #termAddressOf
   ;

lval locals[Token id]
    : lvid=Identifier '[' idx=expr ']'     {$id = $lvid;} #lvalArray
    | '*' lvid=Identifier                  {$id = $lvid;} #lvalIndirect
    | lvid=Identifier                      {$id = $lvid;} #lvalMem
    ;

integer : i=IntegerConstant | i=BinaryConstant | i=HexadecimalConstant | i=OctalConstant ;

float : f=DecimalFloatingConstant ;

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

