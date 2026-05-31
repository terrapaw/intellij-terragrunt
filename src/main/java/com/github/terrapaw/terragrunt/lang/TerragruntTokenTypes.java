package com.github.terrapaw.terragrunt.lang;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Convenience re-exports of token types from the generated TerragruntTypes.
 * Used by syntax highlighter and parser definition.
 */
public interface TerragruntTokenTypes {
    TokenSet COMMENTS = TokenSet.create(TerragruntTypes.LINE_COMMENT, TerragruntTypes.BLOCK_COMMENT);
    TokenSet STRINGS = TokenSet.create(TerragruntTypes.STRING_LITERAL, TerragruntTypes.HEREDOC_CONTENT, TerragruntTypes.QUOTE);
    TokenSet KEYWORDS = TokenSet.create(
            TerragruntTypes.TRUE, TerragruntTypes.FALSE, TerragruntTypes.NULL,
            TerragruntTypes.FOR, TerragruntTypes.IN, TerragruntTypes.IF,
            TerragruntTypes.ELSE, TerragruntTypes.ENDIF, TerragruntTypes.ENDFOR
    );
}
