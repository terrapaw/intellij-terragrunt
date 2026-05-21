package com.github.joelm.terragrunt.editor;

import com.github.joelm.terragrunt.lang.TerragruntLanguage;
import com.github.joelm.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class TerragruntFormattingModelBuilder implements FormattingModelBuilder {
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
        SpacingBuilder spacingBuilder = new SpacingBuilder(settings, TerragruntLanguage.INSTANCE);

        TerragruntFormattingBlock rootBlock = new TerragruntFormattingBlock(
                formattingContext.getNode(), null, null, spacingBuilder);

        return FormattingModelProvider.createFormattingModelForPsiFile(
                formattingContext.getContainingFile(), rootBlock, settings);
    }
}
