package plc.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.math.BigInteger;
import java.math.BigDecimal;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling those functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        //parse source ::= field* method*
        List<Ast.Field> field = new ArrayList<>();
        List<Ast.Method> method = new ArrayList<>();

        //parse 0 or more fields
        while(peek("LET")) {
            field.add(parseField());
        }

        //parse 0 or more methods
        while(peek("DEF")) {
            method.add(parseMethod());
        }

        //return the parsed source structure
        return new Ast.Source(field, method);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    //field ::= 'LET' 'CONST'? identifier ('=' expression)? ';'
    //MODIFIED: field ::= 'LET' 'CONST'? identifier ':' identifier ('=' expression)?
    public Ast.Field parseField() throws ParseException {
        //first token in a field must be keyword LET
        if(!match("LET")){
            throw new ParseException("missing the word LET", tokens.get(0).getIndex());
        }
//        else {
//            match(("LET")); // advance char stream
//        }

        //optional keyword const
        boolean optionalConst= match("CONST");

        //make sure followed by an identifier
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing the identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        //capture the identifier token
        String identifier = tokens.get(0).getLiteral();
        tokens.advance();

        //modified parser
        if (!match(":")) {
            throw new ParseException("missing :", tokens.get(0).getIndex());
        }
//        else{
//            match(":");
//        }
        //make sure followed by an identifier
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing 2nd identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        //capture the 2nd identifier token
        String type = tokens.get(0).getLiteral();
        tokens.advance();

        //check ('=' expression)?
        Optional<Ast.Expression> optExpression = Optional.empty();
        if (match("=")) {
            optExpression = Optional.of(parseExpression()); //parse the following expression
        }

        //make sure ends with a semicolon!
        if(!match(";")) {
            throw new ParseException("Invalid ; here", tokens.get(0).getIndex());
        }

        //return the parsed field :)
        return new Ast.Field(identifier, type, optionalConst, optExpression);
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    //method ::= 'DEF' identifier '(' (identifier (',' identifier)*)? ')' 'DO' statement* 'END'
    //MODIFIED: method ::= 'DEF' identifier '(' (identifier ':' identifier (',' identifier ':' identifier)*)? ')' (':' identifier)? 'DO' statement* 'END'
    public Ast.Method parseMethod() throws ParseException {
        //ensure starts with word DEF
        if(!match("DEF")){
            throw new ParseException("missing the word DEF", tokens.get(0).getIndex());
        }

        //make sure followed by an identifier
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing the identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }

        //capture the identifier token
        String identifier = tokens.get(0).getLiteral();
        tokens.advance();

        //parse '(' (identifier ':' identifier (',' identifier ':' identifier)*)? ')'
        List<String> methodParam = new ArrayList<>();
        List<String> methodParamTypes = new ArrayList<>();

        if (!match("(")) {
            throw new ParseException("Missing the opening (", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        if (!match(")")) {
            //check if no param, aka empty ()
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Missing identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }

            //add first identifier param
            methodParam.add(tokens.get(0).getLiteral());
            tokens.advance();

            //consume ':'
            if (!match(":")) {
                throw new ParseException("missing ':'", tokens.get(0).getIndex());
            }

            //consume 2nd identifier
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Missing 2nd identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            methodParamTypes.add(tokens.get(0).getLiteral());
            tokens.advance();

            //iterate (',' identifier)*
            //NOW (',' identifier ':' identifier)*
            while (match(",")) {
                if (!peek(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Missing identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                methodParam.add(tokens.get(0).getLiteral());
                tokens.advance();

                //consume ':'
                if (!match(":")) {
                    throw new ParseException("missing ':'", tokens.get(0).getIndex());
                }

                //consume 2nd identifier aka type
                if (!peek(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Missing 2nd identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                methodParamTypes.add(tokens.get(0).getLiteral());
                tokens.advance();

            }
            //make sure closing ')'
            if(!match(")")) { //if no more char, avoid out of bounds
                throw new ParseException("Missing the closing )", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }

        //modified (':' identifier)?
        Optional<String> optionalType = Optional.empty();
        if (match(":")) {
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("invalid token not an identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            optionalType = Optional.of(tokens.get(0).getLiteral());
            tokens.advance();
        }

        //make sure followed by DO
        if (!match("DO")) {
            throw new ParseException("Missing the keyword DO ", tokens.get(0).getIndex());
        }

        //followed by 0 or more statements
        List<Ast.Statement> statements = new ArrayList<>();
        while (!peek("END")) {
            statements.add(parseStatement());
        }
        if (!match("END")) {
            throw new ParseException("Missing keyword 'END'", tokens.get(0).getIndex());
        }

        //return the modified parsed method call
        return new Ast.Method(identifier, methodParam, methodParamTypes, optionalType, statements);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, for, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
        //based on the curr token, call the appropriate statement methods
        if (peek("LET")) {
            return parseDeclarationStatement();
        }
        else if (peek("IF")) {
            return parseIfStatement();
        }
        else if (peek("FOR")) {
            return parseForStatement();
        }
        else if (peek("WHILE")) {
            return parseWhileStatement();
        }
        else if (peek("RETURN")) {
            return parseReturnStatement();
        }
        else{ //else we're parsing expression('=' expression)?;
            Ast.Expression firstExp = parseExpression();
            //check ('=' expression)?;
            if (match("=")) {
                Ast.Expression secondExp = parseExpression();
                if(!tokens.has(0)) { //if no more char, avoid out of bounds
                    throw new ParseException("Missing last char", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                if (!match(";")) {
                    throw new ParseException("Missing a ';'", tokens.get(0).getIndex());
                }
                return new Ast.Statement.Assignment(firstExp, secondExp);
            }
            //else just one expression
            else {
                if(!tokens.has(0)) { //if no more char, avoid out of bounds
                    throw new ParseException("Missing last char", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                if (!match(";")) {
                    throw new ParseException("Missing a ';'", tokens.get(0).getIndex());
                }
                return new Ast.Statement.Expression(firstExp);
            }
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    //'LET' identifier ('=' expression)? ';'
    //MODIFIED: statement ::= 'LET' identifier (':' identifier)? ('=' expression)? ';'
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        //inspired by parseField
        match("LET");
        //make sure followed by an identifier
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Missing the identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }

        //capture the identifier token
        String identifier = tokens.get(0).getLiteral();
        tokens.advance();

        //modified (':' identifier)?
        Optional<String> optionalType = Optional.empty();
        if (match(":")) {
            if (!peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("invalid token not an identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            optionalType = Optional.of(tokens.get(0).getLiteral());
            tokens.advance();
        }

        //check ('=' expression)?
        Optional<Ast.Expression> optExpression = Optional.empty();
        if (match("=")) {
            optExpression = Optional.of(parseExpression()); //parse the following expression
        }
        //make sure ends with a semicolon!
        if(!tokens.has(0)){
            throw new ParseException("missing a ;", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        if(!match(";")) {
            throw new ParseException("Invalid ;", tokens.get(0).getIndex());
        }

        return new Ast.Statement.Declaration(identifier, optionalType, optExpression);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    //do in PART 2
    //  'IF' expression 'DO' statement* ('ELSE' statement*)? 'END'
    public Ast.Statement.If parseIfStatement() throws ParseException {
        // get the condition
        //ensure starts with word IF
        if(!match("IF")) {
            throw new ParseException("missing the word IF", tokens.get(0).getIndex());
        }
        // must be followed by an expression
        Ast.Expression condition = parseExpression(); //parse the following expression

        //check for do keyword
        if (!match("DO")) {
            throw new ParseException("Missing the keyword DO", tokens.get(0).getIndex());
        }
        // get the then statements
        List<Ast.Statement>  thenStatements = new ArrayList<>();
        List<Ast.Statement>  elseStatements = new ArrayList<>();
        boolean elsePresent = false;
        while (!peek("END")) {
            if (match("ELSE")) {
                elsePresent = true;
                break;
            }
            thenStatements.add(parseStatement());
        }
        // if there was an else,
        // get the else statements
        if(elsePresent) {
            while (!peek("END")) {
                elseStatements.add(parseStatement());
            }
        }
        // check for END keyword??? it might get
        // caught in an endless while loops if its not there?
        if (!match("END")) { //should this be peek?
            throw new ParseException("Missing keyword 'END'", tokens.get(0).getIndex());
        }

        return new Ast.Statement.If(condition, thenStatements, elseStatements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    //do in PART 2
    //'FOR' '(' (identifier '=' expression)? ';' expression ';' (identifier '=' expression)? ')' statement* 'END'
    public Ast.Statement.For parseForStatement() throws ParseException {
        if(!match("FOR")) {
            throw new ParseException("Missing the FOR keyword", tokens.get(0).getIndex());
        }
        if(!match("(")) {
            throw new ParseException("Missing the ( ", tokens.get(0).getIndex());
        }

        // Declare initializer and increment outside the if blocks
        Ast.Statement initializer = null;
        Ast.Statement increment = null;

        if (peek(Token.Type.IDENTIFIER)) {
            // there is something inside
            //capture the identifier token
            String varName = tokens.get(0).getLiteral();
            tokens.advance();
            // Question: do we need to be separating identifiers? if there is person.name or something does that need to be separated in this step?
            Ast.Expression.Access identifier = new Ast.Expression.Access(Optional.empty(), varName);
            if (!match("=")) {
                throw new ParseException("Missing the = ", tokens.get(0).getIndex());
            };
            // must be followed by expression
            Ast.Expression expression = parseExpression();
            // now captured the two components, identifier and expression
            // combine them into one object called intiializer
            initializer = new Ast.Statement.Assignment(identifier, expression);
        }
        // if didnt match identifier, there must be a ;
        // if doesnt match ; write missing semicolon or invalid identifier
        if(!match(";")) {
            throw new ParseException("Missing the first ; ", tokens.get(0).getIndex());
        }

        // at this point we have gotten through the first ; and should have expression
        Ast.Expression condition = null;
        if(!peek(";")) {
            condition = parseExpression();
        }
        if(condition == null) { //do we need this?????
            throw new ParseException("Missing condition in for loop", tokens.get(0).getIndex());
        }
        if(!match(";")) {
            throw new ParseException("Missing the second ; ", tokens.get(0).getIndex());
        }

        if (peek(Token.Type.IDENTIFIER)) {
            // there is something in increment area
            String varName = tokens.get(0).getLiteral();
            tokens.advance();
            Ast.Expression.Access identifier = new Ast.Expression.Access(Optional.empty(), varName);
            if (!match("=")) {
                throw new ParseException("Missing the = ", tokens.get(0).getIndex());
            };
            // must be followed by expression
            Ast.Expression expression = parseExpression();
            // now captured the two components, identifier and expression
            // combine them into one object called intiializer
            increment =  new Ast.Statement.Assignment(identifier, expression);
        }
        if(!match(")")) {
            throw new ParseException("Missing the )", tokens.get(0).getIndex());
        }
        // check for one or more statements while there is no END
        List<Ast.Statement>  statements = new ArrayList<>();
//        while (!match("END")) {
//            statements.add(parseStatement());
//            if(!tokens.has(0)) { //if no more char, avoid out of bounds
//                throw new ParseException("Missing keyword END", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
//            }
//        }
        while (!peek("END")) {
            if (!tokens.has(0)) {
                throw new ParseException("Missing  END", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            statements.add(parseStatement());
        }

        if (!match("END")) {
            throw new ParseException("Missing  END", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }

        return new Ast.Statement.For(initializer, condition, increment, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    //do in PART 2
    //'WHILE' expression 'DO' statement* 'END'
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        // get the condition
        //ensure starts with word IF
        if(!match("WHILE")) {
            throw new ParseException("missing the word WHILE", tokens.get(0).getIndex());
        }
        // must be followed by an expression
        Ast.Expression condition = parseExpression(); //parse the following expression

        //check for do keyword
        if (!match("DO")) {
            throw new ParseException("Missing the keyword DO", tokens.get(0).getIndex());
        }

        // cycle through the statements
        List<Ast.Statement>  statements = new ArrayList<>();
        while (!match("END")) {
            statements.add(parseStatement());
            if(!tokens.has(0)) { //if no more char, avoid out of bounds
                throw new ParseException("Missing keyword END", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        return new Ast.Statement.While(condition, statements);

    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    //do in PART 2
    // 'RETURN' expression ';'
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        //ensure starts with word RETURN
        if(!match("RETURN")) {
            throw new ParseException("missing the word RETURN", tokens.get(0).getIndex());
        }
        // must be followed by an expression
        Ast.Expression value = parseExpression(); //parse the following expression

        //check for semicolon at end
        if (!match(";")) {
            throw new ParseException("Missing the ;", tokens.get(0).getIndex());
        }

        return new Ast.Statement.Return(value);
    }


    //THE REST OF THESE ARE DONE FROM PT. 1
    /**
     * Parses the {@code expression} rule.
     */
    //simply a recursive call (expression ::= logical_expression)
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    //DO THIS ONE
    // parse logical_expression ::= comparison_expression (('&&' | '||') comparison_expression)*
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression firstExp = parseEqualityExpression();

        while (peek("&&")|| peek("||")) {
            String op = tokens.get(0).getLiteral(); //get the actual operator token
            tokens.advance(); //advance to next token

            if (!tokens.has(0)) { //make sure has another token
                throw new ParseException("Missing token", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }

            Ast.Expression secondExp = parseEqualityExpression(); // Parse second expression, which is recursive call
            firstExp = new Ast.Expression.Binary(op, firstExp, secondExp); //merge left and right hand exps into one binary exp
        }
        return firstExp;
    }

    /**
     * Parses the {@code equality-expression} ru
     */
    //DO THIS ONE
    // parse comparison_expression ::= additive_expression (('<' | '<=' | '>' | '>=' | '==' | '!=') additive_expression)*
    public Ast.Expression parseEqualityExpression() throws ParseException {
        Ast.Expression firstExp = parseAdditiveExpression();
        while (peek("<") || peek("<=") || peek(">")
                || peek(">=") || peek( "==") || peek("!=")) {
            String op = tokens.get(0).getLiteral(); //get whichever operator
            tokens.advance();
            if (!tokens.has(0)) { //make sure has another token
                throw new ParseException("Missing token", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            Ast.Expression secondExp = parseAdditiveExpression();
            firstExp = new Ast.Expression.Binary(op, firstExp, secondExp);
        }
        return firstExp;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    //DO THIS ONE
    //parse additive_expression ::= multiplicative_expression (('+' | '-') multiplicative_expression)*
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression firstExp = parseMultiplicativeExpression();
        while (peek("+") || peek("-")) {
            String op = tokens.get(0).getLiteral();
            //System.out.println("Found operator: " + op); // debug
            tokens.advance();

            if (!tokens.has(0)) { //make sure has another token
                throw new ParseException("Missing token", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }

            Ast.Expression secondExp = parseSecondaryExpression();
            firstExp = new Ast.Expression.Binary(op, firstExp, secondExp);
            //System.out.println("Constructed binary expression: " + firstExp);
        }
        return firstExp;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    //DO THIS ONE
    //parse multiplicative_expression ::= secondary_expression (('*' | '/') secondary_expression)*
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        //System.out.println("Current token: " + tokens.get(0).getLiteral()); //debug
        Ast.Expression firstExp = parseSecondaryExpression();

        while (peek("*") || peek("/")) {
            String op = tokens.get(0).getLiteral();
            tokens.advance();

            if (!tokens.has(0)) { //make sure has another token
                throw new ParseException("Missing token", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }

            Ast.Expression secondExp = parseSecondaryExpression();
            firstExp = new Ast.Expression.Binary(op, firstExp, secondExp);
        }
        return firstExp;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    //DO THIS ONE
    //parse secondary_expression ::= primary_expression ('.' identifier ('(' (expression (',' expression)*)? ')')?)*
    public Ast.Expression parseSecondaryExpression() throws ParseException {
        Ast.Expression prim_Exp = parsePrimaryExpression();

        //iterate while ('.' identifier ('(' (expression (',' expression)*)? ')')?) is true
        while(match(".")){
            if(!peek(Token.Type.IDENTIFIER)){
                throw new ParseException("Not an identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            String identifier = tokens.get(0).getLiteral();
            tokens.advance();

            //check if followed by '('
            if(match("(")){
                List<Ast.Expression> expressions = new ArrayList<>();
                if(!match(")")){
                    expressions.add(parseExpression()); //add first exp
                    while (match(",")) { //add remaining exp to list
                        expressions.add(parseExpression());
                    }
                    //make sure closing )
                    if(!tokens.has(0)) { //if no more char, avoid out of bounds
                        throw new ParseException("Missing last char", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                    }
                    if (!match(")")) {
                        throw new ParseException("Missing ')' at", tokens.get(0).getIndex());
                    }
                }
                //declare as a function call like funct(arg1, arg2) or obj.funct(arg1...)
                prim_Exp = new Ast.Expression.Function(Optional.of(prim_Exp), identifier, expressions);
            }
            //if not a function call, then it's primary_expression.identifier, ie obj.field
            else {
                prim_Exp = new Ast.Expression.Access(Optional.of(prim_Exp), identifier);
            }
        }
        return prim_Exp;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    //DO THIS ONE

    public Ast.Expression parsePrimaryExpression() throws ParseException {
        /* Cases to parse:
        'NIL' | 'TRUE' | 'FALSE' |
        integer | decimal | character | string |
        '(' expression ')' |
        identifier ('(' (expression (',' expression)*)? ')')?
        */
        Ast.Expression exp; //????

        //go through the cases for primary expressions!
        if(match("NIL"))
            exp= new Ast.Expression.Literal(null);
        else if(match("TRUE"))
            exp= new Ast.Expression.Literal(true);
        else if(match("FALSE"))
            exp= new Ast.Expression.Literal(false);
        // should this be match???? TODO
        // it was peek but i changed to match in testing
        else if(match(Token.Type.INTEGER))
            exp = new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        else if(peek(Token.Type.DECIMAL))
            exp= new Ast.Expression.Literal(new BigDecimal(tokens.get(0).getLiteral()));
        else if(peek(Token.Type.CHARACTER)) {
            String charToken = tokens.get(0).getLiteral();

            //remove quotes and acct for escapes
            charToken = charToken.substring(1, charToken.length() - 1)
                    .replace("\\b", "\b")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");

            if (charToken.length() != 1) {
                throw new ParseException("Invalid character length", tokens.get(0).getIndex());
            }

            //char character = charToken.charAt(0);
            exp = new Ast.Expression.Literal(charToken.charAt(0));
        }
        else if(peek(Token.Type.STRING)) {
            String string = tokens.get(0).getLiteral();
            exp = new Ast.Expression.Literal(string.substring(1, string.length() - 1)); //remove the quotes from string

            //acct for escape char and remove double quotes
            String escape_char = string.substring(1, string.length() - 1)
                    .replace("\\b", "\b")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            exp = new Ast.Expression.Literal(escape_char);
        }
        else if(match("(")){
            exp = parseExpression();
            if(!tokens.has(0)) { //if no more char
                throw new ParseException("Missing last char", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length()); //use prev token length
            }
            if (!match(")"))
                throw new ParseException("Missing ')' at", tokens.get(0).getIndex());

            exp=new Ast.Expression.Group(exp); //check this line
        }
        else if(peek(Token.Type.IDENTIFIER)){
            // TODO: he uses -1 as the index because match advances the char stream by 1
            // but we use peek here so not sure what is right
            String identifier = tokens.get(0).getLiteral();
            tokens.advance();

            //check if followed by ('(' (expression (',' expression)*)? ')')?
            if(match("(")){
                List<Ast.Expression> expressions = new ArrayList<>();
                if(!match(")")){
                    expressions.add(parseExpression());
                    while (match(",")) { //add exp to list
                        expressions.add(parseExpression());
                    }
                    if(!tokens.has(0)) { //if no more char, avoid out of bounds
                        throw new ParseException("Missing last char", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                    }
                    if (!match(")"))
                        throw new ParseException("Invalid ')'", tokens.get(0).getIndex()); //double check
                }
                //function call
                exp= new Ast.Expression.Function(Optional.empty(), identifier, expressions);
            }
            //obj.field
            else
                exp= new Ast.Expression.Access(Optional.empty(), identifier);
        }
        else
            throw new ParseException("invalid primary expression :(", tokens.get(0).getIndex()); //tokens.index ?

        return exp;
    }


    //given in class
    public boolean peek(Object... patterns) {
        for(int i=0; i < patterns.length; i++){
            if(!tokens.has(i)){
                return false;
            }
            else if(patterns[i] instanceof Token.Type){
                if(patterns[i] != tokens.get(i).getType()){
                    return false;
                }
            }
            else if(patterns[i] instanceof String){
                if(!patterns[i].equals(tokens.get(i).getLiteral())){
                    return false;
                }
            }
            else{
                throw new AssertionError("Invalid pattern obj: " + patterns[i].getClass());
            }
        }
        return true;
    }

    //given in class
    public boolean match(Object...patterns) {
        //System.out.println("WHAT IS THIS LEGNTH: " + patterns.length);
        boolean peek = peek(patterns);
        if(peek){
            for(int i=0; i<patterns.length; i++){
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
