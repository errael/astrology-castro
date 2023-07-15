/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.antlr;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * This class contains the setup information for a parser.
 * It includes the parser, input, parser result.
 * Note: once the context/parse-result is set to non null, it can not be changed.
 */
public class ParseResult<T extends Parser, U extends Lexer>
{
private final T parser;
private final U lexer;
private final CharStream input;
private ParserRuleContext context;

/**
 * 
 * @param parser
 * @param lexer
 * @param input
 * @param context 
 */
public ParseResult(T parser, U lexer, CharStream input, ParserRuleContext context)
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

public void setContext(ParserRuleContext context)
{
    if(this.context != null)
        throw new IllegalStateException("context already set");
    this.context = context;
}

}
