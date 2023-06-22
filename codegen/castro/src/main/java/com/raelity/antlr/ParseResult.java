/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.antlr;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * This class contains the setup information for a active parse
 * using the AstroParser. It includes the parser, input, parser result.
 */
// TODO: this could be
public class ParseResult<T extends Parser, U extends Lexer>
{
final T parser;
final U lexer;
final CharStream input;
final ParserRuleContext context;

/**
 * 
 * @param parser
 * @param lexer
 * @param input
 * @param context 
 */
public ParseResult(T parser, U lexer, CharStream input,
                                           ParserRuleContext context)
{
    this.parser = parser;
    this.lexer = lexer;
    this.input = input;
    this.context = context;
}

public T getParser()
{
    return parser;
}

public U getLexer()
{
    return lexer;
}

public CharStream getInput()
{
    return input;
}

public ParserRuleContext getContext()
{
    return context;
}




}
