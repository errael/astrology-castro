# astrology-castro - v0.1.0 early beta
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
- Integer constants are decimal, hex (0x), binary(0b). Octal not supported.
- Floating constants are decimal ###.###, exponents not supported.
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


##      Running castro

Requires jre-11 or later. The released jar is executable, use a script named castro like
```
#!/bin/sh
CASTRO_JAR=/lib/castro-0.1.0.jar
java -jar $CASTRO_JAR "$@"
```

Running `castro`  on a file produces 3 output files. For example, if there's `foo.castro` then executing `castro foo.castro` creates
- `foo.as` can be executed by `Astrolog` with `-i foo.as`
- `foo.def` has details of allocation
- `foo.map` has a summary of allocation for all files

When multiple files are compiled together, the `.map` base file name defaults to the first file in the input file list; it can be specifed with the `--mapname=base`.

There's examples.d, astrotest.d and test.d with their gold files.

### Working with multiple files.

Compiling multiple files together is currently the only way to get symbolic references between files resolved.

There is a test which compiles and runs on astrolog at
[multi-file test](https://github.com/errael/astrology-castro/tree/main/astrotest.d)
It is compiled and run with
```
castro --mapn=testing flow_control.castro test_infra.castro main.castro
astrolog -i flow_control.as -i test_infra.as -i main.as
```
main.castro is simply
```
run {
    ~1 { Switch(test_control_flow); }
}
```
and loaded last to make sure all the macro and switch are loaded/defined. Examine the `.def` files to see the allocation details per file. The file `testing.map` shows the allocation for all the files, in this excerpt
```
var test_name[2] @102;    // [ALLOC] test_infra.castro
var cond @200;    // [ALLOC] flow_control.castro
```
note that each line has the file name in which the variable is declared. To run this under released Astrolog-7.60, remove the `layout switch` directives.

Allocation is done in a way such that things can be moved around in a file and after re-compilation there is no change in the locations of allocated variables. If no variables are added, removed or renamed, and their sizes are the same then their allocated locations does not change no matter the order of their declaration.

### Warnings instead of Errors
Some errors that `castro` reports, may in fact not be errors depending on the targeted version of `Astrolog` or because the "programmer knows what they're doing". There are command line options to treat specified errors as options; try `castro -h`.

##      Castro Language

###     Statement summary
These are the basic statements
- `layout` directives constrain automatic allocation. The three regions are memory/macro/switch. `layout` is optional and, if present, must be before anything else.
- `var` declarations and initialization.
- `macro` definitions result in `~M` `Astrolog`commands.
- `switch` definitions result in `-M0` `Astrolog` commands.
- `run` results in inline top level command switches. Parsed as `switch`, but not embedded in a `-M0`.
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
The `macro` statement defines an `AstroExpression macro` using `~M`; it contains expressions with function calls. See [AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express); there are a wide variety of function calls. The value of `Macro(some_macro)` is the value of the last statement/expr in some_macro as defined by `Astrolog`.

####   Flow Control Statements
- `if (`_expr_`)` _expr_
- `if (`_expr_`)` _expr_ `else` _expr_
- `repeat (`_expr_`)` _expr_
- `while (`_expr_`)` _expr_
- `do` _expr_ `while (`_expr_`)`
- `for(` _var_ `=` _expr_ `;` _expr_ `)` _expr_
- _expr_ `?` _expr_ `:` _expr_
- `{` _one or more expr separated by semi-colon_ `}`

Note that everything is an expression, including the flow control statements themselves.

#####   Macro() and Switch()
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

#####   ? :
In `castro`, `e1 ? e2 : e3` follows the same semantics as `if(e1) e2 else e3` (and `"C"`) and only evaluates one of `e2` or `e3`. This is different from `Astrolog`'s `? :` operator which evaluates both `e2` and `e3`. `castro` provides a function `QuestColon(e1, e2, e3)` which has the `Astrolog` semantics.

###     switch
The `switch` statement generates a `command switch macro` using `-M0`; it contains `Astrolog command switch`es and their arguments.
```
var aspect;
var orb;
var var_strings[3];

switch nameId @12 {
    -zl "122W19:59" "47N36:35"
    ~1 { aspect = 7; orb = 2; }
    -Ao {~ aspect; } {~ orb; }
    SetString var_strings[0] "one" 'two' "three"
}
```
All `Astrolog` commands that start with `~`, except `~0`, `_~0`, take an `AstroExpression` as an argument; it is delineated with `{` and `}`. An `AstroExpression` can be used as an argument to a `command switch macro`; it is delineated by `{~` and `}`. `SetString` is used to assign strings. `~2`, `~20`, `~M` commands are not directly supported.

Note that `@12` assigns 12 to the switch address which binds it to **F12**; it is optional. `Astrolog` versions after v7.60 are expected to support `command switch macro` numbers outside the function key range, as it does with the `AstroExpression macro`.

###     run
The contents of a `run` statement are parsed identically to a `switch` statement. The difference is that the switch commands are at the top level of the `.as` file and not embedded in a `-M0`; they are executed when the file is sourced as in `-i file`.

###     copy
The `copy{LITERALLY_COPIED_TO_OUTPUT}` statement literally copies text to the output file with no interpretation or changes; the ultimate hack/workaround. All whitespace, including newlines, is copied as is. Use '\}' to include a '}' in the output. It's unclear if this is needed, it does provide a way to redefine a macro/switch.

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


##      TODO
Handle single file compilation, uses the .map file as input. Not sure this is an essential feature.


##     Warnings/Oddities:
- There are some cases where blanks are significant
    - The functions `=Obj` and `=Hou` can cause problems, for example `a==Obj(...)` is parsed as `a == Obj(...)`,
      write `a= =Obj(...)` for assignment.
      `castro` provides `AssignObj(...)` and `AssignHou(...)` as unambiguous alternates.
    - `{~` introduces an AstroExpression whose result is used as a parameter to a command switch,
      for example `=R {~ a+b; }`.
      But `{ ~ a+b; }` has a space between `{` and `~` and is different,
      it parses as `{ (~a) + b; }`

- A `switch` is almost free form text, except inside `AstroExpression`s as with `~cmd {...}` and `-cmd {~ ... }`. If a command switch argument contains language special characters,
like the `:` in `-zl "122W19:59" "47N36:35"` the word should be quoted. This requirement may be
relaxed in the future.

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
