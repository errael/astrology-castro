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

# JAVA_FILES = $(NAME)Parser.java \
# 	 $(NAME)Lexer.java \
# 	$(NAME)BaseListener.java \
# 	$(NAME)Listener.java \
# 	$(NAME)BaseVisitor.java \
# 	$(NAME)Visitor.java \

JAVA_FILES = \
	$(NAME)Lexer.java \
	$(NAME)ParserBaseListener.java \
	$(NAME)ParserBaseVisitor.java \
	$(NAME)Parser.java \
	$(NAME)ParserListener.java \
	$(NAME)ParserVisitor.java


#.PHONY: Astro
$(NAME): $(NAME)Parser.g4 $(NAME)Lexer.g4
	antlr4 $(ANTLR4_TEST_OPTS) $(NAME)Lexer.g4
	antlr4 $(ANTLR4_TEST_OPTS) $(NAME)Parser.g4
	antlr4 $(ANTLR4_OPTS) $(NAME)Lexer.g4
	antlr4 $(ANTLR4_OPTS) $(NAME)Parser.g4
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
		$(NAME)Lexer.interp \
		$(NAME)Lexer.tokens \
		$(NAME)Parser.interp \
		$(NAME)Parser.tokens \
		*.class

# grun Astro program -gui
run: $(NAME)
	cd $(ANTLR_TEST_OUTPUT); grun Astro program -tree -tokens

grun: $(NAME)
	cd $(ANTLR_TEST_OUTPUT); grun Astro program -gui

# This is convenient to have here,
# but note that it uses "castro" which is not built as part of this Makefile.
test: $(NAME)
	castro --test test.d/test-in test.d/test-out
	diff test.d/test-gold test.d/test-out
