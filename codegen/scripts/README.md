
The scripts in this directory extract data structures from `Astrolog` source.

This only needs to be done once when there's a new `Astrolog` release.
Pathnames are configured in the file `COMMON`.

### EXTRACT FUNCTIONS

Extract information about the `Astrolog` functions.
When the script is run, the target file
location is output to stderr.

```
./extract_functions > xxx
diff xxx <target>   # see what changed
cp xxx <target>
```

### EXTRACT CONSTANTS

There are two scripts to extract information
about `Astrolog` constants from the source.

- extract_constants<br>
    Using awk, pull out the specified arrays.
- clean_table_pairs<br>
    Change `{"xxx", id}` to `"xxx" /*id*/`

```
./extract_constants | ./clean_table_pairs > xxx
diff xxx <target>   # see what changed
cp xxx <target>
```

### EXTRACT WINDOWS KEYCODES

Extract a mapping of character&modifiers to windows key code.

./extract_windows_keycodes
