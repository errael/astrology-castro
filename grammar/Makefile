#
# https://github.com/teverett/antlr4-cpp-example/blob/master/Makefile
#

NAME = Astro

#.PHONY: Astro
$(NAME): $(NAME).g4
	antlr4 $(NAME).g4
	javac -cp $(ANTLR_JAR) $(NAME)*.java
	touch $(NAME)

clean:
	rm -f \
		$(NAME)Parser.java \
		$(NAME).interp $(NAME).tokens \
		$(NAME)Lexer.java $(NAME)Lexer.interp $(NAME)Lexer.tokens \
		$(NAME)BaseListener.java $(NAME)Listener.java \
		*.class

# grun Astro program -gui
run: $(NAME)
	grun Astro program -tree

grun: $(NAME)
	grun Astro program -gui
