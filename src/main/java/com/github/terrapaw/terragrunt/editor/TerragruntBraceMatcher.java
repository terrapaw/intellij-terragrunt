package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TerragruntBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] PAIRS = {
            new BracePair(TerragruntTypes.LBRACE, TerragruntTypes.RBRACE, true),
            new BracePair(TerragruntTypes.LBRACKET, TerragruntTypes.RBRACKET, false),
            new BracePair(TerragruntTypes.LPAREN, TerragruntTypes.RPAREN, false),
            new BracePair(TerragruntTypes.INTERPOLATION_START, TerragruntTypes.INTERPOLATION_END, false),
    };

    @NotNull
    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
