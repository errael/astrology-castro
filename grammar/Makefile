#
# https://github.com/teverett/antlr4-cpp-example/blob/master/Makefile
#

NAME = Astro

ANTLR_OUTPUT = grout
ANTLR4_OPTS = -visitor \
	      -o $(ANTLR_OUTPUT)

	      # ../castro/lib/src/com/raelity/astrolog/castro
	      # -package com.raelity.astrolog.castro

#.PHONY: Astro
$(NAME): $(NAME).g4
	antlr4 $(ANTLR4_OPTS) $(NAME).g4
	cd $(ANTLR_OUTPUT); javac -cp $(ANTLR_JAR) $(NAME)*.java
	touch $(NAME)

clean:
	rm -f Astro
	cd $(ANTLR_OUTPUT); rm -f \
		$(NAME) \
		$(NAME)Parser.java \
		$(NAME).interp $(NAME).tokens \
		$(NAME)Lexer.java $(NAME)Lexer.interp $(NAME)Lexer.tokens \
		$(NAME)BaseListener.java $(NAME)Listener.java \
		$(NAME)BaseVisitor.java $(NAME)Visitor.java \
		*.class

# grun Astro program -gui
run: $(NAME)
	cd $(ANTLR_OUTPUT); grun Astro program -tree

grun: $(NAME)
	cd $(ANTLR_OUTPUT); grun Astro program -gui


AstroParser.java : Astro.g4
Astro.tokens : Astro.g4
AstroLexer.java : Astro.g4
AstroLexer.tokens : Astro.g4
AstroListener.java : Astro.g4
AstroBaseListener.java : Astro.g4
AstroVisitor.java : Astro.g4
AstroBaseVisitor.java : Astro.g4
