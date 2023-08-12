# castro - v1 beta.4
`castro` compiles a simple "C" like language into [Astrolog](https://www.astrolog.org) commands and [AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express); `castro` is tailored to `AstroExpression` (and WYSIWYG). `castro` is a standalone tool. It outputs a `.as` file that can be used with `Astrolog`'s command switch `-i <name>.as`. `castro` easily interoperates with existing `Astrolog` command switch files.

Some motivating factors for `castro`
- familiar expression syntax (avoid writing and maintaining the prefix notation expressions),
- referring to things by name rather than address.
- automatic memory allocation

**There's a cheat sheet**<br>
For those who like to play around before reading the docs: [cheat sheet](#cheat-sheet).

**Building castro**<br>
See [codegen](codegen).
<!--
See `grammar`/[README](grammar/README.md)
and `codegen`/[README](codegen/README.md).
-->

Here's a simple example. Note that the switch, macro and variable definitions could be in 3
different files. As in `Astrolog`, function names are case insensitive;
following that convention _Switch and macro names are case insensitive_.
```
var yearA;
var yearB;

macro progressByYears {
    yearA = 1973;
    yearB = 1975;
    Switch(progressedAspectsBetweenYearsAB);
}

// yearA/yearB inclusive
switch progressedAspectsBetweenYearsAB {
    -dpY {~ yearA < yearB ? yearA : yearB; } {~ Abs(yearA - yearB) + 1; }
}
```
which generates
```
~M 1 '= 27 1973 = 28 1975 Switch 1'
-M0 1 '-dpY  "~ IfElse Lt @27 @28 @27 @28" "~ Add Abs Sub @27 @28 1"'
```

This shows that `castro` is a thin layer that mirrors `Astrolog` and `AstroExpression` basics. See [discussions](https://github.com/errael/astrology-castro/discussions) for musings on possible extensions.

For more examples, there is 
- [mazegame ported to castro](examples.d/mazegame.castro)
- [astroExpressionDocsCommandSwitches.castro](examples.d/astroExpressionDocsCommandSwitches.castro) has the examples from `Astrolog` website under [AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express) ported to castro.
- [astrotest.d](astrotest.d) executes on astrolog and has a simple expect/result infrastructure
- [test.d](test.d) checks lowlevel functionality and has gold files.
- there's [examples.d](examples.d)

###     Interoperability

`castro` works with existing `command switch` files and their declared switch/macro/variables.

`castro` has a [layout directive](#layout) which constrains the addresses which it automatically allocate to specified areas; in addition, it is possible to assign addresses. In order to reference items _defined in an existing command switch file_, use
```
switch a_switch @33;     // in a non castro file there's: -M0 33 "..."
macro a_macro @50;       // in a non castro file there's: ~M 50 "..."
var a_var @60;
```
To assign `switch`/`macro` in a `castro` file to a specific adress:
```
switch b_switch @44 { ... }     // from another file: '-M 44' or '~1 "Switch 44"'
macro b_macro @55 { ... }       // from another file: '~1 "Macro 55"'
```

###      Differences from "C"

`castro` has a weird looking printf, see [castro printf](#castro-printf). And examples [cprintf.castro](examples.d/cprintf.castro) for a description that compiles and runs.

####    statement/expression differences

- Everything has a value, `if`, `while`, `do`, `for`, `repeat`, `{}`, assignments, expressions as described in the `Astrolog` documentation.
- No semi-colons before `else` or before while in `do while()`.
- No `||` or `&&`, only `|`,`&`. Primary downside is no short circuit execution.
- No user defined functions, only builtin functions.
- Address of and indirect, `&var_name` and `*var_name` supported;
  `&` and `*` are only used with an identifier, nothing more complex.
- Integer constants are decimal, hex (0x), binary(0b), octal(0o).
- Floating constants are decimal ###.###, exponents not supported.

####    variable differences

- Variable names are case insensitive.
- Single char variable names 'a' to 'z' are pre-declared.
  AstroExpression hooks use as much as %u ... %z.
  [castro printf](#castro-printf) uses as much as %a ... %j but there is a
  way to have cprintf automatically preserve variables.
- Variables are declared with `var`, for example `var foo;`
- A variable is integer or float depending on usage. `Astrolog` truncates as needed.
- A variable declaration may assign the variable or array to a specific location; append `@integer`, for example `var foo @100;` and `var bar[10] @200;`; this assigns `foo` to location 100 and array `bar` starts at location 200.

##      Running castro

Requires jre-11 or later. The released jar is executable, use a script named castro like
```
#!/bin/sh
CASTRO_JAR=/lib/castro-0.1.0.jar
java -jar $CASTRO_JAR "$@"
```
Do `castro -h` to see [help/usage](https://github.com/errael/astrology-castro/wiki/castro-help).

Running castro like `castro file_name -o -` is convenient to see the compiler output on
the console. The `--fo=min` option might be handy.

Running `castro`  on a file produces 3 output files. For example, if there's `foo.castro` then executing `castro foo.castro` creates
- `foo.as` can be executed by `Astrolog` with `-i foo.as`
- `foo.def` has details of allocation
- `foo.map` has a summary of allocation for all files

Use `castro --gui ...` or `castro --console ...` to see how a file is parsed.

When multiple files are compiled together, the `.map` base file name defaults to the first file in the input file list; it is explicitly specifed with the `--mapname=base`.

### Working with multiple files.

Compiling multiple files together is the simplest way to resolve symbolic references between files. Examine [astrotest.d](astrotest.d) which is compiled and then run on astrolog. In that directory do `./run_tests`, or do
```
castro --mapname=testing expressions.castro flow_control.castro test_infra.castro main.castro
astrolog -i expressions.as -i flow_control.as -i test_infra.as -i main.as
```

main.castro is simply
```
run {
    ~1 {
        Switch(test_expressions);
        Switch(test_flow_control);
    }
}
```
and loaded last to make sure all the macro and switch are loaded/defined before use. Examine the `.def` files to see the allocation details per file. The file `testing.map` shows the allocation for all the files, in this excerpt
```
var test_name[2] @102;    // [ALLOC] test_infra.castro
var cond @200;    // [ALLOC] flow_control.castro
```
note that each line has the file name in which the variable is declared.
**Warning**: Astrlog 7.6.0 limits switch address to 48, some tests won't run.
Remove the `layout switch` directives to improve results.

After moving definitions and code around in a file and re-compiling there is no change in the allocated addresses. If nothing is added, removed or renamed, or resized (only variables have a size) then their address does not change no matter the order of their declaration.

### Warnings instead of Errors
Some errors that `castro` reports, may in fact not be errors depending on the targeted version of `Astrolog` or because the "programmer knows what they're doing". There are command line options to treat specified errors as options; try `castro -h`.

##      Castro Language

Probably the trickiest thing when writing castro programs is dealing with `switch` versus `macro`; it's like having two languages. The `switch` format is the familiar `Astrolog command switch file. `switch` and `run` is almost free form input, very little checking, and delcarative; and `macro` is strictly parsed and procedural. `switch` has two mechanisms that embed `macro` like procedures
- `AstroExpression` command switch hooks: `~cmd { ... }`
- `AstroExpression` as a command switch argument `-Xxx {~ ... }`

###     Statement summary
These are the top level statements
- `layout` directives constrain automatic allocation. The three regions are memory/macro/switch. `layout` is optional and, if present, must be before anything else.
- `var` declarations and initialization.
- `macro` definitions result in `~M` `Astrolog`commands.
- `switch` definitions result in `-M0` `Astrolog` commands.
- `run` results in inline top level command switches. Parsed like `switch`, but not embedded in a `-M0`.
- `copy` literally copies text to the output file with no interpretation or changes.

`castro` identifiers are the same as with `"C"`, likewise operators have the same precedence as with `"C"`; blanks and newlines are whitespace.

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
The `macro()` and `switch()` functions take either an identifier, which is a `macro` or `switch` name respectively, or an expression which evaluates to an address. Expressions and function calls are as usual. Note the following
```
var pp;
macro m1 {
    macro(100 + a);
    pp = 100 + a;
    macro(pp);      // This generates an error, the identifier pp is not the name of a macro
    macro(+pp);     // This works because "+pp" is unambiguously an expression
}
```

#####   ? : - QuestColon
In `castro`, `e1 ? e2 : e3` has the same semantics as `if(e1) e2 else e3` (and `"C"`) and only evaluates one of `e2` or `e3`. This is different from `Astrolog`'s `? :` operator which evaluates both `e2` and `e3`. `castro` provides a function `QuestColon(e1, e2, e3)` which has the `Astrolog` semantics.

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
    cprintf "aspect %d, orb %d, 1st string <%s>\n" {~ aspect; orb; &var_strings; }
}
```
All `Astrolog` commands that start with `~`, except `~0`, `_~0`, take an `AstroExpression` as an argument; it is delineated with `{` and `}`. An `AstroExpression` can be used as an argument to a `command switch macro`; it is delineated by `{~` and `}`. `SetString` is used to assign strings. `~2`, `~20`, `~M` commands are not directly supported.

Note that `@12` assigns 12 to the switch address which binds it to **F12**; it is optional. `Astrolog` versions after v7.60 are expected to support `command switch macro` numbers outside the function key range, as it does with the `AstroExpression macro`.

####    castro printf

```
    cprintf <format_string> {~ <arguments> }

    format_string - %d, %i, %f, %g to print a number (they are equivelent).
                    %s to print a string, use its address as the arg.
    arguments     - One AstroExpression per format specifier.
                    Arguments is optional.
    var cprintf_save_area[10]; - Define this for cprintf to save/restore variables.
```
Example: `cprintf "v1 %d, v2 %d" {~ 3 + 4; 7 + 4; }`

`cprintf` optionally does a **save/restore of the variables** it uses. It does this by looking for an array variable named `cprintf_save_area` and using it if found. Recursive use of cprintf will not restore reliably.
```
var cprintf_save_area[10];  // save area for cprintf temps, up to 10.
```

**Warning**: cprintf uses the lower memory locations for the cprintf arguments, up to 10: `%a`, `%b`, `%c`, ..., `%i`, `%j`, by default these are changed. Declare `cprintf_save_area` to avoid interference.

###     run
The contents of a `run` statement are parsed identically to a `switch` statement. The difference is that the switch commands are at the top level of the `.as` file and not embedded in a `-M0`; they are executed when the file is sourced as in `-i file`.

###     copy
The `copy{LITERALLY_COPIED_TO_OUTPUT}` statement literally copies text to the output file with no interpretation or changes; the ultimate hack/workaround. All whitespace, including newlines, is copied as is. Use '\\}' to include a '}' in the output. It's unclear if this is needed, it does provide a way to redefine a macro/switch.

###     Variables

Variable declarations take on one of the following forms
```
var var_name1;            // automatically allocate
var var_name2[4];         // automatically allocate
var var_name3 @100;       // assign variable to address 100
var var_name4[4] @101;    // assign array variable to addresses 101-104
```

#####   Initializing numeric variables

Note that forward references in initialization expressions will use whatever value happens to be there.

```
var var1 {1};       // initialize automantically allocated variable
var var2 @100 {1};  // initialize variable assigned to addess 100
var var_array1[] { a+b, c+d };  // declare and initialize 2 element array
var var_array2[4] { a+b, c+d };  // 4 element array, initialize first two elements
```
Builtin variables are initialized like other variables; but their **address can not be assigned**.


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

##      TODO
- Handle single file, out of normally multi-file, compilation. Uses something like the .map file as input; maybe a `--extern-file` option. Not sure this is an essential feature. May be too confusing; just compile them all.
- Warn if switch/macro used before defined in same file.
```
    run { ~1 { switch(some_switch); }
    switch some_switch { -YYT "Boo\n" }
```
- Generate a `.xref` output file which lists vars with where they are used.
- Handle parsing inside a `switch`/`run` better so fewer words require quoting.


##     Warnings/Oddities:
- `castro` checks for valid `AstroExpression` function names. There is no such check for valid switch commands; if that information becomes available, `castro` will use it.
- Too long switch or macro don't fit in `Astrolog`'s parser; there is no "too large" error. There is often an apparently unrelated error message. Splitting it into two...
- cprintf only works in a `switch`/`run`.
- Some cases where blanks are significant
    - The functions `=Obj` and `=Hou` can cause problems, for example<br>
      `a==Obj(...)` is parsed as `a == Obj(...)`, write `a= =Obj(...)` for assignment.
      `castro` provides `AssignObj(...)` and `AssignHou(...)` as unambiguous alternates.
    - `{~` starts an `AstroExpression` whose value is used as a parameter to a `command switch`,
      for example `=R {~ a+b; }`.
      But `{ ~ a+b; }`, with a space between `{` and `~`, parses as `{ (~a) + b; }`.

- A `switch` is almost free form text, except inside `AstroExpression`s as with<br>
    `~cmd {...}` and `-cmd {~ ... }`. If a command switch argument contains language special characters,<br>
    like the `:` in `-zl "122W19:59" "47N36:35"` quote the word as shown.

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

##      Cheat Sheet

### switch and macro statements

- `macro` defines an `AstroExpression macro` with `~M`.
- `switch` defines a `command switch macro` with `-M0`.
- Quoted string can not have embedded quotes of any type.

And see [Flow Control Statements](#flow-control-statements) used in macro.<br>
The last expression of a macro is the _return_ value.<br>
Use `&arr + expr` because &arr[expr] isn't implemented.

```
macro macroName { aspect = 7; orb = 2; } // returns 2
```

The `~` command switches take an AstroExpression as an argument.
```
switch switchName { ~1 { aspect = 7; orb = 2; } }
```
A regular switch command can take an AstroExpression value as a parameter, use `{~ ... }`.
```
switch switchName { -Ao {~ aspect; } {~ orb; }
```

### variables & layout
[Declare/initialize numeric/string variables](#variables)

`AstroExpression` hook processings can use up to 6 variables: u, v, ..., z.

Varible names are case insensitive.

```
layout memory { base 101; limit 111; reserve 104, 106:108; }
```

Also can specify `layout` for `switch`/`macro`. `limit` is exclusive, all else inclusive

```
var a {123};    // init builtin variable a to 123
var var1 @30;   // declare variable var1 assigned to specific location
var var2[3] {456, 789}; // declare var2 with 3 elements, init first two.
```

### castro functions

See [mazegame ported to castro](examples.d/mazegame.castro) for example usage.

| Function Name | Alias | usage ex | note |
| ------------- | ----- | ---- | -- |
| SwitchAdress  | SAddr | SAddr(switchName) | The address of a switch |
| MacroAdress   | MAddr | MAddr(macroName) | The address of a macro |
| KeyCode       | KeyC | KeyC("a") | "a" is ascii val 97, takes range ' ' - '~' |
| Switch2KeyCode | Sw2KC | Sw2KC(switchName) | see ~XQ hook. arg range 1 - 48 |
| SizeOf       | ----- | SizeOf(varname) | the number of locations used by the variable |

Astrolog associates switch commands at adresses 1 - 48 with function keys
```
// 'a' keypress is mapped to execute func_key_demo
switch func_key_demo { ... }
run { ~XQ { if (z == KeyC("a")) z = Sw2KC(func_key_demo); } }
```

Alternatively

```
run { ~XQ { if (z == KeyC("a")) { Switch(func_key_demo); z = -1; } } }
```

### constants

Integer constants: `201`, `0xc9`, `0b11001001` `0o311`.<br>
Symbolic constants are case insensitive.<br>

#### astrolog constants

Identifiers that start with `M_`, `O_`, `A_`, `H_`, `S_`, `K_`, `W_`, `Z_`
are AstrologConstants.<br>
See [Astrolog Constants](https://github.com/errael/astrology-castro/wiki/astrolog-constants) for the constants in tabular form.

The entire constant name is not needed,
only 3 characters are required after the prefix;
in a few instances more characters are needed to disambiguate.
Except for `K_` and `Z_`, `castro` checks for valid constants;
`K_` and `Z_` are passed through as is to `Astrolog`.

#### castro constants

There are constants for dealing with keyboard input.

See [castro Constants](https://github.com/errael/astrology-castro/wiki/castro-constants) for the constants in tabular form and some examples.


### cprintf
See [cprintf](astrotest.d/cprintf.castro) for example usage.

```
var cprintf_save_area[10];  // save area for cprintf temps, up to 10.

var str;
switch cpr {
    SetString str "a string"
    cprintf "%d %s\n" {~ x + y; &str; }
}
```

### castro help output

See [output of `castro -h`](https://github.com/errael/astrology-castro/wiki/castro-help)
