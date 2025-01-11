package plc.project;

import java.util.ArrayList; //???
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the invalid character.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation easier.
 */

public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> list = new ArrayList<>();
        while(chars.has(0)){
            if (peek("[ \b\n\r\t]")) {
                chars.advance();
                chars.skip();
            }
            else if (peek("[\s]")){
                chars.advance();
                chars.skip();
            }
            else {
                list.add(lexToken()); //else determine token
            }
        }
        return list;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if (peek("[A-Za-z_]")) {
            return lexIdentifier();  //identifiers
        }
        else if (peek("[+-]", "[0-9]")) {
            return lexNumber();  //ints and decimals
        }
        else if (peek("[0-9]")) {
            return lexNumber(); //unsigned ints and decimals
        }
        else if (peek("[']")) {
            return lexCharacter();  //characters
        }
        else if (peek("[\"]")) {
            return lexString();  //strings
        }
        else {
            return lexOperator();  //operators
        }
    }

    public Token lexIdentifier() {
        //check if there is a first char, and if it is not a digit or hyphen
        if (!chars.has(0) || !Character.toString(chars.get(0)).matches("[A-Za-z_]")) {
            throw new ParseException("Invalid leading char", chars.index);
        }

        chars.advance(); //first char
        while(chars.has(0) && Character.toString(chars.get(0)).matches("[A-Za-z0-9_-]")) {
            chars.advance(); //check if the current char matches specs for identifier
        }

        return chars.emit(Token.Type.IDENTIFIER); //declare the token as an Identifier
    }

    public Token lexNumber() {
        String token="";
        if(chars.has(0) && Character.toString(chars.get(0)).matches("[+-]")){
            token = token + chars.get(0); //append + or - char to token
            chars.advance(); //increment to next char
        }
        //right now token = "-"

        //check if valid digit
        if (!chars.has(0) || !Character.toString(chars.get(0)).matches("[0-9]")) {
            throw new ParseException("Invalid leading number", chars.index);
        }

        //check the case of first digit=0, if so must either be valid decimal or just 0
        if(chars.get(0) == '0') {
            token = token + chars.get(0);
            chars.advance();

            //check if decimal, no leading 0s
            if (chars.has(0) && chars.get(0) == '.') {
                token = token + chars.get(0);
                chars.advance();

                //if so, check if valid subsequent digit
                if (!chars.has(0) || !Character.toString(chars.get(0)).matches("[0-9]")) {
                    throw new ParseException("Not a valid decimal at index ", chars.index);
                }
                //add the rest of decimal digits to token
                while (chars.has(0) && Character.toString(chars.get(0)).matches("[0-9]")) {
                    token = token + chars.get(0);
                    chars.advance();
                }
                //declare as a Decimal token (form +-0.124...)
                return chars.emit(Token.Type.DECIMAL);
            }
            //cases where just int 0
            if(token.equals("0"))
                return chars.emit(Token.Type.INTEGER);
            else
                throw new ParseException("Not valid int 0", chars.index);
        }
        //else token starts with nonzero digit
        else{
            //check to make sure valid digits
            while (chars.has(0) && Character.toString(chars.get(0)).matches("[0-9]")) {
                token = token+chars.get(0);
                chars.advance();
            }
            //check if valid decimal and subsequent digits
            if(chars.has(0) && chars.get(0) == '.'){
                token = token+chars.get(0);
                chars.advance();
                //make sure digit follows decimal
                if (!chars.has(0) || !Character.toString(chars.get(0)).matches("[0-9]")) {
                    throw new ParseException("Invalid decimal", chars.index);
                }
                //else add digits to decimal token
                while (chars.has(0) && Character.toString(chars.get(0)).matches("[0-9]")) {
                    token = token+chars.get(0);
                    chars.advance();
                }
                //declare as a decimal token
                return chars.emit(Token.Type.DECIMAL);
            }
            //else just a nonzero integer!
            return chars.emit(Token.Type.INTEGER);
        }
    }

    public Token lexCharacter() {
        // we know that the lexer got a '
        /* add that to the token
        if the next char is an escape sequence, make sure its valid
        and add it- and the char after- to the token
        else if the next thing is a valid char- anything but a '
        add it to the token
        ?Q? now what about @ character or % character??
        well according to character ::= ['] ([^'\\] | escape) [']
        those would be allowed
        after checking those:
        if the one after THAT is anything but a ', send error
         */
        if(chars.has(0) && Character.toString(chars.get(0)).matches("[']")){
            chars.advance();
            //adding the ' to token
            //moving to next index and adding length to token
        }
        if (chars.has(0) && Character.toString(chars.get(0)).matches("[\\\\]")){
            // make sure its a valid escape
            lexEscape();
            //TODO possible error in lexEscape bc newline not passing tests T

        }
        else if (chars.has(0) && Character.toString(chars.get(0)).matches("[^'\\\\]")){
            chars.advance();
        }
        else {
            throw new ParseException("Invalid Character", chars.index);
        }
        if (chars.has(0) && Character.toString(chars.get(0)).matches("[']")){
            chars.advance();
        }
        else {
            throw new ParseException("Unterminated Character", chars.index);
        }
        return chars.emit(Token.Type.CHARACTER);
    }

    public Token lexString() {
        // you see a " and were taken to this function
        if(chars.has(0) && Character.toString(chars.get(0)).matches("[\"]")){
            chars.advance();
            //adding the " to token
            // this should always execute i think
        }
        // while there is a character in the index v
        while(chars.has(0)) {
            // if character is a ", string is done. add to token and return
            if (Character.toString(chars.get(0)).matches("[\"]")){
                chars.advance();
                return chars.emit(Token.Type.STRING);
            }
            // if character is \, make sure followed by valid escape
            else if (Character.toString(chars.get(0)).matches("[\\\\]")){
                // make sure its a valid escape
                lexEscape();
                //TODO possible error in lexEscape bc newline not passing tests T
            }
            //if its not whitespace, advance
            else if (chars.has(0) && Character.toString(chars.get(0)).matches("[^\\n\\r]")){
                chars.advance();
            }
            // catch the whitespace in the string and send a ParseException
            else if (!Character.toString(chars.get(0)).matches("[^\\n\\r]")) {
                throw new ParseException("String spans over multiple lines", chars.index);
            }
        }
        //If you get to this line, went though whole thing without ending “
            throw new ParseException("Unterminated String", chars.index);
    }

    //super confused what we do with this void function?? I guess just check if valid escape
    public void lexEscape() {
        //make sure escape char starts with a \
        if (!chars.has(0) || chars.get(0) != '\\') {
            throw new ParseException("Invalid escape", chars.index);
        }

        chars.advance();  //go to next char after backslash
        // will check \b -> \\\b

        if (!chars.has(0)) {
            throw new ParseException("Unterminated escape :(", chars.index);
        }

        //get the escape character and check if valid [bnrt'"\]
        if(chars.has(0) && !Character.toString(chars.get(0)).matches("[bnrt\\'\\\"\\\\]")){
            throw new ParseException("Invalid escape: \\" + chars.get(0), chars.index);
        }
        chars.advance();
    }

    public Token lexOperator() {
        if (chars.has(0) && Character.toString(chars.get(0)).matches("[<>!=]")) {
            // check if followed by an =
            if (chars.has(1) && Character.toString(chars.get(1)).matches("=")) {
                // advance twice for both of them
                chars.advance();
                chars.advance();
                return chars.emit(Token.Type.OPERATOR);
            }
            else {
                chars.advance();
                return chars.emit(Token.Type.OPERATOR);
            }
        }
            // if not a pair, it will be added as its own operator later
        else if (chars.has(0) && Character.toString(chars.get(0)).matches("[&]")){
                //check if next is another &
                if (chars.has(1) && Character.toString(chars.get(1)).matches("[&]")){
                    // advance twice for both of them
                    chars.advance();
                    chars.advance();
                    return chars.emit(Token.Type.OPERATOR);
                }
                else {
                    chars.advance();
                    return chars.emit(Token.Type.OPERATOR);
                }
        }
        else if(chars.has(0) && Character.toString(chars.get(0)).matches("[|]")) {
            //check if next is another |
            if (chars.has(1) && Character.toString(chars.get(1)).matches("[|]")){
                // advance twice for both of them
                chars.advance();
                chars.advance();
                return chars.emit(Token.Type.OPERATOR);
            }
            else {
                chars.advance();
                return chars.emit(Token.Type.OPERATOR);
            }
        }
        // any other character would be a ! @ # $ % ( ) etc.
        else {
            // if it reaches this line, must be valid
            chars.advance();
            return chars.emit(Token.Type.OPERATOR);
        }
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) { //given in lecture
        for(int i=0; i<patterns.length; i++){
            if(!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])){
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) { //given by dobbins
        boolean peek = peek(patterns);
        if(peek)
        {
            for(int i=0; i< patterns.length; i++){
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
