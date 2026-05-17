package com.github.joelm.terragrunt.highlight;

import com.github.joelm.terragrunt.lang.TerragruntLexerAdapter;
import com.github.joelm.terragrunt.lang.TerragruntTokenTypes;
import com.github.joelm.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class TerragruntSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey KEYWORD = createTextAttributesKey("TG_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey STRING = createTextAttributesKey("TG_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMBER = createTextAttributesKey("TG_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey LINE_COMMENT = createTextAttributesKey("TG_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BLOCK_COMMENT = createTextAttributesKey("TG_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey IDENTIFIER = createTextAttributesKey("TG_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey BRACES = createTextAttributesKey("TG_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey BRACKETS = createTextAttributesKey("TG_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey PARENTHESES = createTextAttributesKey("TG_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey OPERATOR = createTextAttributesKey("TG_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey INTERPOLATION = createTextAttributesKey("TG_INTERPOLATION", DefaultLanguageHighlighterColors.METADATA);

    private static final TextAttributesKey[] KEYWORD_KEYS = {KEYWORD};
    private static final TextAttributesKey[] STRING_KEYS = {STRING};
    private static final TextAttributesKey[] NUMBER_KEYS = {NUMBER};
    private static final TextAttributesKey[] LINE_COMMENT_KEYS = {LINE_COMMENT};
    private static final TextAttributesKey[] BLOCK_COMMENT_KEYS = {BLOCK_COMMENT};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = {IDENTIFIER};
    private static final TextAttributesKey[] BRACES_KEYS = {BRACES};
    private static final TextAttributesKey[] BRACKETS_KEYS = {BRACKETS};
    private static final TextAttributesKey[] PARENTHESES_KEYS = {PARENTHESES};
    private static final TextAttributesKey[] OPERATOR_KEYS = {OPERATOR};
    private static final TextAttributesKey[] INTERPOLATION_KEYS = {INTERPOLATION};
    private static final TextAttributesKey[] EMPTY = {};

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new TerragruntLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (TerragruntTokenTypes.KEYWORDS.contains(tokenType)) return KEYWORD_KEYS;
        if (tokenType == TerragruntTypes.STRING_LITERAL || tokenType == TerragruntTypes.HEREDOC_CONTENT) return STRING_KEYS;
        if (tokenType == TerragruntTypes.NUMBER) return NUMBER_KEYS;
        if (tokenType == TerragruntTypes.LINE_COMMENT) return LINE_COMMENT_KEYS;
        if (tokenType == TerragruntTypes.BLOCK_COMMENT) return BLOCK_COMMENT_KEYS;
        if (tokenType == TerragruntTypes.IDENTIFIER) return IDENTIFIER_KEYS;
        if (tokenType == TerragruntTypes.LBRACE || tokenType == TerragruntTypes.RBRACE) return BRACES_KEYS;
        if (tokenType == TerragruntTypes.LBRACKET || tokenType == TerragruntTypes.RBRACKET) return BRACKETS_KEYS;
        if (tokenType == TerragruntTypes.LPAREN || tokenType == TerragruntTypes.RPAREN) return PARENTHESES_KEYS;
        if (tokenType == TerragruntTypes.INTERPOLATION_START || tokenType == TerragruntTypes.DIRECTIVE_START) return INTERPOLATION_KEYS;
        if (tokenType == TerragruntTypes.EQEQ || tokenType == TerragruntTypes.NEQ ||
            tokenType == TerragruntTypes.AND || tokenType == TerragruntTypes.OR ||
            tokenType == TerragruntTypes.LT || tokenType == TerragruntTypes.GT ||
            tokenType == TerragruntTypes.LTEQ || tokenType == TerragruntTypes.GTEQ ||
            tokenType == TerragruntTypes.PLUS || tokenType == TerragruntTypes.MINUS ||
            tokenType == TerragruntTypes.STAR || tokenType == TerragruntTypes.SLASH ||
            tokenType == TerragruntTypes.PERCENT || tokenType == TerragruntTypes.NOT) return OPERATOR_KEYS;
        return EMPTY;
    }
}
