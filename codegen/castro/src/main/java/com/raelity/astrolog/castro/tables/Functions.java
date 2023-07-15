/* Copyright © 2023 Ernie Rael. All rights reserved */

// This file is generated by a script: scipts/extract_functions
// from astrolog's express.cpp which is copyrighted by:
// Walter D. Pullen (Astara@msn.com, http://www.astrolog.org/astrolog.htm)

package com.raelity.astrolog.castro.tables;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Functions
{

/** @return number of arguments for func; null if func does not exist */
public static Integer narg(String funcName)
{
    Info info = functions.funcs.get(funcName.toLowerCase(Locale.ROOT));
    return info != null ? info.narg : null;
}

public static String name(String funcName)
{
    Info info = functions.funcs.get(funcName.toLowerCase(Locale.ROOT));
    return info != null ? info.name : null;
}

/** Convert castro function name to AstroExpression name.
 * This is only used in cases where a name can be replaced without requiring
 * a code rewrite.
 * @return the astro name; if no replacement return the input funcName.
 */
public static String translate(String funcName)
{
    return functions.replaceFuncs.getOrDefault(funcName, name(funcName));
}

// Would like a read only unioun of regular maps and unknown maps.
// https://stackoverflow.com/questions/66428518/java-read-only-view-of-the-union-of-two-maps;
// But, unknown only needs to be a set; since unkown rest of info is derived.

/** Track an unknown function; further inquiries about this
 * name will not return null and narg returns 0. 
 * Keep a set of unknown function names. And add the unknown
 * to the main map to keep the rest of the code simple.
 */
public static void recordUnknownFunction(String funcName)
{
    String lc = funcName.toLowerCase(Locale.ROOT);
    if(functions.funcs.containsKey(lc))
        throw new IllegalArgumentException(funcName);
    functions.unknownFuncs.add(lc);
    functions.add(lc, 0, "R_");
}

public static boolean isUnkownFunction(String funcName)
{
    return functions.unknownFuncs.contains(funcName.toLowerCase(Locale.ROOT));
}

// singleton
private static final Functions functions = new Functions();

private final Map<String, Info> modifiableFuncs;
private final Map<String, Info> funcs;
private final Map<String,String> replaceFuncs;
private final Set<String> unknownFuncs;

private Functions() {
    // ~500 items, 700 entries, load-factor .72
    // keep modifiable funcs around to add unknown func.
    this.modifiableFuncs = new HashMap<>(700);
    this.funcs = Collections.unmodifiableMap(modifiableFuncs);
    this.unknownFuncs = new HashSet<>();
    this.replaceFuncs = new HashMap<>();
    createEntries();
    // Provide "evaluate both sides" semantics for "?:" if wanted.
    addWithReplacement("QuestColon", "?:", 3, "E_IEE");
    addWithReplacement("AssignObj", "=Obj", 4, "R_IIII");
    addWithReplacement("AssignHou", "=Hou", 4, "R_IIII");
}

private void addWithReplacement(String castroName, String astroName, int narg, String types)
{
    add(castroName, narg, types);
    replaceFuncs.put(castroName, astroName);
}

/** name is the "documented" case */
record Info(String name, int narg){};

/** key is lower case. Save original name and nargs. */
private void add(String funcName, int narg, String types)
{
    Objects.nonNull(types);
    modifiableFuncs.put(funcName.toLowerCase(Locale.ROOT), new Info(funcName, narg));
}

private void createEntries()
{
    add("False", 0, "I_");
    add("True", 0, "I_");
    add("Int", 1, "I_I");
    add("Real", 1, "R_R");
    add("Type", 1, "I_E");
    add("Add", 2, "E_EE");
    add("Sub", 2, "E_EE");
    add("Mul", 2, "E_EE");
    add("Div", 2, "E_EE");
    add("Mod", 2, "E_EE");
    add("Pow", 2, "R_RR");
    add("Neg", 1, "E_E");
    add("Inc", 1, "E_E");
    add("Dec", 1, "E_E");
    add("Equ", 2, "I_EE");
    add("Neq", 2, "I_EE");
    add("Lt", 2, "I_EE");
    add("Gt", 2, "I_EE");
    add("Lte", 2, "I_EE");
    add("Gte", 2, "I_EE");
    add("Not", 1, "I_I");
    add("And", 2, "I_II");
    add("Or", 2, "I_II");
    add("Xor", 2, "I_II");
    add("Inv", 1, "I_I");
    add("<<", 2, "I_II");
    add(">>", 2, "I_II");
    add("Odd", 1, "I_I");
    add("Abs", 1, "E_E");
    add("Sgn", 1, "E_E");
    add("Sgn2", 1, "E_E");
    add("Min", 2, "E_EE");
    add("Max", 2, "E_EE");
    add("Tween", 3, "I_EEE");
    add("?:", 3, "E_IEE");
    add("Squ", 1, "E_E");
    add("Sqr", 1, "R_R");
    add("Dist", 2, "R_RR");
    add("Ln", 1, "R_R");
    add("Log10", 1, "R_R");
    add("Sin", 1, "R_R");
    add("Cos", 1, "R_R");
    add("Tan", 1, "R_R");
    add("Asin", 1, "R_R");
    add("Acos", 1, "R_R");
    add("Atan", 1, "R_R");
    add("Ang", 2, "R_RR");
    add("SinD", 1, "R_R");
    add("CosD", 1, "R_R");
    add("TanD", 1, "R_R");
    add("AsinD", 1, "R_R");
    add("AcosD", 1, "R_R");
    add("AtanD", 1, "R_R");
    add("AngD", 2, "R_RR");
    add("Floor", 1, "R_R");
    add("Fract", 1, "R_R");
    add("DMS", 3, "R_RRR");
    add("Rnd", 2, "I_II");
    add("Rgb", 3, "I_III");
    add("RgbR", 1, "I_I");
    add("RgbG", 1, "I_I");
    add("RgbB", 1, "I_I");
    add("Blend", 3, "I_IIR");
    add("Hue", 1, "I_R");
    add("Hue2", 1, "I_R");
    add("Char", 2, "I_II");
    add("Signs", 0, "I_");
    add("Objs", 0, "I_");
    add("Asps", 0, "I_");
    add("Mon", 0, "I_");
    add("Day", 0, "I_");
    add("Yea", 0, "I_");
    add("Tim", 0, "R_");
    add("Dst", 0, "R_");
    add("Zon", 0, "R_");
    add("Lon", 0, "R_");
    add("Lat", 0, "R_");
    add("Mon1", 0, "I_");
    add("Day1", 0, "I_");
    add("Yea1", 0, "I_");
    add("Tim1", 0, "R_");
    add("Dst1", 0, "R_");
    add("Zon1", 0, "R_");
    add("Lon1", 0, "R_");
    add("Lat1", 0, "R_");
    add("Mon2", 0, "I_");
    add("Day2", 0, "I_");
    add("Yea2", 0, "I_");
    add("Tim2", 0, "R_");
    add("Dst2", 0, "R_");
    add("Zon2", 0, "R_");
    add("Lon2", 0, "R_");
    add("Lat2", 0, "R_");
    add("Mon3", 0, "I_");
    add("Day3", 0, "I_");
    add("Yea3", 0, "I_");
    add("Tim3", 0, "R_");
    add("Dst3", 0, "R_");
    add("Zon3", 0, "R_");
    add("Lon3", 0, "R_");
    add("Lat3", 0, "R_");
    add("Mon4", 0, "I_");
    add("Day4", 0, "I_");
    add("Yea4", 0, "I_");
    add("Tim4", 0, "R_");
    add("Dst4", 0, "R_");
    add("Zon4", 0, "R_");
    add("Lon4", 0, "R_");
    add("Lat4", 0, "R_");
    add("Mon5", 0, "I_");
    add("Day5", 0, "I_");
    add("Yea5", 0, "I_");
    add("Tim5", 0, "R_");
    add("Dst5", 0, "R_");
    add("Zon5", 0, "R_");
    add("Lon5", 0, "R_");
    add("Lat5", 0, "R_");
    add("Mon6", 0, "I_");
    add("Day6", 0, "I_");
    add("Yea6", 0, "I_");
    add("Tim6", 0, "R_");
    add("Dst6", 0, "R_");
    add("Zon6", 0, "R_");
    add("Lon6", 0, "R_");
    add("Lat6", 0, "R_");
    add("MonN", 1, "I_I");
    add("DayN", 1, "I_I");
    add("YeaN", 1, "I_I");
    add("TimN", 1, "R_I");
    add("DstN", 1, "R_I");
    add("ZonN", 1, "R_I");
    add("LonN", 1, "R_I");
    add("LatN", 1, "R_I");
    add("MonL", 1, "I_I");
    add("DayL", 1, "I_I");
    add("YeaL", 1, "I_I");
    add("TimL", 1, "R_I");
    add("DstL", 1, "R_I");
    add("ZonL", 1, "R_I");
    add("LonL", 1, "R_I");
    add("LatL", 1, "R_I");
    add("MonS", 0, "I_");
    add("DayS", 0, "I_");
    add("YeaS", 0, "I_");
    add("TimS", 0, "R_");
    add("DstS", 0, "R_");
    add("ZonS", 0, "R_");
    add("LonS", 0, "R_");
    add("LatS", 0, "R_");
    add("MonT", 0, "I_");
    add("DayT", 0, "I_");
    add("YeaT", 0, "I_");
    add("TimT", 0, "R_");
    add("MonG", 0, "I_");
    add("DayG", 0, "I_");
    add("YeaG", 0, "I_");
    add("DstD", 0, "R_");
    add("ZonD", 0, "R_");
    add("LonD", 0, "R_");
    add("LatD", 0, "R_");
    add("ObjLon", 1, "R_I");
    add("ObjLat", 1, "R_I");
    add("ObjDir", 1, "R_I");
    add("ObjDirY", 1, "R_I");
    add("ObjDirL", 1, "R_I");
    add("ObjHouse", 1, "I_I");
    add("ObjLon1", 1, "R_I");
    add("ObjLat1", 1, "R_I");
    add("ObjDir1", 1, "R_I");
    add("ObjDirY1", 1, "R_I");
    add("ObjDirL1", 1, "R_I");
    add("ObjHouse1", 1, "I_I");
    add("ObjLon2", 1, "R_I");
    add("ObjLat2", 1, "R_I");
    add("ObjDir2", 1, "R_I");
    add("ObjDirY2", 1, "R_I");
    add("ObjDirL2", 1, "R_I");
    add("ObjHouse2", 1, "I_I");
    add("ObjLon3", 1, "R_I");
    add("ObjLat3", 1, "R_I");
    add("ObjDir3", 1, "R_I");
    add("ObjDirY3", 1, "R_I");
    add("ObjDirL3", 1, "R_I");
    add("ObjHouse3", 1, "I_I");
    add("ObjLon4", 1, "R_I");
    add("ObjLat4", 1, "R_I");
    add("ObjDir4", 1, "R_I");
    add("ObjDirY4", 1, "R_I");
    add("ObjDirL4", 1, "R_I");
    add("ObjHouse4", 1, "I_I");
    add("ObjLon5", 1, "R_I");
    add("ObjLat5", 1, "R_I");
    add("ObjDir5", 1, "R_I");
    add("ObjDirY5", 1, "R_I");
    add("ObjDirL5", 1, "R_I");
    add("ObjHouse5", 1, "I_I");
    add("ObjLon6", 1, "R_I");
    add("ObjLat6", 1, "R_I");
    add("ObjDir6", 1, "R_I");
    add("ObjDirY6", 1, "R_I");
    add("ObjDirL6", 1, "R_I");
    add("ObjHouse6", 1, "I_I");
    add("ObjLonN", 2, "R_II");
    add("ObjLatN", 2, "R_II");
    add("ObjDirN", 2, "R_II");
    add("ObjDirYN", 2, "R_II");
    add("ObjDirLN", 2, "R_II");
    add("ObjHouseN", 2, "I_II");
    add("ObjX", 1, "R_I");
    add("ObjY", 1, "R_I");
    add("ObjZ", 1, "R_I");
    add("ObjXN", 1, "R_I");
    add("ObjYN", 1, "R_I");
    add("ObjZN", 1, "R_I");
    add("ObjOn", 1, "I_I");
    add("ObjOnT", 1, "I_I");
    add("ObjOrb", 1, "R_I");
    add("ObjAdd", 1, "R_I");
    add("ObjInf", 1, "R_I");
    add("ObjInfT", 1, "R_I");
    add("ObjCol", 1, "I_I");
    add("ObjRul", 1, "I_I");
    add("ObjRul2", 1, "I_I");
    add("ObjRulS", 1, "I_I");
    add("ObjRulS2", 1, "I_I");
    add("ObjRulH", 1, "I_I");
    add("ObjRulH2", 1, "I_I");
    add("ObjExa", 1, "I_I");
    add("ObjRay", 1, "I_I");
    add("ObjDist", 1, "R_I");
    add("ObjYear", 1, "R_I");
    add("ObjDiam", 1, "R_I");
    add("ObjDay", 1, "R_I");
    add("AspAngle", 1, "R_I");
    add("AspOrb", 1, "R_I");
    add("AspInf", 1, "R_I");
    add("AspCol", 1, "I_I");
    add("Cusp", 1, "R_I");
    add("Cusp3D", 1, "R_I");
    add("Cusp1", 1, "R_I");
    add("Cusp3D1", 1, "R_I");
    add("Cusp2", 1, "R_I");
    add("Cusp3D2", 1, "R_I");
    add("Cusp3", 1, "R_I");
    add("Cusp3D3", 1, "R_I");
    add("Cusp4", 1, "R_I");
    add("Cusp3D4", 1, "R_I");
    add("HouseInf", 1, "R_I");
    add("PlusZone", 1, "I_I");
    add("SignRul", 1, "I_I");
    add("SignRul2", 1, "I_I");
    add("SignEso", 1, "I_I");
    add("SignEso2", 1, "I_I");
    add("SignHie", 1, "I_I");
    add("SignHie2", 1, "I_I");
    add("SignRay", 1, "I_I");
    add("SignRay2", 2, "I_II");
    add("RayCol", 1, "I_I");
    add("LonSign", 1, "I_R");
    add("LonDeg", 1, "R_R");
    add("LonHouse", 1, "I_R");
    add("LonHou3D", 2, "R_RR");
    add("LonDist", 2, "R_RR");
    add("LonDiff", 2, "R_RR");
    add("LonMid", 2, "R_RR");
    add("LonDecan", 1, "R_R");
    add("LonNavam", 1, "R_R");
    add("LonDwad", 1, "R_R");
    add("LonTerm", 2, "I_RI");
    add("DayWeek", 3, "I_III");
    add("JulianT", 0, "R_");
    add("LATLMT", 0, "R_");
    add("PolDist", 4, "R_RRRR");
    add("Oblique", 0, "R_");
    add("RAMC", 0, "R_");
    add("DeltaT", 0, "R_");
    add("SidDiff", 0, "R_");
    add("Nutation", 0, "R_");
    add("HouseSys", 0, "I_");
    add("AspLon", 3, "I_III");
    add("AspLon2", 3, "I_III");
    add("AspLat", 3, "I_III");
    add("AspLat2", 3, "I_III");
    add("GridNam", 2, "I_II");
    add("GridVal", 2, "R_II");
    add("DoGrid", 0, "I_");
    add("DoGrid2", 1, "I_I");
    add("ListCnt", 0, "I_");
    add("ListCur", 0, "I_");
    add("List1", 0, "I_");
    add("List2", 0, "I_");
    add("TiltXY", 2, "R_IR");
    add("Context", 0, "I_");
    add("Version", 0, "R_");
    add("=Obj", 4, "R_IIII");
    add("=Hou", 4, "R_IIII");
    add("_v3", 0, "I_");
    add("_v31", 0, "I_");
    add("_w1", 0, "I_");
    add("_aj", 0, "I_");
    add("_L1", 0, "I_");
    add("_L2", 0, "I_");
    add("_d1", 0, "I_");
    add("_EY", 0, "I_");
    add("_E01", 0, "I_");
    add("_E02", 0, "I_");
    add("_P1", 0, "I_");
    add("_N1", 0, "I_");
    add("_80", 0, "I_");
    add("_I1", 0, "I_");
    add("_zv", 0, "R_");
    add("_zf", 0, "R_");
    add("_A3", 0, "I_");
    add("_Ap", 0, "I_");
    add("_APP", 0, "I_");
    add("_b", 0, "I_");
    add("_b0", 0, "I_");
    add("_c", 0, "I_");
    add("_c3", 0, "I_");
    add("_c31", 0, "I_");
    add("_s", 0, "I_");
    add("_s0", 0, "I_");
    add("_s1", 0, "R_");
    add("_sr", 0, "I_");
    add("_sr0", 0, "I_");
    add("_h", 0, "I_");
    add("_p", 0, "I_");
    add("_p0", 0, "I_");
    add("_pd", 0, "R_");
    add("_pC", 0, "R_");
    add("_x", 0, "R_");
    add("_1", 0, "I_");
    add("_3", 0, "I_");
    add("_4", 0, "I_");
    add("_f", 0, "I_");
    add("_G", 0, "I_");
    add("_J", 0, "I_");
    add("_9", 0, "I_");
    add("_YT", 0, "I_");
    add("_YV", 0, "I_");
    add("_Yf", 0, "I_");
    add("_Yh", 0, "I_");
    add("_Ym", 0, "I_");
    add("_Ys", 0, "I_");
    add("_Ys1", 0, "R_");
    add("_Yn", 0, "I_");
    add("_Yn0", 0, "I_");
    add("_Yz0", 0, "R_");
    add("_Yu", 0, "I_");
    add("_Yu0", 0, "I_");
    add("_Yr", 0, "I_");
    add("_YC", 0, "I_");
    add("_YO", 0, "I_");
    add("_Y8", 0, "I_");
    add("_Ya", 0, "I_");
    add("_Yao", 0, "I_");
    add("_Yoo", 0, "I_");
    add("_Ycc", 0, "I_");
    add("_Yp", 0, "I_");
    add("_YZ", 0, "I_");
    add("_Yb", 0, "I_");
    add("_YR0", 1, "I_I");
    add("_YR1", 1, "I_I");
    add("_YR2", 1, "I_I");
    add("_YRZ", 1, "I_I");
    add("_YR7", 1, "I_I");
    add("_Y5I1", 0, "I_");
    add("_Y5I2", 0, "I_");
    add("_XI1", 0, "R_");
    add("_XI2", 0, "I_");
    add("_Xr", 0, "I_");
    add("_Xm", 0, "I_");
    add("_XT", 0, "I_");
    add("_Xi", 0, "I_");
    add("_Xuu", 0, "I_");
    add("_Xx", 0, "I_");
    add("_Xll", 0, "I_");
    add("_XA", 0, "I_");
    add("_XL", 0, "I_");
    add("_Xj", 0, "I_");
    add("_XF", 0, "I_");
    add("_XW0", 0, "I_");
    add("_Xee", 0, "I_");
    add("_XU", 0, "I_");
    add("_XC", 0, "I_");
    add("_XQ", 0, "I_");
    add("_XN", 0, "I_");
    add("_Xwx", 0, "I_");
    add("_Xwy", 0, "I_");
    add("_Xnn", 0, "I_");
    add("_Xs", 0, "I_");
    add("_XSS", 0, "I_");
    add("_XU0", 0, "I_");
    add("_XE1", 0, "I_");
    add("_XE2", 0, "I_");
    add("_XE", 0, "I_");
    add("_XL0", 0, "I_");
    add("_X1", 0, "I_");
    add("_Xv", 0, "I_");
    add("_XJJ", 0, "I_");
    add("_X8", 0, "I_");
    add("_XGx", 0, "R_");
    add("_XGy", 0, "R_");
    add("_XZ", 0, "I_");
    add("_YXG", 0, "I_");
    add("_YXGc", 0, "I_");
    add("_YXGu", 0, "I_");
    add("_YXGp", 0, "I_");
    add("_YXGl", 0, "I_");
    add("_YXGv", 0, "I_");
    add("_YXGe", 0, "I_");
    add("_YXe", 0, "I_");
    add("_YXa", 0, "I_");
    add("_YXf", 0, "I_");
    add("_YXft", 0, "I_");
    add("_YXfs", 0, "I_");
    add("_YXfh", 0, "I_");
    add("_YXfo", 0, "I_");
    add("_YXfa", 0, "I_");
    add("_YXfn", 0, "I_");
    add("_YXW", 0, "I_");
    add("_YXK", 1, "I_I");
    add("DCol", 1, "I_I");
    add("DDot", 2, "I_II");
    add("DSpot", 2, "I_II");
    add("DLine", 4, "I_IIII");
    add("DBox", 4, "I_IIII");
    add("DBlock", 4, "I_IIII");
    add("DCirc", 4, "I_IIII");
    add("DDisk", 4, "I_IIII");
    add("DText", 3, "I_III");
    add("DSign", 3, "I_III");
    add("DHouse", 3, "I_III");
    add("DObj", 3, "I_III");
    add("DAsp", 3, "I_III");
    add("DNak", 3, "I_III");
    add("_Xnp", 0, "I_");
    add("_Xnf", 0, "I_");
    add("Dlg", 0, "I_");
    add("Mouse", 1, "I_I");
    add("_WN", 0, "I_");
    add("_Wnn", 0, "I_");
    add("_Wh", 0, "I_");
    add("_Wt", 0, "I_");
    add("_Wo", 0, "I_");
    add("_Wo0", 0, "I_");
    add("_Wo3", 0, "I_");
    add("_WZ", 0, "I_");
    add("PC", 0, "I_");
    add("WIN", 0, "I_");
    add("X11", 0, "I_");
    add("WCLI", 0, "I_");
    add("WSETUP", 0, "I_");
    add("JPLWEB", 0, "I_");
    add("Var", 1, "E_I");
    add("Do", 2, "E_XE");
    add("Do2", 3, "E_XXE");
    add("Do3", 4, "E_XXXE");
    add("If", 2, "E_IX");
    add("IfElse", 3, "E_IXX");
    add("DoCount", 2, "E_IX");
    add("While", 2, "E_XE");
    add("DoWhile", 2, "E_XE");
    add("For", 4, "I_IIIX");
    add("Macro", 1, "E_I");
    add("Switch", 1, "I_I");
    add("RndSeed", 1, "I_I");
    add("Assign", 2, "E_IE");
    add("=", 2, "E_IE");
    add("=A", 1, "E_E");
    add("=B", 1, "E_E");
    add("=C", 1, "E_E");
    add("=D", 1, "E_E");
    add("=E", 1, "E_E");
    add("=F", 1, "E_E");
    add("=G", 1, "E_E");
    add("=H", 1, "E_E");
    add("=I", 1, "E_E");
    add("=J", 1, "E_E");
    add("=K", 1, "E_E");
    add("=L", 1, "E_E");
    add("=M", 1, "E_E");
    add("=N", 1, "E_E");
    add("=O", 1, "E_E");
    add("=P", 1, "E_E");
    add("=Q", 1, "E_E");
    add("=R", 1, "E_E");
    add("=S", 1, "E_E");
    add("=T", 1, "E_E");
    add("=U", 1, "E_E");
    add("=V", 1, "E_E");
    add("=W", 1, "E_E");
    add("=X", 1, "E_E");
    add("=Y", 1, "E_E");
    add("=Z", 1, "E_E");
}

}
