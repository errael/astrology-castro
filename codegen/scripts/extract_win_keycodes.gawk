#
# Process two files
#       resource.h to build id_map
#       astrolog.rc to build keycode_map
# Generate code to create map of key&modifiers to windows key-code
#

BEGIN {
    build_id_map = 1
    # The [:space:] includes "\r" (useful if DOS)
    FS = "[[:space:],]+"
}

BEGINFILE {
    #print "switching: " FILENAME
    if (FILENAME ~ /astrolog\.rc/) {
        #print "flip field separator: " FILENAME
        FS = ","
        build_id_map = 0
    }
}

build_id_map && $1 == "#define" {
    id_map[$2] = $3
}

/^accelerator ACCELERATORS/ {
    doing_keys = 1
}

doing_keys == 1 && NF >= 3 {
    # for each field remove preceding and trailing space
    for(i = 1; i <= NF; i++) gsub("^[[:space:]]+|[[:space:]]+$", "", $i)

    modifiers = ""
    for(i = 3; i <= NF; i++) {
        if ($i == "NOINVERT")
            continue
        modifiers = modifiers $i ", "
    }
    # remove the trailing ", "
    modifiers = substr(modifiers, 1, length(modifiers) - 2)

    # make sure every key is quoted
    key = $1
    if (substr(key, 1, 1) != "\"")
        key = "\"" key "\""

    printf "    codes.put(Stroke.get(%s, EnumSet.of(%s)), %s);\n",
           tolower(key), modifiers, id_map[$2]
}

doing_keys && /^END/ {
    exit 0
}
