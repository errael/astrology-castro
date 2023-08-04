
There is a Makefile in the `grammer` directory. It runs `antlr4` and
copies its output files to `codegen`.

The Makefile is simple. It uses an environment variable `ANTLR_JAR`,
which is setup something like:
```
export ANTLR_JAR=/<somewhere>/antlr-4.13.0-complete.jar
```

In addition to building the files for `grammar`, it also makes a local copy
of the output used only for grammar debug. The Makefile has targets

- run - parse tree and tokens output to console
- grun - display parse tree

These use a shell command named `grun` which looks like
```
#!/bin/sh

java -Xmx500M -cp .:$ANTLR_JAR org.antlr.v4.gui.TestRig "$@"
```

