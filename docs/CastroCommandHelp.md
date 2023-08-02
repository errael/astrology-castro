```
$ castro -h
Usage: castro [-h] [several-options] [-o outfile] infile+
    infile may be '-' for stdin.
    if outfile not specified, it is derived from infile.
    -o outfile      allowed if exactly one infile, '-' is stdout
    --mapname=mapname     Map file name is <mapname>.map.
                            Default is derived from first infile.
    --Ewarn=ename   Make the specified error a warning.
                    Default: warn for func-castro and var-rsv.
                    Can do no-ename to turn warning to error.
                    Use --Ewarn=junk for a list.
    --formatoutput=opt1,... # comma separated list of:
            min             - no extra/blank lines
            qflip           - quote flip default inner/outer
            bslash          - split into new-line/backslash lines
            nl              - split into lines
            indent          - indent lines
            run_nl          - split into lines
            run_indent      - indent lines
            debug           - precede macro output with original text
        Default is no options; switch/macro/run on a single line
        which is compatible with all Astrolog versions.
        "min"/"qflip" usable with any Astrolog version.
    --anonymous     no dates/versions in output files (for test golden)
    --version       version
    -h      output this message

    The following options are primarily for debug. --gui is also fun to see
    and may provide insight. It shows how the program is parsed. Only uses
    the first file and does not generate any compilation output files.
    --gui           show AST in GUI
    --console       show the AST in the console
    --test  output prefix parse data
    -v      output more info

Errors that can be made warnings
    func-unk     unknown function
    func-narg    wrong number of function arguments
    func-castro  function used internally for code generation
    var-rsv      assign variable to reserved area
    array-oob    array index out of bounds
    octal-const  octal constant
    inner-quote  inner quote in string stripped
    const-ambig  constant id is ambiguous
```
