/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.astrolog.castro;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.xpath.XPath;

import com.raelity.astrolog.castro.Castro.CastroErr;
import com.raelity.astrolog.castro.Castro.CastroWarningOptions;
import com.raelity.astrolog.castro.antlr.AstroParser.ExprContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Func_callContext;
import com.raelity.astrolog.castro.antlr.AstroParser.IntegerContext;
import com.raelity.astrolog.castro.antlr.AstroParser.LvalMemContext;
import com.raelity.astrolog.castro.antlr.AstroParser.Switch_cmdContext;
import com.raelity.astrolog.castro.lib.CentralLookup;
import com.raelity.astrolog.castro.mems.AstroMem;
import com.raelity.astrolog.castro.mems.AstroMem.Var;
import com.raelity.astrolog.castro.tables.Functions;
import com.raelity.astrolog.castro.tables.Functions.Function;

import static com.raelity.antlr.ParseTreeUtil.getNthParent;
import static com.raelity.astrolog.castro.Error.OCTAL_CONST;
import static com.raelity.astrolog.castro.antlr.AstroParser.BinaryConstant;
import static com.raelity.astrolog.castro.antlr.AstroParser.HexadecimalConstant;
import static com.raelity.astrolog.castro.antlr.AstroParser.IntegerConstant;
import static com.raelity.astrolog.castro.antlr.AstroParser.OctalConstant;
import static com.raelity.astrolog.castro.mems.Macros.MEM_MACROS;
import static com.raelity.astrolog.castro.mems.Registers.MEM_REGISTERS;
import static com.raelity.astrolog.castro.mems.Switches.MEM_SWITCHES;
import static com.raelity.astrolog.castro.Error.INNER_QUOTE;

/**
 *
 */
