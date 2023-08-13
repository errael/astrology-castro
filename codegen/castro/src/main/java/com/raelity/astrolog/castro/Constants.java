/*
 * Copyright © 2023 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.Token;

import static com.raelity.astrolog.castro.Constants.ConstantFlag.*;
import static com.raelity.astrolog.castro.Error.CONST_AMBIG;
import static com.raelity.astrolog.castro.Util.reportError;
import static com.raelity.astrolog.castro.tables.AstrologConstants.*;

/**
 * Handle constants. There are Astrolog Builtin constants.
 * Some day might have a "define"/"const".
 * <p>
 * For now, do a quick check for possible constant.
 * Would like to capture known constants from Astrolog. 
 */
public class Constants
{

private static final int NFK = 12;
public static final int FK_F0_KEY_CODE = 200; 
public static final int FK_F0_SLOT = 0;
public static final int FK_FIRST_SLOT = FK_F0_SLOT + 1; 
public static final int FK_LAST_SLOT = FK_F0_SLOT + 4 * NFK; 

record NameVal(String n, int v){};
private static final NameVal[] castroConsts = new NameVal[] {
    new NameVal(  "FK_F0", FK_F0_SLOT + 0 * NFK),
    new NameVal("S_FK_F0", FK_F0_SLOT + 1 * NFK),
    new NameVal("C_FK_F0", FK_F0_SLOT + 2 * NFK),
    new NameVal("A_FK_F0", FK_F0_SLOT + 3 * NFK),

    new NameVal(  "FK_F0_KC", FK_F0_KEY_CODE + 0 * NFK),
    new NameVal("S_FK_F0_KC", FK_F0_KEY_CODE + 1 * NFK),
    new NameVal("C_FK_F0_KC", FK_F0_KEY_CODE + 2 * NFK),
    new NameVal("A_FK_F0_KC", FK_F0_KEY_CODE + 3 * NFK),
};

private static Constants INSTANCE;
private static Constants get()
{
    if(INSTANCE == null)
        INSTANCE = new Constants();
    return INSTANCE;
}

/** @return true if exact name match or might be astrolog constant. */
public static boolean isConstantName(Token token)
{
    return get().isConstantNameCheck(token, false);
}

/**
 * Find the full name of the specified constant, if not found
 * then return the specified name. If not found, and name is
 * not a possible constant, return null.
 * 
 * @return the name of a constant, null if not a constant.
 */
public static String constantName(Token token)
{
    if(!get().isConstantNameCheck(token, true))
        return null;
    Info info = get().findName(token);
    return info != null ? info.id : token.getText();
}

/**
 * For a builtin constant, return the name and let Astrolog handle it;
 * Otherwise return the value of the constant.
 * @return the value of the constant, null if no such.
 */
// TODO: should return "best case" value in all cases. (there's a pun in there)
public static String constant(Token token)
{
    Info match = get().findName(token);
    
    // TODO: deal with QUOTE_IT when there's a blank in the name
    return match == null ? null
           : match.sval;
           //: match.flags.contains(QUOTE_IT) ? "\"" + match.val + "\"" : match.val;
}

/** @return true if exact name match and value is known. */
public static Integer numericConstant(Token token)
{
    Info match = get().findName(token);
    return match == null ? null
           : match.flags.contains(NUMERIC) ? match.ival : null;
}

private static final String astrologPrefix = "moahskwz";
private static final String astrologPassthruPrefix = "kz";

private final NavigableMap<String, Info> constants = new TreeMap<>();
private final Set<String> exactConstants = new HashSet<>();

/** Available for short term use. */
private final StringBuilder sb = new StringBuilder();
/** Available for short term use. */
private EnumSet<ConstantFlag> tmpConstantFlagSet = EnumSet.noneOf(ConstantFlag.class);

private Constants() {
    if(Boolean.FALSE) { displayConstants(); System.exit(0); }
    initExactConstants();
    setupAstrologImport();
}

private void initExactConstants()
{
    addConstant("true", new Info("True", EnumSet.of(SRC_ASTROLOG, EXACT)));
    addConstant("false", new Info("False", EnumSet.of(SRC_ASTROLOG, EXACT)));
    addConstant("signs", new Info("Signs", EnumSet.of(SRC_ASTROLOG, EXACT)));

    /** the "base" for X function keys. Add 1 for F1, ... */
    for(NameVal cc : castroConsts) {
        addConstant(cc.n.toLowerCase(Locale.ROOT),
                    new Info(cc.n, cc.v, EnumSet.of(SRC_CASTRO, EXACT, NUMERIC)));
    }
    // castroConsts = null; not that much mem
}

private void addConstant(String name, Info info)
{
    constants.put(name, info);
    if(info.flags.contains(EXACT)) {
        if(!exactConstants.add(name)) {
            throw new RuntimeException("constant already defined");
        }
    }
}

private Info findName(Token token)
{
    String id = token.getText().toLowerCase(Locale.ROOT);
    boolean needsExact;
    // Short circuit if can't be a constant
    if(exactConstants.contains(id))
        needsExact = true;
    else if(hasAstrologPrefix(id)) {
        // An astrolog constant must have at leat 5 chars: 2(prefix) + 3(name)
        if(id.length() < 5) {
            repErr(token);
            return null;
        }
        if(astrologPassthruPrefix.indexOf(id.charAt(0)) >= 0)
            return new Info(token.getText(), EnumSet.of(NONE, QUOTE_IT));
        needsExact = false;
    } else {
        repErr(token);
        return null;
    }

    NavigableMap<String, Info> tail = constants.tailMap(id, true);
    if(needsExact) {
        if(!tail.isEmpty() && id.equals(tail.firstKey()))
            return tail.firstEntry().getValue();
        else
            return null;
    }

    List<Info> matches = new ArrayList<>();
    List<Info> matches_low_pri = new ArrayList<>();
    for(Entry<String, Info> entry : tail.entrySet()) {
        String key = entry.getKey();
        Info info = entry.getValue();
        if(!key.startsWith(id))
            break;
        (info.flags.contains(LOW_PRI) ? matches_low_pri : matches).add(info);
    }
    List<Info> winner = !matches.isEmpty() ? matches : matches_low_pri;
    if(winner.isEmpty()) {
        repErr(token);
        return null;
    }
    // When there's more than one match, take the first match.
    Info winfo = winner.get(0);
    if(winfo.flags.contains(EXACT) && !winfo.id.equalsIgnoreCase(id)) {
        repErr(token);
        return null;
    }
    repErr(token, winner); // only does something if more than one match
    return winfo;
}

private void repErr(Token token)
{
    reportError(token, "constant '%s' not found", token.getText());

}

private void repErr(Token token, List<Info> matches)
{
    if(matches.size() <= 1)
        return;
    reportError(CONST_AMBIG, token, "constant '%s' is ambiguous: %s",
                token.getText(),
                matches.stream().map((m) -> m.id).collect(Collectors.toList()));

}

/** Check if token is a constant.
 * 
 * @return true if exact match or looks like an astrolog constant.
 */
private boolean isConstantNameCheck(Token token, boolean reportNotFound)
{
    String id = token.getText().toLowerCase(Locale.ROOT);
    if(exactConstants.contains(id))
        return true;
    if(hasAstrologPrefix(id)) {
        if(reportNotFound && findName(token) == null)
            reportError(token, "'%s' is constant prefix, but '%s', not found",
                        id.substring(0, 2), id);
        return true;
    }
    return false;
}

private boolean hasAstrologPrefix(String id)
{
    return id.length() >= 2 && id.charAt(1) == '_'
            && astrologPrefix.indexOf(id.charAt(0)) >= 0;

}

static enum ConstantFlag {
    SRC_ASTROLOG,
    SRC_CASTRO,
    SRC_USER,
    QUOTE_IT,  // requires quote on output
    LOW_PRI, // Use this if no other match
    EXACT, // must be an exact match
    NUMERIC, // integer value available
    NONE,
    }

