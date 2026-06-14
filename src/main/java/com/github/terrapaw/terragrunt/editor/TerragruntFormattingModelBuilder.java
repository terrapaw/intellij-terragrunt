package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.TerragruntLanguage;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class TerragruntFormattingModelBuilder implements FormattingModelBuilder {
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
        SpacingBuilder spacingBuilder = new SpacingBuilder(settings, TerragruntLanguage.INSTANCE)
                // Space after comma
                .after(TerragruntTypes.COMMA).spaces(1)
                // Space after { and before } in inline objects
                .afterInside(TerragruntTypes.LBRACE, TerragruntTypes.OBJECT_EXPR).spaces(1)
                .beforeInside(TerragruntTypes.RBRACE, TerragruntTypes.OBJECT_EXPR).spaces(1);

        TerragruntFormattingBlock rootBlock = new TerragruntFormattingBlock(
                formattingContext.getNode(), null, null, spacingBuilder);

        return FormattingModelProvider.createFormattingModelForPsiFile(
                formattingContext.getContainingFile(), rootBlock, settings);
    }
}