public class Util
{
private Util() { }

private static final TreeProps<Boolean> didTreeError = new TreeProps<>();
private static final IdentityHashMap<Token,Boolean> didTokenError = new IdentityHashMap<>();

public static boolean didError(ParseTree node)
{
    return didTreeError.get(node) != null;
}

private static void setError(ParseTree node)
{
    didTreeError.put(node, Boolean.TRUE);
}

public static boolean didError(Token token)
{
    return didTokenError.get(token) != null;
}

private static void setError(Token token)
{
    didTokenError.put(token, Boolean.TRUE);
}


// Not a convenient way...
// public static LineMap buildLineMap(String fName)
// {
//     new WriteableLineMap(new ArrayList<>(100));
// 
//     //https://stackoverflow.com/questions/14791997/how-to-know-offset-of-a-begining-of-a-line-in-text-file-in-java
//     //offset += strline.getBytes(Charset.defaultCharset()).length + 1;
// 
//     return null;
// }

/**
 * Strip leading/trailing quote and remove Embedded quotes.
 * Remove Embedded quotes, report an error if one is found.
 */
// TODO: this is used from too many places.
public static String cleanString(Token token)
{
    String s1 = token.getText();
    String s2 = s1.replaceAll("[\"']", "");
    // There should be extactly two less characters in the String
    // for the leading/trailing quotes. If there are even less
    // then there are embedded quotes.
    if(s1.length() > s2.length() + 2)
        reportError(INNER_QUOTE, token, "'%s' string has inner quotes, stripping", s1);
    return s2;
}

public static boolean isBuiltinVar(Token id)
{
    return isBuiltinVar(id.getText());
}

public static boolean isBuiltinVar(String text)
{
    if (text.length() != 1)
        return false;
    char c = text.charAt(0);

    return c >= 'a' && c <= 'z';
}

record RS(int radix, String s){};
private static RS radixString(Token token)
{
    // strip off the first two chars if 0x or 0b
    String s = token.getText();
    return switch(token.getType()) {
    case IntegerConstant -> new RS(10, s);
    case BinaryConstant -> new RS(2, s.substring(2));
    case HexadecimalConstant -> new RS(16, s.substring(2));
    case OctalConstant -> new RS(8, s);
    default -> throw new IllegalArgumentException();
    };
}

public static int parseInt(Token token)
{
    RS rs = radixString(token);
    int i = Integer.parseInt(rs.s, rs.radix);
    if(rs.radix == 8 && i != 0)
        reportError(OCTAL_CONST, token, "'%s' Octal constant", token.getText());
    return i;
}

/** Extract the string(s) without sourounding quotes. */
public static List<String> collectAssignStrings(Switch_cmdContext sc_ctx)
{
    ArrayList<String> strings = sc_ctx.str.stream()
            .map((t) -> cleanString(t))
            .collect(Collectors.toCollection(ArrayList::new));
    return strings;
}

/** For addr output something like either "=x" or "= 100". */
public static StringBuilder writeRegister(StringBuilder sb, int addr)
{
    //String lvalName = ctx.id.getText();
    if(addr >= 1 && addr <= 26)
        sb.append('=').append((char)('a' + addr - 1));
    else
        sb.append("= ").append(addr);
    return sb;
}

public static Func_callContext lvalArg2Func(ParserRuleContext ctx)
{
    if(ctx instanceof LvalMemContext
            && (getNthParent(ctx, 3) instanceof Func_callContext fc_ctx))
        return fc_ctx;
    return null;
}

/**
 * If the lval is used in a function call as the name of a switch or macro,
 * return the memory space. The lval must be the sole argument.
 * <p>
 * TODO: honor a global on whether or not to return name or number.
 * @return address space of either switch or macro, else null
 */
public static AstroMem lval2MacoSwitchSpace(ParserRuleContext ctx)
{
    Func_callContext fc_ctx = lvalArg2Func(ctx);
    if(fc_ctx == null)
        return null;
    Function f = Functions.get(fc_ctx.id.getText());
    return f.targetMemSpace();
}

/** Must be single LvalMem arg in specified mem space. */
public static boolean isMacroSwitchFuncArgLval(Func_callContext ctx,
                                               AstroMem memSpace)
{
    if(ctx.args.size() != 1 || memSpace == null)
        return false;
    List<ParseTree> l = List.copyOf(expr2Lvals(ctx.args.get(0)));
    if(!l.isEmpty()
            && l.get(0) instanceof LvalMemContext
            && memSpace.getVar(ctx.args.get(0).getText()) != null) {
        return true;
    }
    // TODO: shouldn't hardcode user visible sting
    ExprContext ex = ctx.args.get(0);
    reportError(ex, "'%s' is not a defined %s", ex.getText(),
                memSpace.memSpaceName.equals(MEM_SWITCHES) ? "switch"
                : memSpace.memSpaceName.equals(MEM_MACROS) ? "macro"
                  : memSpace.memSpaceName.equals(MEM_REGISTERS) ? "var"
                    : "#unknownMemSpace#");
    return false;
}


/**
 * Check a switch/macro arg might be ok. Check is that an LvalMem
 * is a valid name in the expected space. A true return means it
 * is good so far.
 * @return false if LvalMem that is not a macro/switch name else true
 * 
 */
public static boolean macroSwitchFuncArgs(Func_callContext ctx, AstroMem memSpace)
{
    if(ctx.args.isEmpty())
        return false;
    List<ParseTree> l = List.copyOf(expr2Lvals(ctx.args.get(0)));
    if(memSpace != null
            && !l.isEmpty()
            && l.get(0) instanceof LvalMemContext
            && memSpace.getVar(ctx.args.get(0).getText()) == null) {
        ExprContext ex = ctx.args.get(0);
        reportError(ex, "'%s' is not a defined %s", ex.getText(),
                    memSpace.memSpaceName.equals(MEM_SWITCHES) ? "switch" : "macro");
        return false;
    }
    return true;
}

private static XPath xpathConstInt;
/** @return IntegerConstext if pt is expr that's an integer constant, else null */
public static Integer expr2constInt(ParseTree pt)
{
    if( xpathConstInt == null) {
        xpathConstInt = new XPath(lookup(AstroParseResult.class).getParser(),
                                  "/expr/term/integer");
    }
    Collection<ParseTree> constVal = xpathConstInt.evaluate(pt);
    if(constVal.isEmpty())
        return null;
    IntegerContext i_ctx = (IntegerContext)constVal.iterator().next();
    return parseInt(i_ctx.i);
}

private static XPath xpathFuncArgLval;
public static Collection<ParseTree> expr2Lvals(ParseTree pt)
{
    if(xpathFuncArgLval == null)
        xpathFuncArgLval = new XPath(lookup(AstroParseResult.class).getParser(),
                                     "/expr/term/lval");
    return xpathFuncArgLval.evaluate(pt);
}
public static boolean isLvalExpr(ParseTree pt)
{
    return !expr2Lvals(pt).isEmpty();
}



public static PrintWriter getErr()
{
    return lookup(CastroErr.class).pw();
}

//
// The use of ctx/token is distinct for keeping track of
// what has had an error reported.
//

public static void reportError(ParserRuleContext ctx, Object... msg)
{
    reportError(null, ctx, msg);
}

public static void reportError(Error err, ParserRuleContext ctx, Object... msg)
{
    if(didError(ctx))
        return;
    report(err, ctx.start, msg);
    setError(ctx);
}

public static void reportError(Token token, Object... msg)
{
    reportError(null, token, msg);
}

public static void reportError(Error err, Token token, Object... msg)
{
    if(didError(token))
        return;
    report(err, token, msg);
    setError(token);
}

private static void report(Error err, Token token, Object... msg)
{
    boolean fError = err == null ? true
                     : !lookup(CastroWarningOptions.class).warn().contains(err);
    String optMsg = msg.length == 0 ? "" : String.format(
            (String)msg[0], Arrays.copyOfRange(msg, 1, msg.length));
    String lineText = getLineText(token);
    if(!lineText.isEmpty())
        lineText = "'" + lineText + "' ";
    getErr().printf("%s %s %s%s\n", (fError ? "Error:" : "Warn:"),
                    tokenLoc(token), lineText, optMsg);
    if(fError)
        lookup(AstroParseResult.class).countError();
}

/** @return text encompassing token1:token2 */
public static String getText(Token token1, Token token2)
{
    // assuming both tokens in same stream
    try {
        return token1.getInputStream().getText(
                new Interval(token1.getStartIndex(), token2.getStopIndex()));
    } catch(Exception ex) {
        return ex.getMessage();
    }
}

public static String getLineText(Token token)
{
    if(token == null)
        return "NULL TOKEN";
    // TODO: check return is empty string, try a backup
    return getLineText(token.getLine(), token.getInputStream());
}

//public static String getLineText(int line)
//{
//    return getLineText(line, Castro.getInput());
//}

public static String getLineText(int line, CharStream cs)
{
    LineMap lm = lookup(LineMap.class);

    Interval interval = lm != null ? lm.getInterval(line) : null;
    return interval != null ? cs.getText(interval) : "";
}

/** @return "fname:line:charpos" */
// TODO: return record("fname:line:charpos", "originalline text")
public static String tokenLoc(ParserRuleContext ctx)
{
    return tokenLoc(ctx.start);
}
public static String tokenLoc(Token token)
{
    if(token == null)
        return "";
    String fName = token.getTokenSource().getSourceName();
    if(!fName.equals(IntStream.UNKNOWN_SOURCE_NAME)) {
        Path path = Path.of(fName).toAbsolutePath(); 
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        fName = cwd.relativize(path).toString();
    }
    return String.format("%s:%d:%d",
                         fName,
                         token.getLine(),
                         token.getCharPositionInLine());
}

public static void checkReport(Var var, Object... msg)
{
    if(!var.hasError())
        return;
    String optMsg = msg.length == 0 ? "" : String.format(
            (String)msg[0], Arrays.copyOfRange(msg, 1, msg.length));
    if(!var.getConflicts().isEmpty()) {
        StringBuilder sb = new StringBuilder(optMsg)
                .append(" conflicts with");
        for(Var conflict : var.getConflicts()) {
            sb.append(' ').append(tokenLoc(conflict.getId())).append(' ')
                    .append('\'').append(conflict.getName()).append('\'');
        }
        optMsg = sb.toString();
    }
    reportError(var.getId(), "'%s' %s %s", var.getName(), var.getState(), optMsg);
}

public static <T> T lookup(Class<T> clazz)
{
    return CentralLookup.getDefault().lookup(clazz);
}

public static <T> Collection<T> lookupAll(Class<T> clazz)
{
    return Collections.unmodifiableCollection(
            CentralLookup.getDefault().lookupAll(clazz));
}

public static void addLookup(Object instance)
{
    CentralLookup.getDefault().add(instance);
}

public static void removeLookup(Object instance)
{
    CentralLookup.getDefault().remove(instance);
}

public static void replaceLookup(Object instance)
{
    CentralLookup.getDefault().replace(instance);
}
}
