#
# https://github.com/teverett/antlr4-cpp-example/blob/master/Makefile
#

NAME = Astro

ANTLR_OUTPUT = ../AstroComp/castro/src/main/java/com/raelity/astrolog/castro/antlr
ANTLR4_OPTS = -visitor \
	      -package com.raelity.astrolog.castro.antlr \
	      -o $(ANTLR_OUTPUT)

ANTLR_TEST_OUTPUT = grout
ANTLR4_TEST_OPTS = -visitor \
	      -o $(ANTLR_TEST_OUTPUT)

JAVA_FILES = $(NAME)Parser.java \
	 $(NAME)Lexer.java \
	$(NAME)BaseListener.java \
	$(NAME)Listener.java \
	$(NAME)BaseVisitor.java \
	$(NAME)Visitor.java \

#.PHONY: Astro
$(NAME): $(NAME).g4
	antlr4 $(ANTLR4_TEST_OPTS) $(NAME).g4
	antlr4 $(ANTLR4_OPTS) $(NAME).g4
	for f in $(JAVA_FILES); do \
	    echo >> $(ANTLR_OUTPUT)/$$f; \
	    echo '// vim:set ts=4:' >> $(ANTLR_OUTPUT)/$$f; done 
	cd $(ANTLR_TEST_OUTPUT); javac -cp $(ANTLR_JAR) $(NAME)*.java
	touch $(NAME)

clean:
	rm -f Astro
	cd $(ANTLR_TEST_OUTPUT); rm -f \
		$(NAME) \
		$(JAVA_FILES) \
		$(NAME).interp \
		$(NAME).tokens \
		$(NAME)Lexer.interp \
		$(NAME)Lexer.tokens \
		*.class

# grun Astro program -gui
run: $(NAME)
	cd $(ANTLR_TEST_OUTPUT); grun Astro program -tree

grun: $(NAME)
	cd $(ANTLR_TEST_OUTPUT); grun Astro program -gui


AstroParser.java : Astro.g4
Astro.tokens : Astro.g4
AstroLexer.java : Astro.g4
AstroLexer.tokens : Astro.g4
AstroListener.java : Astro.g4
AstroBaseListener.java : Astro.g4
AstroVisitor.java : Astro.g4
AstroBaseVisitor.java : Astro.g4
