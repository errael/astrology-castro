/*
 * Copyright © 2024 by Ernie Rael. All rights reserved.
 */

package com.raelity.astrolog.castro.tables;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableMap;

import static com.raelity.astrolog.castro.tables.AstrologKeyCodes.Modifier.*;

/**
 * The goal is to have an Astrolog binary program that runs on any version,
 * and KeyCodes are particularly tricky.
 * 
 * On windows the keycodes are related to menu/action accelerators and they
 * change from release to release. For the castro function {@code KeyCode("w")}
 * there is a generated function
 * 
 *      macro KeyCode_dynamically_generate_w() {
 *          if(WIN()) { if(Version() == 7.70) 40084 else 40082; }
 *          else 0x77;
 *      }
 * 
 * And for {@code KeyCode("W")}, uppercase
 * 
 *      macro KeyCode_dynamically_generate_s_w() {
 *          if(WIN()) { if(Version() == 7.70) 40042 else 40041; }
 *          else 0x57;
 *      }
 * 
 * The non windows value is plain ascii, the windows value is generated
 * from tables.
 */
public abstract class AstrologKeyCodes
{
/** Track the characters that need generated code */
private static Set<Stroke> characters_used = new HashSet<>();
/** The various win key code maps */
private static AstrologKeyCodes keyCodes_770 = new AstrologKeyCodes_770();
private static AstrologKeyCodes keyCodes_760 = new AstrologKeyCodes_760();

/** Return true if the the char is OK to use, otherwise false.
 * If OK, generate the helper function for the value.
 */
public static boolean useChar(char c, Consumer<String> defineMacro)
{
    Stroke stroke = getStroke(c);
    if(stroke == null)
        return false;
    if(!characters_used.contains(stroke)) {
        characters_used.add(stroke);
        generateCode(stroke, defineMacro);
    }
    return true;
}

/**
 * Return the name of the castro macro that returns the key code for the
 * specified character. Currently only handle ascii 0-9,a-z,A-Z,{@literal <space>}.
 * 
 * @param c
 * @return 
 */
public static String keyCodeMacroName(char c)
{
    Stroke stroke = getStroke(c);
    if(stroke == null)
        return null;
    characters_used.add(stroke);
    return "KeyCode_dynamically_generate_" + keyCodeName(stroke);
}

private static final String macroKeyCode =
        """
        macro KeyCode_dynamically_generate_{name}() {
            if(WIN()) { if(Version() == 7.70) {key_code_770} else {key_code_760}; }
            else {ascii_code};
        }
        """;

/**
 * For all character codes requested generate the macros to produce the codes.
 * 
 * @param defineMacro Invoke this to add macros to the helper file.
 */
private static void generateCode(Stroke stroke, Consumer<String> defineMacro)
{
    String name = keyCodeName(stroke);
    String key_code_770 = String.valueOf(keyCodes_770.getCode(stroke));
    String key_code_760 = String.valueOf(keyCodes_760.getCode(stroke));
    char c = stroke.key().charAt(0);
    int ascii_int = stroke.modifiers.contains(SHIFT) ? Character.toUpperCase(c) : c;
    String ascii_code = String.format("0x%x", ascii_int);
    String code = macroKeyCode
            .replace("{name}", name)
            .replace("{key_code_770}", key_code_770)
            .replace("{key_code_760}", key_code_760)
            .replace("{ascii_code}", ascii_code);
    defineMacro.accept(code);
}

private static String keyCodeName(Stroke stroke)
{
    String name = charName.get(stroke.key.charAt(0));
    if(name == null)
        name = stroke.key();

    // NOTE: if charName then can't have shift.
    return (stroke.modifiers.contains(SHIFT) ? "s_" : "") + name;
}

/** Get a stroke, if it exists, for the given char.
 * It may not exist because there is no windows mapping.
 */
private static Stroke getStroke(char c)
{
    EnumSet<Modifier> mods = EnumSet.noneOf(Modifier.class);
    if('A' <= c && c <= 'Z') {
        mods.add(SHIFT);
        mods.add(VIRTKEY); // regular alphanum are all this kind
    } else if(' ' <= c && c <= '~') {
        // Not all keys exist on Astrolog's win port.
        Modifier tmod = keyType(c);
        if(tmod != null)
            mods.add(tmod);
    }
    if(mods.isEmpty())
        return null;
    return Stroke.get(String.valueOf(Character.toLowerCase(c)), mods);
}

/**
 * return ASCII or VIRTKEY; or null if no such.
 * There's an assumption that a key with no shft/ctrl/alt exists.
 */
private static Modifier keyType(char c)
{
    String ch = String.valueOf(Character.toLowerCase(c));
    Modifier mod = null;
    for(Modifier tmod : List.of(VIRTKEY, ASCII)) {
        Stroke stroke = Stroke.get(ch, EnumSet.of(tmod));
        if(keyCodes_770.codes.containsKey(stroke)
                && keyCodes_760.codes.containsKey(stroke)) {
            mod = tmod;
            break;
        }
    }
    return mod;
}

//////////////////////////////////////////////////////////////////////
//
// Instance
//

final Map<Stroke, Integer> codes;

@SuppressWarnings("OverridableMethodCallInConstructor")
AstrologKeyCodes()
{
    Map<Stroke, Integer> tmap = new HashMap<>();
    createEntries(tmap);
    codes = Collections.unmodifiableMap(tmap);
}

/** Win modifiers */
enum Modifier {
    ASCII, VIRTKEY,     // AFAICT, ASCII has no shft/ctrl/alt modifiers
    SHIFT, CONTROL, ALT;
}

record Stroke(String key,
              @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
              EnumSet<Modifier> modifiers)
{
    public static Stroke get(String key, EnumSet<Modifier> modifiers)
    {
        return new Stroke(key, modifiers);
    }
}

int getCode(Stroke stroke)
{
    return codes.get(stroke);
}

abstract void createEntries(Map<Stroke, Integer> codes);


private static final Map<Character, String> charName = 
        new ImmutableMap.Builder<Character, String>()
        .put(' ', "space")
        .put('!', "bang")
        .put('"', "double_quote")
        .put('#', "hash")
        .put('$', "dollar")
        .put('%', "percent")
        .put('&', "ampersand")
        .put('\'', "single_quote")
        .put('(', "left_paren")
        .put(')', "right_paren")
        .put('*', "splat")
        .put('+', "plush")
        .put(',', "comma")
        .put('-', "dash")
        .put('.', "dot")
        .put('/', "slash")
        .put(':', "colon")
        .put(';', "semi_colon")
        .put('<', "less_than")
        .put('=', "equal")
        .put('>', "greater_than")
        .put('?', "question")
        .put('@', "at")
        .put('[', "left_bracket")
        .put('\\', "back_slash")
        .put(']', "right_bracket")
        .put('^', "circumflex")
        .put('_', "under_bar")
        .put('`', "back_quote")
        .put('{', "left_brace")
        .put('|', "pipe")
        .put('}', "right_brace")
        .put('~', "tilde")
        .build();
}
