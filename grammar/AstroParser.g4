/* Copyright © 2023 Ernie Rael. All rights reserved */

parser grammar AstroParser;

options { tokenVocab=AstroLexer; }

// The restriction of layout before anything else is
// implemented in code.
program
    : (layout | var
            | switch | macro | assign_switch_addr | assign_macro_addr)+ EOF
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
    : var1 | varArray | varArrayInit
    ;

var1
    : 'var' Identifier ('@' addr=integer)? ('{' init=expr '}')? ';'
    ;

varArray
    : 'var' Identifier '[' size=integer ']' ('@' addr=integer)? ';'
    ;

/*
 * var p[3] @100 { 4, 3 }    // p is size 3, starts at 100, p[2] is not init.
 * var p[] { 4, 3 }          // p is size 2
 */
varArrayInit
    : 'var' Identifier '[' (size=integer)? ']' ('@' addr=integer)?
                                '{' init+=expr (',' init+=expr)* '}' ';'
    ;

assign_macro_addr : 'macro' id=Identifier '@' addr=integer ';' ;
assign_switch_addr : 'switch' id=Identifier '@' addr=integer ';' ;

macro
    : 'macro' id=Identifier ('@' addr=integer)? '{' s+=astroExprStatement + '}'
    ;

switch
    : 'switch' id=Identifier ('@' addr=integer)?
                START_VERBATIM (q+=Stuff | q+=CaptureString)* STOP_VERBATIM ;

/*
 * After top level '} allow optional ';' to avoid conusion.
 * "if(a){b;} *r = 2;" parses like "if(a){b;} * (r = 2);"
 * TODO: Give warning if line starts with '*', '+', '-':w
 */
astroExprStatement
    : astroExpr opt_semi[($astroExpr.stop.getType() == Semi) ? 1 : 0]
    ;

opt_semi[int fHasSemi]
    : {$fHasSemi == 0}? ';'
    |
    ;

astroExpr
    : expr expr_semi[$expr.fBlock]
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
    : func_name '(' (args+=expr (',' args+=expr)*)? ')'
    ;

func_name
    : id=Identifier | id=AssignObj | id=AssignHouse
    ;

// TODO: make '*' indirection in expr

expr returns [int fBlock = 0]
@after {
    $fBlock = $stop.getType() == RightBrace ? 1 : 0;
}
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
    | lval ('='|'+='|'-='|'*='|'/='|'%='|'<<='|'>>='|'&='|'^='|'|=') expr
                                                                #exprAssOp
    | term                                  #exprTermOp

    | brace_block                           #exprBraceBlockOp

    | 'if' paren_expr expr                          #exprIfOp
    | 'if' paren_expr expr 'else' expr              #exprIfElseOp
    | 'repeat' paren_expr expr                      #exprRepeatOp
    | 'while' paren_expr expr                       #exprWhileOp
    | 'do' expr 'while' paren_expr                  #exprDowhileOp
    | 'for' '(' lval '=' expr ';' expr ')' expr     #exprForOp
    ;

term
   : integer        #termSingle
   | paren_expr     #termParen
   | lval           #termSingle
   | '&' Identifier #termAddressOf
   ;

lval locals[Token id]
    : altid=Identifier '[' expr ']'   {$id = $altid;} #lvalArray
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

