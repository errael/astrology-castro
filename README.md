# castro - v1 beta for 770 with macro functions
`castro` compiles a simple "C" like language into [Astrolog](https://www.astrolog.org) commands and [AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express); `castro` is tailored to `AstroExpression`. `castro` is a standalone tool. It outputs a `.as` file that can be used with `Astrolog`'s command switch `-i <name>.as`. `castro` easily interoperates with existing `Astrolog` command switch files.

Some motivating factors for `castro`
- familiar expression syntax (avoid writing and maintaining the prefix notation expressions),
- referring to things by name rather than address.
- automatic memory/address allocation

**There's a cheat sheet**<br>
For those who like to play around before reading the docs: [cheat sheet](#cheat-sheet).

**Building castro**<br>
See [codegen](codegen).
<!--
See `grammar`/[README](grammar/README.md)
and `codegen`/[README](codegen/README.md).
-->

---

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

castro 0.9.x introduces macro functions. From the previous example the macro can be defined as follows and the years are provided _when the macro is invoked_. **NOTE: There is no stack**; in the example, yearA and yearB are global variables, **beware of recusion**. Macro functions are syntactic sugar.
```
macro progressByYears(yearA, yearB) {           // declares globals yearA/yearB
    cprintf("Progressed aspects between years %d and %d\n", yearA, yearB);
    Switch(progressedAspectsBetweenYearsAB);
}

run { ~1 { progressByYears(1973, 1975); } }
```
This generates the following, use the same `switch progressedAspectsBetweenYearsAB`
```
; MACRO progressByYears
~M 1 'Switch 1'

; RUN 
~1 "Do2 = 27 1973 = 28 1975 Macro 1" 
```


For more examples, there is 
- [mazegame ported to `castro`](examples.d/mazegame/mazegame.castro)
- [expressionAsSwitchCommandParameter.castro](examples.d/expressionAsSwitchCommandParameter/expressionAsSwitchCommandParameter.castro)
  which describes in detail how to use an `AstroExpression` as a switch parameter
  in `castro`.
- [astroExpressionCommandSwitches.castro](examples.d/astroExpressionCommandSwitches/astroExpressionCommandSwitches.castro)
  has the examples from `Astrolog` website under
  [AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express) ported
  to `castro`.
- [astrotest.d](astrotest.d) runs `castro` to generate `*.as` files which
  are then executed on astrolog; it has a simple expect/result infrastructure
- [test.d](test.d) checks lowlevel `castro` functionality and has gold files.
- several of these examples, and more, are in [examples.d](examples.d)

###     Interoperability

`castro` works with existing `command switch` files and their declared switch/macro/variables.

`castro` has a [layout directive](#layout) which constrains automatically
allocated addresses to specified areas. In addition, it is possible to assign
an address to a name; this allows referencing items _defined in an existing
command switch file_ (like an extern); use
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
When the address is specified, it may be a constant expression
```
const xxx_base {33};
const yyy_base {50};
switch sw_01 @xxx_base { ... }      // assign to switch addr 33
switch sw_02 @xxx_base + 1 { ... }  // assign to switch addr 34
macro ma_01 @yyy_base + 7 { ... }   // assign to macro addr 57
```


###      Differences from "C"

In a `switch` or `run` statement, `castro` has a weird looking cprintf output
command, see [castro printf](#castro-printf). And examples
[cprintf.castro](examples.d/cprintf.castro) for a description that compiles and
runs. In a `macro` statement, `cprintf` looks "normal".

####    macro function

Macro functions use globals for function parameters. There is no stack; no recursion.

####    statement/expression differences

- Everything has a value, `if`, `while`, `do`, `for`, `repeat`, `{}`, assignments, expressions as described in the `Astrolog` documentation.
- No semi-colons before `else` or before while in `do while()`.
- No user defined functions, only builtin functions. There are `macro`
  functions, but they are syntactic sugar.
- Not all "C" operators are supported. The following are supported and have
  "C" precedence and semantics
  - unary  ops: `+`, `-`, `!`, `~`, `*`, `&`
  - arithmetic ops: `*`, `/`, `%`, `+`, `-`, `<<`, `>>` `&`, `^`, `|`
  - relational/logical ops: `<`, `<=`, `>`, `>=`, `&&`, `||`
  - assignment ops: `=`, `+=`, `-=`, `*=`, `/=`, `%=`, `<<=`, `>>=`, `&=`, `^=`, `|=`
  - ternary: `?:`
- Address of and indirect, `&var_name`, `&arr_name[expr]` and `*var_name`
  supported; nothing more complex.
- Integer constants are decimal, hex (`0x`), binary(`0b`), octal(`0o`).
- Floating constants are decimal ###.###, exponents not supported.

####    variable differences

- All variables `var` and contants `const` are part of a global namespace;
  there are no local variables.
- Variable names are case insensitive.
- Single char variable names 'a' to 'z' are pre-declared.
  AstroExpression hooks use as much as %u ... %z.
  [castro printf](#castro-printf) uses as much as %a ... %j but there is a
  way to have cprintf automatically preserve variables.
- Variables are declared with `var`, for example `var foo;`
- A variable is integer or float depending on usage. `Astrolog` truncates as needed.
- A variable declaration may assign the variable or array to a specific location; append `@integer`, for example `var foo @100;` and `var bar[10] @200;`; this assigns `foo` to location 100 and array `bar` starts at location 200.

##      Running the `castro` compiler

Requires jre-11 or later. The released jar is executable, use a script named `castro` like
```
#!/bin/sh
CASTRO_JAR=/lib/castro-1.0.0.jar
java -jar $CASTRO_JAR "$@"
```
Do `castro -h` to see [help/usage](https://github.com/errael/astrology-castro/wiki/castro-help).

Running castro like `castro file_name -o -` is convenient to see the compiler output on
the console. The `--fo=min` option might be handy.

Running `castro`  on a file produces 3 output files. For example, if there's `foo.castro` then executing `castro foo.castro` creates
- `foo.as` can be executed with `astrolog -i foo.as`
- `foo.def` has details of allocation
- `foo.map` has a summary of allocation;
   includes the file and line number where each item is defined

Use `castro --gui ...` or `castro --console ...` to see how a file is parsed.

When multiple files are compiled together, the `.map` base file name defaults to the first file in the input file list; use `--mapname=base` explicitly set the map file name base.

### Working with multiple files.

Compiling multiple files together resolves symbolic references between files.
This example is available in [castroCompileExample](examples.d/castroCompileExample)
which is compiled and then run on astrolog by doing
```
castro --mapname test file1.castro file2.castro main.castro
astrolog -i test.helper.as
```
Then press the function keys, `F1` through `F4`. Notice how `F4` increments a number displayed when
`F2` or `F3` is pressed.

And see [helper.castro](#the-helpercastro-file) to understand the
`test.helper.as` file in the above example.

This example shows how files compiled together reference items in each other.
Load order is important; the file, `main.castro`, is loaded last.
As `main.castro` is loaded, it invokes a macro function 
(using the `run` statement) loaded in an earlier file.
`main.castro` also initializes a variable declared in `file2.castro`.

Examine the `.def` files to see the allocation details per file.
The file `test.map` shows the allocation for all the files, in this excerpt
```
var cprintf_save_area[10] @32; // [ALLOC] test.helper.castro:1
var f1m_param1            @27; // [ALLOC] file1.castro:36
var second_arg            @30; // [ALLOC] file2.castro:5
```
note that each line has the file name in which the variable is declared.

### The `helper.castro` file

To support `cprintf()`, `print()` or string assignment in a `macro`, some helper
switch statements are automatically generated and placed in a
`helper.castro` file; the basename of the file is the same as the map file
basename. When compilation completes there is a `basename.helper.as` file.

The `helper.castro` file may have 4 types of statements
- load compiled files in order, e.g. `-i file1 -i file2`<br/>
  This is not present if the `--nohelperload` option is used.<br/>
  **The path, if any, as specified on the command line is used for loading**.
- declare `cprintf_save_area`<br/>
  `var cprintf_save_area[10]; // cprintf save/restore up to 10 variables.`<br/>
  This is not present if already defined or if there are no output statements.
- string assignment switch statements for macros<br/>
  When something like `some_var = "string value;"`.
- print switch statements for macros<br/>
  When something like `cprintf("FOO");"`.

And note that, if not specified, a file's _layout restrictions are inherited_
from the first file on the command line. This means that the `helper.castro`
file has the **same layout restrictions as the first file**.

#### Examples/options for `helper.castro` file

For example, with `foo.castro` and `bar.castro` do
```
castro foo.castro bar.castro
astrolog -i foo.helper.as
```
The basename of the `helper.castro` file is same as the `map` file, so
```
castro --mapname=test foo.castro bar.castro
astrolog -i test.helper.as
```
And there's a `--helpername=some_name` option.

In the previous examples there is no path associated with the files.
If `astrolog` is executed from a different
directory, use the `astrolog` `-Yi` option.
```
astrolog -Yi0 /full/path/to/compilation/directory -i test.helper.as
```

Alternatively, a path can be specified when the files are compiled
```
castro --mapname=test /full/path/foo.castro /full/path/bar.castro
# then from a different directory do
astrolog -i /full/path/test.helper.as
```

If the `--nohelperload` option is used, and there are `macro` output statements, then
```
castro --nohelperload foo.castro bar.castro
astrolog -i foo.helper.as -i foo.as -i bar.as
```

Note that when there is no `macro` output or string assignment
and the `--nohelperload` is used, then
a `helper.castro` file is **not generated**.
```
castro --nohelperload foo.castro bar.castro
astrolog -i foo.as -i bar.as
```


### Warnings instead of Errors
Some errors that `castro` reports, may in fact not be errors depending on the
targeted version of `Astrolog` or because the "programmer knows what they're
doing". There are command line options to treat specified errors as options; try
`castro -h`.

##      Castro Language

Probably the trickiest thing when writing castro programs is dealing with
`switch` versus `macro`; it's like having two languages. The `switch` format is
the familiar `Astrolog` command switch file. `switch` and `run` take almost free
form input, very little checking, and delcarative; and `macro` is strictly
parsed and procedural. `switch` has two mechanisms that embed `macro` like
procedures
- `AstroExpression` command switch hooks: `~cmd { ... }`
- `AstroExpression` as a command switch argument `-Xxx {~ ... }`

###     Statement summary
These are the top level statements
- `const` declares a constant.
- `layout` directives constrain automatic allocation. The three spaces/regions
  are `memory`/`macro`/`switch`. `layout` is optional and, if present, must be
  before anything else (except `const` declarations).
- `var` declarations and initialization.
- `macro` definitions result in `~M` `Astrolog`commands.
- `switch` definitions result in `-M0` `Astrolog` commands.
- `run` results in inline top level command switches. Parsed like `switch`, but
  not embedded in a `-M0`.
- `copy` literally copies text to the output file with no interpretation or changes.

`castro` identifiers are the same as with `"C"`, likewise operators have the
same precedence as with `"C"`; blanks and newlines are whitespace.

###     layout
`layout` statement specifies, on a per file basis, the addresses that automatic allocation can use for `memory`, `macro`, and `switch` addresses.
```
layout memory {
    base 101;
    limit 111;                  // 111 exclusive
    reserve 104, 106:108;       // 108 inclusive
}
```
The values in layout are specified with constant expressions.

This directive allows allocation of addresses between 101 inclusive and 111 exclusive; but not addresses 104, 106, 107, 108. If an _out of memory_ error occurs look at the `.def` output file for more information.

The layout from the first file may be inherited. **If a file does not specify
a layout, the layout from the first file is inherited**.

**Note:** For `Astrolog` 770 the switch base is always at least 49; that's after the function key
slots.

###     macro
The `macro` statement defines an `AstroExpression macro` using `~M`; it contains
expressions with function calls. See
[AstroExpressions](https://www.astrolog.org/ftp/astrolog.htm#express); there are
a wide variety of function calls. The value of `Macro(some_macro)`, or
`some_macro()`if it is macro function, is the value of the last statement/expr
in `some_macro` as defined by `Astrolog`.

A simple macro definition looks like
```
macro macName { ... }
```
A macro function definition looks like
```
macro macFunName(...) { ... }
```

A macro function definition is indicated by `()` after the macro name. There are zero or more parameters within the '()'. **Each parameter becomes a named global**. Macro function calls may nest only if the macro has one parameter. Different macros may nest. For example.
```
macro macFun1(macFun1_arg) { ... }
macro macFun2(macFun2_arg1, macFun2_arg2) { ... }
macro macFun2B(macFun2B_arg1, macFun2B_arg2) { ... }
run { ~1 {
    macFun1(manFun1(x)); // OK - can nest if only one parameter
    macFun2(manFun2(x, y), x); // ERROR - macros with two parameters can not nest
    macFun2(manFun2B(x, y), manFun2B(u, v)); // OK - different macros
} }
```

####   Flow Control Statements
- `if (`_expr_`)` _expr_
- `if (`_expr_`)` _expr_ `else` _expr_
- `repeat (`_expr_`)` _expr_
- `while (`_expr_`)` _expr_
- `do` _expr_ `while (`_expr_`)`
- `for(` _var_ `=` _expr_ `;` _expr_ `)` _expr_
- _expr_ `?` _expr_ `:` _expr_
- `{` _one or more expr, each terminated by a semi-colon_ `}`

Note that everything is an expression, including the flow control statements themselves.

####   Macro() and Switch() functions
The `macro()` and `switch()` functions take either an identifier, which is a `macro` or `switch` name respectively, or an expression which evaluates to an address. Expressions and function calls are as usual. Note the following
```
var pp;
macro m1 {
    macro(100 + a); // Invoke the macro with address "100 + a"
    pp = 100 + a;
    macro(pp);      // This generates an error, the identifier pp is not the name of a macro
    macro(+pp);     // This works because "+pp" is unambiguously an expression
}
```

####   ? : - QuestColon
In `castro`, `e1 ? e2 : e3` has the same semantics as `if(e1) e2 else e3` (and
`"C"`) and only evaluates one of `e2` or `e3`. This is different from
`Astrolog`'s `? :` operator which evaluates both `e2` and `e3`. `castro`
provides a function `QuestColon(e1, e2, e3)` which has the `Astrolog` semantics.

###     switch
The `switch` statement generates a `command switch macro` using `-M0`; it
contains `Astrolog command switch`es and their arguments.
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
All `Astrolog` commands that start with `~`, except `~0`, `_~0`, take an
`AstroExpression` as an argument; it is delineated with `{` and `}`. An
`AstroExpression` can be used as an argument to a `command switch macro`; it is
delineated by `{~` and `}`. `SetString` is used to assign strings. `~2`, `~20`,
`~M` commands are not directly supported.

Note that `@12` assigns 12 to the switch's address which binds it to **F12**;
see [Function key slots](https://github.com/errael/astrology-castro/wiki/castro-constants#function-key-slots).
If a switch address is not assigned, it will be allocated; use [layout](layout)
to specify the allocation range. `Astrolog` versions _after_ 760 support
`command switch macro` numbers outside of the function key range.

###    castro printf

Both the functions `cprintf` and `printf` are available. The difference is what
`Astrolog` switch command is used.

| `castro` function | `astrolog` command | note |
| :--------: | :-----: | ---- |
| `cprintf`  | `-YYT`  | Popup formatted text string in current context.
| `printf`   | `-YYt`  | Output formatted text string in current context.

There are two forms of each; one for switch and one for macro.

The macro form is available since `Astrolog` 770. The macro form uses helper
switch statements to do the actual output;
see [helper.castro](#the-helpercastro-file) for details.

#### switch printf
```
    cprintf "format_string" {~ expr1; expr2; ... }

    format_string - %d, %i, %f, %g to print a number (they are equivelent).
                    %s to print a string, use its address as the arg.
    arguments     - One AstroExpression per format specifier.
                    Arguments, {~ ... }, are optional.
```
Example: `cprintf "v1 %d, v2 %d" {~ 3 + 4; 7 + 4; }`

#### macro printf

The macro form is like a varargs function call.
The parameters are as described for switch form.
```
    cprintf("format_string", expr1, expr2, ...);
```
Example: `cprintf("v1 %d, v2 %d", 3 + 4, 7 + 4)`

#### printf variable usage

`cprintf` optionally does a **save/restore of the variables** it uses. It does
this by looking for an array variable named `cprintf_save_area` and using it if
found. It is automatically defined, if needed, in the
[helper.castro](#the-helpercastro-file) file.

 _Nested use of cprintf will not restore reliably_.

**Warning**: cprintf uses the lower memory locations for the cprintf arguments,
up to 10: `%a`, `%b`, `%c`, ..., `%i`, `%j`, by default these are changed.
Use the `helper.file` or declare `cprintf_save_area` to avoid them
getting trashed.

###     run
The contents of a `run` statement are parsed identically to a `switch` statement. The difference is that the `run`'s switch commands are at the top level of the `.as` file and not embedded in a `-M0`; they are executed when the file is sourced as in `-i file`.

**Note:** A `switch` or `macro` must already be defined when invoked from a `run` statement; if not, undefined behavior.

###     copy
The `copy{LITERALLY_COPIED_TO_OUTPUT}` statement literally copies text to the output file with no interpretation or changes; the ultimate hack/workaround. All whitespace, including newlines, is copied as is. Use '\\}' to include a '}' in the output. This is needed because some things don't parse correctly, and I can be lazy, for example

```
copy { -zl 121W57'26.9 37N17'28.2 ; Default location      [longitude and latitude] }
```

In a run statement, the `-zl` params do not parse correctly.

It also provide a way to redefine a macro/switch.

###     Constants

All contants are part of the global namespace. So, for example, configuration constants can be defined in one file, and used in other files. Constants can often times be used before they are defined; exceptions are for the size of an array or to specify an address assignment using `@`.

Constants are defined like `const <name> {<expr>};` where `<expr>` is an expression made up only of integer constants. Program wide constants can be defined in a single file; and putting that file first in compilation order avoids some issues. Constants take up no `AstroExpression` VM storage, they exist only in the `castro` compiler.

```
const const_name1 {10};
const const_name2 {const_name1 + 23};
```

###     Variables

All variables are part of the global namespace, see [Constants](#Constants).

Variable declarations take on one of the following forms
```
var var_name1;            // automatically allocate
var var_name2[4];         // automatically allocate
var var_name3 @100;       // assign variable to address 100
var var_name4[4] @101;    // assign array variable to addresses 101-104

const some_size {5};
const addr_base {110};
var var_name5 @addr_base;
var var_name6 @addr_base + 1;   // address can be a constant expression

// both size and address can be a constant expression
var var_name7[some_size+3] @addr_base + 2;
```

####   Initializing numeric variables

Note that forward references in initialization expressions will use whatever value happens to be there.

```
var var1 {1};       // initialize automantically allocated variable
var var2 @100 {1};  // initialize variable assigned to addess 100
var var_array1[] { a+b, c+d };  // declare and initialize 2 element array
var var_array2[4] { a+b, c+d };  // 4 element array, initialize first two elements
```
Builtin variables are initialized like other variables; but their **address can not be assigned**.


####   Initializing string variables

Initialize variables with strings in variable declarations like:
```
var some_string { "string" };
var some_strings[] { "string1", "string2", "string3" };
```
#####   Macro string initialization

Assign string programatically in `macro {...}` like:
```
var var0;
macro someMacro {
    var0 = "string";
}
```

#####   Switch string initialization

Set strings programatically in `switch {...}` or `run {...}` like:
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

#### The same variable can reference both a number and a string

Given this file: **share.castro**
```
var share[] { 0, 1, 2 };
run {
    SetString share[0] "zero" "one" "two"
    // share[1] references both a string and a number
    cprintf "share[1]: %s - %d\n" {~ &share[1]; share[1]; }
}
```
Compile and run it:
```
$ castro share.castro
$ astrolog -i share.as
```
And see the output:
```
share[1]: one - 1
```

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
var some_strings[] { "string1", "string2", "string3" };
```

### castro functions

Some functions are part of the `castro` language. They are treated as constants.

See [mazegame ported to castro](examples.d/mazegame.castro) for example usage.

| Function Name | Alias | usage ex | note |
| ------------- | ----- | ---- | -- |
| SwitchAdress  | SAddr | SAddr(switchName) | The address of a switch |
| MacroAdress   | MAddr | MAddr(macroName) | The address of a macro |
| KeyCode       | KeyC | KeyC("a") | "a" is ascii val 97, takes range ' ' - '~' |
| Switch2KeyCode | Sw2KC | Sw2KC(switchName) | see ~XQ hook. arg range 1 - 48 |
| SizeOf       | ----- | SizeOf(varname) | the number of locations used by the variable |

By default `Astrolog` associates switch commands at adresses 1 - 48 with function keys
```
// 'a' keypress is mapped to execute func_key_demo at slot for Shift-F1
switch func_key_demo @S_FK_F0 + 1 { ... }
run { ~XQ { if (z == KeyC("a")) z = Sw2KC(func_key_demo); } }
```

Alternatively

```
run { ~XQ { if (z == KeyC("a")) { Switch(func_key_demo); z = KeyCode(' '); } } }
```

### constants

Integer constants: `201`, `0xc9`, `0b11001001` `0o311`.<br>
Symbolic constants are case insensitive.<br>

#### user constants

User constants are define as `const const_name {~10 + 1};`.

#### astrolog constants

**Identifiers that start with `M_`, `O_`, `A_`, `H_`, `S_`, `K_`, `W_`, `Z_`
are AstrologConstants.**<br>
See [Astrolog Constants](https://github.com/errael/astrology-castro/wiki/astrolog-constants) for the constants in tabular form.

The entire constant name is not needed,
only 3 characters are required after the prefix;
in a few instances more characters are needed to disambiguate.
Except for `K_` and `Z_`, `castro` checks for valid constants;
`K_` and `Z_` are passed through as is to `Astrolog`.

#### castro constants

There are constants for dealing with keyboard input.

See [castro Constants](https://github.com/errael/astrology-castro/wiki/castro-constants) for the constants in tabular form and some examples.


### cprintf & printf

See [castro printf](#castro-printf) for details.

See [cprintf](astrotest.d/cprintf.castro) for example usage.

For a file, **testprint.castro**
```
var cprintf_save_area[10];  // save area for cprintf temps, up to 10.

var str;
switch cpr {
    SetString str "a string"
    cprintf "%d %s\n" {~ x + y; &str; }
}

macro mcpr {
    str = "different string";
    cprintf("%d %s\n", x + y, &str) }
}
```
and run it like
```
astrolog -i testprint.helper.as -i testprint.as
```
Note that `testprint.helper.as` must be included before anything it contains is
executed. The helper file contains no direct execution commands. For example,
it can be last as long as none of it's helper switch statements are executed,
directly or indirectly, from the top level, through `run` or `copy` in any
`*.as` file. _Safest to always have it first_.
See [helper.castro](#the-helpercastro-file) for details.

### castro help output

See [output of `castro -h`](https://github.com/errael/astrology-castro/wiki/castro-help)


##      TODO
- Handle single file, out of normally multi-file, compilation. Uses something like the .map file as input; maybe a `--extern-file` option. Not sure this is an essential feature. May be too confusing; just compile them all.
- Warn if switch/macro used before defined in same file.
```
    run { ~1 { switch(some_switch); } }
    switch some_switch { -YYT "Boo\n" }
```
- Generate a `.xref` output file which lists vars with where they are used.
- Handle parsing inside a `switch`/`run` better so fewer words require quoting.
- After pass1 build a dependency graph for undefined constants, try to resolve.
- Implement some stack functions in a user library.
- Implement stack frames and local variables and recursion.

