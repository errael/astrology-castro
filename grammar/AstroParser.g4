/* Copyright © 2023 Ernie Rael. All rights reserved */

parser grammar AstroParser;

options { tokenVocab=AstroLexer; }

// The restriction of layout before anything else is
// implemented in code.
program
    : (layout | var | copy | run
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
    : 'base' integer                        #baseContstraint
    // | 'stack' integer                    #stackContstraint
    | 'limit' integer                       #limitContstraint
    | 'reserve' rsv_loc (',' rsv_loc)*      #reserveContstraint
    ;

rsv_loc
    : range+=integer
    | range+=integer ':' range+=integer
    ;

// TODO: also string array initialization

var
    : var1 | varArray 
    ;

/* var pp;
 * var pp @101;
 * var pp { 4 };
 */
var1
    : 'var' id=Identifier ('@' addr=integer)? ('{' init=expr '}')? ';'
    ;

/*
 * var pp[3];
 * var pp[3] @100 { 4, 3 }    // pp is size 3, starts at 100, pp[2] is not init.
 * var pp[] { 4, 3 }          // pp is size 2
 */
varArray
    : 'var' id=Identifier '[' (size=integer)? ']' ('@' addr=integer)?
                                ('{' init+=expr (',' init+=expr)* '}')? ';'
    ;

/** copy stuff verbatim to output */
copy : COPY_START COPY_STUFF COPY_STOP ;

// following like "extern"
assign_macro_addr : 'macro' id=Identifier '@' addr=integer ';' ;
assign_switch_addr : 'switch' id=Identifier '@' addr=integer ';' ;

macro
    : 'macro' id=Identifier ('@' addr=integer)? '{' bs+=astroExprStatement + '}'
    ;

// Define/declare a "switch" command
// which is a squence of astrolog command switches.
// Note that "~M <index> 'expression'" and "~2[0] <index> 'vals'"
// are not supported. Use macro definition and string initializes.
// Can always do copy { ... } to manually generate these.

switch
    : 'switch' id=Identifier ('@' addr=integer)? '{' sc+=switch_cmd+ '}'
    ;

/** top level switch commands (not part of switch/macro). */
run
    : 'run' '{' sc+=switch_cmd+ '}'
    ;

switch_cmd
    : expr_arg=SW_ARG bs+=astroExprStatement + '}'   // express arg to regular switch
    | name=sw_name '{' bs+=astroExprStatement + '}'
    | name=sw_name
    | string=String
    //| assign=AssignString '(' l=lval ',' str+=String (',' str+=String)* ')'
    | assign=AssignString l=lval  str+=String + 
    ;

// No blanks in sw_name, check-report in compiler code.
// "-YYT" (not "- YYT"), "-80" (not "- 80"), "~FA" (not "~ FA"), ...

sw_name
    : pre=('-' | '=' | '_' )?
            ( id=IdentifierDigitNondigit |  id=Identifier | id=IntegerConstant)
    | pre=('-' | '=' | '_' )? tilde='~'
            (id=IdentifierDigitNondigit |  id=Identifier | id=IntegerConstant)
    | tilde='~'
    ;

/*
 * After top level '} allow optional ';' to avoid conusion.
 * "if(a){b;} *r = 2;" parses like "if(a){b;} * (r = 2);"
 * TODO: Give warning if line starts with '*', '+', '-'
 */
astroExprStatement
    : astroExpr opt_semi[($astroExpr.stop.getType() == Semi) ? 1 : 0]
    ;

opt_semi[int fHasSemi]
    : {$fHasSemi == 0}? ';'
    |
    ;

astroExpr
    //: expr expr_semi[$expr.fBlock]
    : expr expr_semi[$expr.stop.getType() == RightBrace ? 1 : 0]
    ;

expr_semi[int fBlock]
    : {$fBlock == 0}? ';'
    | {$fBlock != 0}?
    ;

brace_block
    : '{' bs+=astroExprStatement + '}'
    ;

paren_expr
   : '(' expr ')'
   ;

func_call
    : id=Identifier '(' (args+=expr (',' args+=expr)*)? ')'
    // Note no '(' in following rule, it's embedded in TrickyFunc
    | id=TrickyFunc (args+=expr (',' args+=expr)*)? ')'
    ;

// TODO: make '*' indirection in expr

expr returns [int fBlock = 0]
//@after {
//    $fBlock = $stop.getType() == RightBrace ? 1 : 0;
//}
    : func_call                         #exprFunc
    | ('+'|'-'|'!'|'~') expr            #exprUnOp
    | expr ('*'|'/'|'%') expr               #exprBinOp
    | expr ('+'|'-') expr                   #exprBinOp
    | expr ('<<'|'>>') expr                 #exprBinOp
    | expr ('<'|'<='|'>'|'>=') expr         #exprBinOp
    | expr ('=='|'!=') expr                 #exprBinOp
    | expr ('&') expr                       #exprBinOp
    | expr ('^') expr                       #exprBinOp
    | expr ('|') expr                       #exprBinOp
    | <assoc=right> expr '?' expr ':' expr      #exprQuestOp
    | l=lval ao=('='|'+='|'-='|'*='|'/='|'%='|'<<='|'>>='|'&='|'^='|'|=')
                                                        e=expr #exprAssOp
    | term                                  #exprTermOp

    | brace_block                           #exprBraceBlockOp

    | 'if' paren_expr expr                          #exprIfOp
    | 'if' paren_expr expr 'else' expr              #exprIfElseOp
    | 'repeat' paren_expr expr                      #exprRepeatOp
    | 'while' paren_expr expr                       #exprWhileOp
    | 'do' expr 'while' paren_expr                  #exprDowhileOp
    | 'for' '(' l=lval '=' expr ';' expr ')' expr   #exprForOp
    ;

term
   : integer            #termSingle
   | paren_expr         #termParen
   | lval               #termSingle
   | '&' id=Identifier  #termAddressOf
   ;

lval locals[Token id]
    : altid=Identifier '[' idx=expr ']'   {$id = $altid;} #lvalArray
    | '*' altid=Identifier            {$id = $altid;} #lvalIndirect
    | altid=Identifier                {$id = $altid;} #lvalMem
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

