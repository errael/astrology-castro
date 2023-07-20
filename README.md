# astrology-castro
`castro` compiles a simple "C" like language into [Astrolog](https://www.astrolog.org) commands and [AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express); `castro` is tailored to `AstroExpression`. `castro` is a standalone tool; its output is a `.as` file that can be used with `Astrolog`'s command switch `-i <filename>`. Some motivating factors for `castro` were familiar expression syntax (avoid writing and maintaining the prefix notation expressions), automatic memory allocation, referring to things by name rather than address.

Here's a simple example. Note that the switch, macro and variable definitions could be in 3 different files. As in `Astrolog`, function names are case insensitive. _Switch and macro names are case sensitive_.
```
var yearA;
var yearB;

// yearA/yearB inclusive
switch progressedAspectsBetweenYearsAB {
    -dpY {~ yearA < yearB ? yearA : yearB; } {~ Abs(yearA - yearB) + 1; }
}

macro progressByYears {
    yearA = 1973;
    yearB = 1975;
    Switch(progressedAspectsBetweenYearsAB);
}
```
which generates
```
-M0 1 '-dpY  "~ IfElse Lt @27 @28 @27 @28" "~ Add Abs Sub @27 @28 1"'
~M 1 '= 27 1973 = 28 1975 Switch 1'
```

This shows that `castro` is a thin layer that mirrors `Astrolog` and `AstroExpression` basics. See DISCUSSION for musings on possible extensions.

`castro` has a `layout` directive which constrains the allocated address; in addition, it is possible to assign addresses.

###      Differences from "C"
<!--
<details>
<summary>statement differences</summary>
-->
####    statement/expression differences

- Everything has a value, `if`, `while`, `do`, `for`, `repeat`, `{}`, assignments, expressions as described in the `Astrolog` documentation.
- No semi-colons before `else` or before while in `do while()`.
- No `||` or `&&`, only `|`,`&`. Primary downside is no short circuit execution.
- No user defined functions, only builtin functions.
- Address of and indirect, `&var_name` and `*var_name` supported;
  `&` and `*` are only used with an identifier, nothing more complex.
<!--
</details>

<details>
<summary>variable differences</summary>
-->
####    variable differences

- Single char variable names 'a' to 'z' are pre-declared.
- Variables are declared with `var`, for example `var foo;`
- A variable is integer or float depending on usage. `AstroLog` truncates as needed.
- A variable declaration may assign the variable or array to a specific location; append `@integer`, for example `var foo @100;` and `var bar[10] @200;`; this assigns `foo` to location 100 and array `bar` starts at location 200.
<!--
</details>
-->


##      Castro Language

###     Statement summary
These are the basic statements
- `layout` directives constrain automatic allocation. The three regions are memory/macro/switch. `layout` is optional and, if present, must be before anything else.
- `var` declarations and initialization.
- `macro` definitions result in `~M` `Astrolog`commands.
- `switch` definitions result in `-M0` `Astrolog` commands.
- `run` results in inline top level command switches. Contents like `switch`, but not embedded in a `-M0`.
- `copy` literally copies text to the output file with no interpretation or changes.

`castro` identifiers are the same as with `"C"`, likewise operators have the same precedence as with `"C"`; blanks and newlines are just white space.

###     layout
`layout` statement specifies, on a per file basis, the addresses that automatic allocation can use for `memory`, `macro`, and `switch` addresses.
```
layout memory {
    base 101;
    limit 111;
    reserve 104, 106:108;
}
```
This directive allows allocation of addresses between 101 inclusive and 111 exclusive; but not addresses 104, 106, 107, 108. If an _out of memory_ error occurs look at the `.def` output file for more information.

###     macro
The `macro` statement defines an `AstroExpression macro` using `~M`; it contains expressions with function calls. See [AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express); there are a wide variety of function calls.

#####   Control Flow Statements
- `if (`_expr_`)` _expr_
- `if (`_expr_`)` _expr_ `else` _expr_
- `repeat (`_expr_`)` _expr_
- `while (`_expr_`)` _expr_
- `do` _expr_ `while (`_expr_`)`
- `{` _one or more expr separated by semi-colon_ `}`

Note that everything is an expression, including the control flow statements.

The `macro()` and `switch()` functions take either an identifier, which is a `macro` or `switch` name respectively. They also take an expression which evaluates to an index. Expressions and function calls are as usual. Note the following
```
var pp;
macro m1 {
    macro(100 + a);
    pp = 100 + a;
    macro(pp);      // This generates an error, the identifier pp is not the name of a macro
    macro(+pp);     // This works because "+pp" is unambiguously an expression
}
```

###     switch
The `switch` statement defines a `command switch macro` using `-M0`; it contains `Astrolog command switch`es and their arguments.
```
var aspect;
var orb;
var var_strings[3];

switch nameId @12 {
    -zl 122W19:59 47N36:35
    ~1 { aspect = 7; orb = 2; }
    -Ao {~ aspect; } {~ orb; }
    SetString var_strings[0] "one" "two" "three"
}
```
Note that `@12` attaches this macro to **F12**; it is optional, but until `Astrolog` supports `command switch macro` numbers outside the function key range it should be specified, rather than using automatic allocation. All `Astrolog` commands that start with `~`, except `~0`, `_~0`, `~2` and `~20`, take an `AstroExpression` as an argument; it is delineated with `{` and `}`. An `AstroExpression` can be used as an argument to a `command switch macro`; it is delineated by `{~` and `}`. `SetString`, and it's aliases, is used to assign strings.

###     run
The contents of a `run` statement are parsed identically to a `switch` statement. The difference is that the switch commands are at the top level of the `.as` file output and not embedded in a `-M0`.

###     copy
The `copy` statement literally copies text to the output file with no interpretation or changes; the ultimate hack/workaround.

###     Variables

Variable declarations take on one of the following forms
```
var var_name1;            // automatically allocate
var var_name2[4];         // automatically allocate
var var_name1 @100;
var var_name2[4] @101;
```

<!--
<details>
<summary>numeric and string initialization examples</summary>
-->

#####   Initializing numeric variables

Note that forward references in initialization expressions will use whatever value happens to be there.

```
var var1 {1};       // initialize automantically allocated variable
var var2 @100 {1};  // initialize variable assigned to addess 100
var var_array1[] { a+b, c+d };  // declare and initialize 2 element array
var var_array2[4] { a+b, c+d };  // 4 element array, initialize first two elements
```
Builtin variables can be initialized like other non-array variables; but they can **not** have their address assigned.


#####   Initializing string variables

Strings are initialized in `switch {...}` or `run {...}`.
```
var var1;
var var_array[4];
switch someSwitch {
    SetString var_array[0] "one" "two"  // assign string to var_array[0], var_array[1]
    SetString var_array[3] "one"        // assign string to var_array[3]
    SetString var1 "one"                // assign string to var1
}
```
Use `SetString`, `setstring`, `AssignString`, `assignstring`, `SetStrings`, `setstrings`, `AssignStrings`, or `assignstrings`.

<!--
</details>
-->

##      Running castro

Requires jre-11 or later. The released jar is executable, I use a script like
```
#!/bin/sh
CASTRO_JAR=/lib/castro-0.1.0.jar
java -jar $CASTRO_JAR "$@"
```



After running `castro`, look at the resulting `.as` to see what happened.
Explore examples.d and test.d with their gold files.

Allocation is done in a way such that things can be moved around in a file and after re-compilation there is no change in the locations of allocated variables. If no variables are added, removed or renamed, and their sizes are the same then their allocated locations does not change no matter the order of their declaration.

### Warnings instead of Errors
Some errors that `castro` reports, may in fact not be errors depending on the targeted version of `Astrolog` or because the "programmer knows what they're doing". There are command line options to treat specified errors as options; try `castro -h`.


##      TODO
Handle single file compilation, uses the .map file as input.


##     Warnings/Oddities:
- There are some cases where blanks are significant
    - The functions `=Obj` and `=Hou` can cause problems, for example `a==Obj(...)` is parsed as `a == Obj(...)`,
      write `a= =Obj(...)` for assignment.
      `castro` provides `AssignObj(...)` and `AssignHou(...)` as unambiguous alternates.
    - `{~` introduces an AstroExpression whose result is used as a parameter to a command switch,
      for example `=R {~ a+b; }`.
      But `{ ~ a+b; }` has a space between `{` and `~` and is different,
      it parses as `{ (~a) + b; }`

- 

- `;` ends an expression. Beware a missing `;`. Consider
    ```
    macro ex1 { a += 3; -3; }   // add 3 to "a", return -3.
    macro ex2 { a += 3 -3; }    // add 0 to "a", does "a += (3 - 3)", return "a".
    macro ex3 { (a += 3) -3; }  // add 3 to "a", subtract 3 from result, returns original "a"
    ```

    Use "--gui" to graphically see how expressions are parsed.

    More complex examples
    ```
    macro ex4 { repeat(3) { a += 1; }; -3; }    // similar to ex1
    macro ex5 { repeat(3) { a += 1; } -3; }     // similar to ex3, but only weirdly so
    ```
    `ex5` does `{ a += 1; } -3;` three times. The result of first two subtractions are thrown away, the last is the value of the `repeat(3)`.

    _A prefix notation predence/parenthesis free language can be nice and does have advantages._
