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
        SpacingBuilder spacingBuilder = new SpacingBuilder(settings, TerragruntLanguage.INSTANCE)
                .after(TerragruntTypes.LBRACE).lineBreakInCode()
                .before(TerragruntTypes.RBRACE).lineBreakInCode()
                .after(TerragruntTypes.EQUALS).spaces(1)
                .before(TerragruntTypes.EQUALS).spaces(1)
                .after(TerragruntTypes.COMMA).spaces(1)
                .after(TerragruntTypes.COLON).spaces(1);

        TerragruntFormattingBlock rootBlock = new TerragruntFormattingBlock(
                formattingContext.getNode(), null, null, spacingBuilder);

        return FormattingModelProvider.createFormattingModelForPsiFile(
                formattingContext.getContainingFile(), rootBlock, settings);
    }
}
