#
# https://github.com/teverett/antlr4-cpp-example/blob/master/Makefile
#

ANTLR_OUTPUT = ../codegen/castro/src/main/java/com/raelity/astrolog/castro/antlr
ANTLR4_OPTS = -visitor \
	      -package com.raelity.astrolog.castro.antlr \
	      -o $(ANTLR_OUTPUT)

ANTLR_TEST_OUTPUT = grout
ANTLR4_TEST_OPTS = -visitor \
	      -o $(ANTLR_TEST_OUTPUT)

JAVA_FILES = \
	AstroLexer.java \
	AstroParserBaseListener.java \
	AstroParserBaseVisitor.java \
	AstroParser.java \
	AstroParserListener.java \
	AstroParserVisitor.java


#.PHONY: Astro
Astro: AstroParser.g4 AstroLexer.g4
	antlr4 $(ANTLR4_TEST_OPTS) AstroLexer.g4
	antlr4 $(ANTLR4_TEST_OPTS) AstroParser.g4
	antlr4 $(ANTLR4_OPTS) AstroLexer.g4
	antlr4 $(ANTLR4_OPTS) AstroParser.g4
	for f in $(JAVA_FILES); do \
	    echo >> $(ANTLR_OUTPUT)/$$f; \
	    echo '// vim:set ts=4:' >> $(ANTLR_OUTPUT)/$$f; done 
	cd $(ANTLR_TEST_OUTPUT); javac -cp $(ANTLR_JAR) Astro*.java
	touch Astro

clean:
	rm -f Astro
	cd $(ANTLR_TEST_OUTPUT); rm -f \
		Astro \
		$(JAVA_FILES) \
		AstroLexer.interp \
		AstroLexer.tokens \
		AstroParser.interp \
		AstroParser.tokens \
		*.class

# grun Astro program -gui
run: Astro
	cd $(ANTLR_TEST_OUTPUT); grun Astro program -tree -tokens

grun: Astro
	cd $(ANTLR_TEST_OUTPUT); grun Astro program -gui

# This is convenient to have here,
# but note that it uses "castro" which is not built as part of this Makefile.
test: Astro
	castro --test ../test.d/expr.castro
	diff ../test.d/gold/expr.test-gold ../test.d/expr.test
