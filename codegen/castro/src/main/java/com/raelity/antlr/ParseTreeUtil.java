/* Copyright © 2023 Ernie Rael. All rights reserved */

package com.raelity.antlr;


import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Utils for traversing Parse Trees.
 */
public final class ParseTreeUtil
{
    private ParseTreeUtil() { }

    public static String getOriginalText(ParserRuleContext ctx, CharStream input)
    {
        int a = ctx.start.getStartIndex();
        int b = ctx.stop.getStopIndex();
        Interval interval = new Interval(a,b);
        return input.getText(interval);
    }

    public static String getSpacedText(ParserRuleContext ctx)
    {
        StringBuilder sb = new StringBuilder();
        List<ParseTree> nodes = getTerminalNodes(ctx);
        for(ParseTree node : nodes)
            sb.append(node).append(' ');
        if(sb.length() > 0)
            sb.setLength(sb.length() - 1); // get rid of trailing ' '
        return sb.toString();
    }

    public static List<ParseTree> getTerminalNodes(ParseTree pt)
    {
        assert pt != null;
        if(pt instanceof TerminalNode)
            return List.of(pt);
        List<ParseTree> ptl = new ArrayList<>();
        for(int i = 0; i < pt.getChildCount(); i++)
            ptl.addAll(getTerminalNodes(pt.getChild(i)));
        return ptl;
    }

    public static String getRuleName(Parser parser, ParseTree pt,
                                     boolean useBrackets)
    {
        String s;
        String name;
        if(pt instanceof RuleContext ctx)
            name = parser.getRuleNames()[ctx.getRuleIndex()];
        else
            name = "???";

        if(useBrackets)
            s = '[' + name + ']';
        else
            s = name;
        return s;
    }


    // ===================================================================
    // The following is from 
    //https://github.com/sleekbyte/tailor/blob/master/src/main/java/com/sleekbyte/tailor/utils/ParseTreeUtil.java
    // And subject to the MIT license.
    // Following from sleekbyte and subject to any res

    /**
     * Return parent `nval` levels above ctx.
     *
     * @param ctx Child node
     * @param nval 'n' value, number of levels to go up the tree
     * @return Parent node or null if parent does not exist
     */
    public static ParserRuleContext getNthParent(ParserRuleContext ctx, int nval) {
        if (ctx == null) {
            return null;
        }
        while (nval != 0) {
            nval--;
            ctx = ctx.getParent();
            if (ctx == null) {
                return null;
            }
        }
        return ctx;
    }

    /**
     * Returns node's index with in its parent's child array.
     *
     * @param node A child node
     * @return Node's index or -1 if node is null or doesn't have a parent
     */
    public static int getNodeIndex(ParseTree node) {
        if (node == null || node.getParent() == null) {
            return -1;
        }
        ParseTree parent = node.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == node) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gets left sibling of a parse tree node.
     *
     * @param ctx A node
     * @return Left sibling of a node, or null if no sibling is found
     */
    public static ParseTree getLeftSibling(ParseTree ctx) {
        int index = ParseTreeUtil.getNodeIndex(ctx);
        if (index < 1) {
            return null;
        }
        return ctx.getParent().getChild(index - 1);
    }

    /**
     * Gets right sibling of a parse tree node.
     *
     * @param ctx A node
     * @return Right sibling of a node, or null if no sibling is found
     */
    public static ParseTree getRightSibling(ParseTree ctx) {
        int index = ParseTreeUtil.getNodeIndex(ctx);
        ParseTree parent = ctx.getParent();
        if (index < 0 || index >= parent.getChildCount() - 1) {
            return null;
        }
        return parent.getChild(index + 1);
    }

    /**
     * Gets last child of a parse tree node.
     *
     * @param ctx A node
     * @return Last child of a node, or null if node has no children
     */
    public static ParseTree getLastChild(ParseTree ctx) {
        if (ctx.getChildCount() == 0) {
            return null;
        }
        return ctx.getChild(ctx.getChildCount() - 1);
    }

    /**
     * Return node situated on the left of the input node (does not have to be at the same level as the current node).
     *
     * @param ctx A node
     * @return The left node
     */
    public static ParseTree getLeftNode(ParseTree ctx) {
        while (true) {
            if (ctx == null) {
                return null;
            }
            ParseTree left = getLeftSibling(ctx);
            if (left != null) {
                return left;
            }
            ctx = ctx.getParent();
        }
    }

    /**
     * Return node situated on the right of the input node (does not have to be at the level as the current node).
     *
     * @param ctx A node
     * @return The right node
     */
    public static ParseTree getRightNode(ParseTree ctx) {
        while (true) {
            if (ctx == null) {
                return null;
            }
            ParseTree right = getRightSibling(ctx);
            if (right != null) {
                return right;
            }
            ctx = ctx.getParent();
        }
    }

    /**
     * Returns the starting token of the construct represented by node.
     *
     * @param node A node
     * @return Start token
     */
    public static Token getStartTokenForNode(ParseTree node) {
        assert node != null;
        if (node instanceof TerminalNode terminalNode) {
            return terminalNode.getSymbol();
        } else {
            return ((ParserRuleContext) node).getStart();
        }
    }

    /**
     * Returns the last token of the construct represented by node.
     *
     * @param node A node
     * @return Stop token
     */
    public static Token getStopTokenForNode(ParseTree node) {
        assert node != null;
        if (node instanceof TerminalNode terminalNode) {
            return terminalNode.getSymbol();
        } else {
            return ((ParserRuleContext) node).getStop();
        }
    }
}
