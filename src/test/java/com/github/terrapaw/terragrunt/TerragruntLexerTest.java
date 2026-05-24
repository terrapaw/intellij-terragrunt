package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.lang.TerragruntLexerAdapter;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

public class TerragruntLexerTest extends BasePlatformTestCase {

    private Lexer lexer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        lexer = new TerragruntLexerAdapter();
    }

    public void testIdentifier() {
        assertTokens("include", TerragruntTypes.IDENTIFIER);
    }

    public void testIdentifierWithDash() {
        assertTokens("my-var", TerragruntTypes.IDENTIFIER);
    }

    public void testKeywords() {
        assertTokens("true", TerragruntTypes.TRUE);
        assertTokens("false", TerragruntTypes.FALSE);
        assertTokens("null", TerragruntTypes.NULL);
    }

    public void testKeywordNotMatchedInsideIdentifier() {
        // "include" contains "in" but should be a single IDENTIFIER
        assertTokens("include", TerragruntTypes.IDENTIFIER);
        assertTokens("format", TerragruntTypes.IDENTIFIER);
        assertTokens("iffy", TerragruntTypes.IDENTIFIER);
        assertTokens("nullify", TerragruntTypes.IDENTIFIER);
    }

    public void testNumber() {
        assertTokens("42", TerragruntTypes.NUMBER);
        assertTokens("3.14", TerragruntTypes.NUMBER);
        assertTokens("1e10", TerragruntTypes.NUMBER);
    }

    public void testStringLiteral() {
        List<IElementType> tokens = getTokenTypes("\"hello\"");
        // Opening quote, content, closing quote - all STRING_LITERAL
        assertTrue("All tokens should be STRING_LITERAL", tokens.stream().allMatch(t -> t == TerragruntTypes.STRING_LITERAL));
        assertEquals(3, tokens.size());
    }

    public void testEmptyString() {
        List<IElementType> tokens = getTokenTypes("\"\"");
        assertTrue("Empty string should be STRING_LITERAL tokens", tokens.stream().allMatch(t -> t == TerragruntTypes.STRING_LITERAL));
        assertEquals(2, tokens.size());
    }

    public void testStringWithInterpolation() {
        // "${func()}" inside a string should tokenize the interpolation content
        List<IElementType> tokens = getTokenTypes("\"${path_relative_to_include()}/state\"");
        assertTrue("Should contain INTERPOLATION_START", tokens.contains(TerragruntTypes.INTERPOLATION_START));
        assertTrue("Should contain IDENTIFIER for function name", tokens.contains(TerragruntTypes.IDENTIFIER));
        assertTrue("Should contain LPAREN", tokens.contains(TerragruntTypes.LPAREN));
        assertTrue("Should contain RPAREN", tokens.contains(TerragruntTypes.RPAREN));
        assertTrue("Should contain INTERPOLATION_END", tokens.contains(TerragruntTypes.INTERPOLATION_END));
        assertTrue("Should contain STRING_LITERAL for /state part", tokens.contains(TerragruntTypes.STRING_LITERAL));
    }

    public void testOperators() {
        assertTokens("==", TerragruntTypes.EQEQ);
        assertTokens("!=", TerragruntTypes.NEQ);
        assertTokens("<=", TerragruntTypes.LTEQ);
        assertTokens(">=", TerragruntTypes.GTEQ);
        assertTokens("&&", TerragruntTypes.AND);
        assertTokens("||", TerragruntTypes.OR);
        assertTokens("=>", TerragruntTypes.FAT_ARROW);
    }

    public void testSingleCharOperators() {
        assertTokens("=", TerragruntTypes.EQUALS);
        assertTokens("+", TerragruntTypes.PLUS);
        assertTokens("-", TerragruntTypes.MINUS);
        assertTokens("*", TerragruntTypes.STAR);
        assertTokens("/", TerragruntTypes.SLASH);
        assertTokens("%", TerragruntTypes.PERCENT);
        assertTokens("!", TerragruntTypes.NOT);
        assertTokens("?", TerragruntTypes.QUESTION);
        assertTokens(".", TerragruntTypes.DOT);
        assertTokens(",", TerragruntTypes.COMMA);
        assertTokens(":", TerragruntTypes.COLON);
    }

    public void testBraces() {
        assertTokens("{", TerragruntTypes.LBRACE);
        assertTokens("}", TerragruntTypes.RBRACE);
        assertTokens("[", TerragruntTypes.LBRACKET);
        assertTokens("]", TerragruntTypes.RBRACKET);
        assertTokens("(", TerragruntTypes.LPAREN);
        assertTokens(")", TerragruntTypes.RPAREN);
    }

    public void testLineComment() {
        assertTokens("# this is a comment", TerragruntTypes.LINE_COMMENT);
        assertTokens("// also a comment", TerragruntTypes.LINE_COMMENT);
    }

    public void testBlockComment() {
        List<IElementType> tokens = getTokenTypes("/* block */");
        assertTrue("Block comment tokens should all be BLOCK_COMMENT",
                tokens.stream().allMatch(t -> t == TerragruntTypes.BLOCK_COMMENT));
    }

    public void testHeredocStart() {
        List<IElementType> tokens = getTokenTypes("<<EOF");
        assertTrue("Should contain HEREDOC_START", tokens.contains(TerragruntTypes.HEREDOC_START));
    }

    public void testTokenSequenceForAttribute() {
        // path = "value"
        List<IElementType> tokens = getTokenTypes("path = \"value\"");
        assertTrue("Should start with IDENTIFIER", tokens.get(0) == TerragruntTypes.IDENTIFIER);
        assertTrue("Should have EQUALS", tokens.contains(TerragruntTypes.EQUALS));
        assertTrue("Should have STRING_LITERAL", tokens.contains(TerragruntTypes.STRING_LITERAL));
    }

    public void testTokenSequenceForBlock() {
        // include "root" {
        List<IElementType> tokens = getTokenTypes("include \"root\" {");
        assertEquals("First token should be IDENTIFIER", TerragruntTypes.IDENTIFIER, tokens.get(0));
        assertTrue("Should have STRING_LITERAL for label", tokens.contains(TerragruntTypes.STRING_LITERAL));
        assertTrue("Should have LBRACE", tokens.contains(TerragruntTypes.LBRACE));
    }

    public void testTokenTypesAreSameInstances() {
        // This is the bug that caused the original parsing failure
        List<IElementType> tokens = getTokenTypes("\"test\"");
        for (IElementType token : tokens) {
            assertSame("Lexer STRING_LITERAL must be same instance as parser expects",
                    TerragruntTypes.STRING_LITERAL, token);
        }

        tokens = getTokenTypes("include");
        assertSame("Lexer IDENTIFIER must be same instance as parser expects",
                TerragruntTypes.IDENTIFIER, tokens.get(0));

        tokens = getTokenTypes("true");
        assertSame("Lexer TRUE must be same instance as parser expects",
                TerragruntTypes.TRUE, tokens.get(0));
    }

    private void assertTokens(String input, IElementType expectedType) {
        List<IElementType> tokens = getTokenTypes(input);
        assertEquals("Expected single token for '" + input + "'", 1, tokens.size());
        assertEquals("Wrong token type for '" + input + "'", expectedType, tokens.get(0));
    }

    private List<IElementType> getTokenTypes(String input) {
        lexer.start(input);
        List<IElementType> tokens = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            IElementType type = lexer.getTokenType();
            if (type != com.intellij.psi.TokenType.WHITE_SPACE) {
                tokens.add(type);
            }
            lexer.advance();
        }
        return tokens;
    }
}