    private static class Info
    {
    private final String id;
    private final String sval;
    private final int ival;
    private final EnumSet<ConstantFlag> flags;

    /** For ASTROLOG, String val gets passed through. */
    private Info(String val, EnumSet<ConstantFlag> flags)
    {
        this.id = val;
        this.sval = val;
        this.flags = flags;
        this.ival = Integer.MIN_VALUE;
    }
    // Something with a numeric value
    private Info(String id, int ival, EnumSet<ConstantFlag> type)
    {
        this.id = id;
        this.ival = ival;
        this.sval = String.valueOf(ival);
        this.flags = type;
    }
    /** For CASTRO/USER, id is generated as val. */
    private Info(String id, String val, EnumSet<ConstantFlag> type)
    {
        this.id = id;
        this.sval = val;
        this.flags = type;
        this.ival = Integer.MIN_VALUE;
    }

    @Override
    public String toString()
    {
        return "Info{" + "id=" + id + ", val=" + sval + ", flags=" + flags +
                '}';
    }

    } /////// class Info

//
// Integrate the extracted constants from AstrologConstants
//
// TODO: put the data in a resource file. But since the String
//       is reused, wastage not that great.

// For the original name
// convert 'blanks', '-' and '.' to '_';
// other non-id chars, '(', ')', ',' removed.
// Consider: "Pullen (S.Ratio)"
// id: "Pullen_S_Ratio", val: is the original, must be quoted.
//

/** AstrologRawConstantInfo */
record CI(String prefix, String[] data, ConstantFlag flag){};

/** these are not in the lookup table */


// 287 entries, Aug 1, 2023
//System.err.printf("imported entries: %d\n", constants.size());
private void setupAstrologImport()
{
    // Not "K_", "Z_"
    List<CI> rawCI = List.of(
            new CI("S_", szSignName, NONE),  // array/array
            new CI("M_", szMonth, NONE),
            new CI("W_", szDay, NONE),
            new CI("O_", szObjName, NONE),
            new CI("O_", rgObjName, LOW_PRI),
            new CI("A_", szAspectAbbrev, NONE),
            new CI("A_", rgAspectName, LOW_PRI),
            new CI("H_", szSystem, NONE),
            new CI("H_", rgSystem, LOW_PRI)
    );
    for(CI ci : rawCI) {
        for(String name : ci.data) {
            if(name.isEmpty())
                continue;
            EnumSet<ConstantFlag> flags = EnumSet.of(SRC_ASTROLOG, ci.flag);
            String s = castroId(name, flags);
            constants.put((ci.prefix + s).toLowerCase(),
                           new Info(ci.prefix + s, ci.prefix + name, flags));
        }
    }
    astrologConstantAliases("A_", szAspectName, szAspectAbbrev);
}
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void astrologConstantAliases(String prefix, 
                                     String[] aliases, String[] names)
{
    if(aliases.length != names.length) {
        System.err.println("INTERNAL ERROR: astrologConstantAliases");
        System.exit(1);
    }
    for(int i = 0; i < aliases.length; i++) {
        String alias = aliases[i];
        String name = names[i];
        if(name.isEmpty())
            continue;
        EnumSet<ConstantFlag> flags = EnumSet.of(SRC_ASTROLOG, LOW_PRI);
        String s = castroId(alias, flags);
        constants.put((prefix + s).toLowerCase(),
                          new Info(prefix + s, prefix + name, flags));
    }
}

/**  If the name is modified, then require quoting on output */
private String castroId(String name, EnumSet<ConstantFlag> flags)
{
    sb.setLength(0);
    for(int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        // If there are more chars to skip...
        // Using if is probably more reliable, not as nice to look at.
        switch(c) {
            case '-', ' ', '.', ':' -> {
                flags.add(QUOTE_IT);
                sb.append('_');
            }
            case '(', ')', ',' -> {
                flags.add(QUOTE_IT);
                continue;
            }
            default -> sb.append(c);
        }
    }
    return sb.toString();
}

record SubGroup(String header, int n){};
private void displayConstants()
{
    displayConstantGroup("S_", "Signs", Arrays.asList(szSignName), null);
    displayConstantGroup("M_", "Months", Arrays.asList(szMonth), null);
    displayConstantGroup("W_", "Days", Arrays.asList(szDay), null);

    displayConstantGroup("A_", "Aspects", Arrays.asList(szAspectAbbrev), null);
    List<String> altAspects = new ArrayList<>();
    altAspects.addAll(Arrays.asList(szAspectName));
    altAspects.addAll(Arrays.asList(rgAspectName));
    displayConstantLowPri("Aspects", altAspects);

    displayConstantGroup("H_", "Houses", Arrays.asList(szSystem), null);
    displayConstantLowPri("Houses", Arrays.asList(rgSystem));

    displayConstantGroup("O_", "Objects", Arrays.asList(szObjName),
                         List.of(new SubGroup("Planets", 11),
                                 new SubGroup("Asteroids", 5),
                                 new SubGroup("Nodes", 2),
                                 new SubGroup("Others", 4),
                                 new SubGroup("Cusps", 12),
                                 new SubGroup("Uranians", 9),
                                 new SubGroup("Dwarfs", 9),
                                 new SubGroup("Moons", 32),
                                 new SubGroup("Stars", 50),
                                 new SubGroup("Extra", 4)

                         ));
    displayConstantLowPri("Objects", Arrays.asList(rgObjName));
}

@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void displayConstantGroup(String prefix, String tag, List<String>group,
                                  List<SubGroup> subgroups)
{
    System.err.printf("### %s - prefix: `%s`\n", tag, prefix);
    if(subgroups == null)
        displayConstantSubGroup(null, group);
    else {
        int idx = 0;
        for(SubGroup sg : subgroups) {
            displayConstantSubGroup(sg.header , group.subList(idx, idx+sg.n));
            idx += sg.n;
        }

    }
}

private void displayConstantLowPri(String tag, List<String>group)
{
    displayConstantSubGroup(tag + " - low priority match", group);
}


@SuppressWarnings("UseOfSystemOutOrSystemErr")
private void displayConstantSubGroup(String tag, List<String>group)
{
    int perLine = 6;
    if(tag != null)
        System.err.printf("#### %s\n", tag);

    for(int n = 0; n < perLine; n++)
        System.err.printf("|     ");
    System.err.printf("|\n");
    for(int n = 0; n < perLine; n++)
        System.err.printf("| --- ");
    System.err.printf("|\n");

    int i = 1;
    for(String item : group) {
        if(item.isEmpty())
            continue;
        //System.err.printf("%-13s ", item);
        System.err.printf("| %s ", castroId(item, tmpConstantFlagSet));
        if(i % perLine == 0)
            System.err.printf("|\n");
            //System.err.println("");
        i++;
    }
    System.err.println("");

}

}
