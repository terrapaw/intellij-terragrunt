package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.TerragruntLanguage;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntLabel;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectElem;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TerragruntBreadcrumbsProvider implements BreadcrumbsProvider {

    @Override
    public Language[] getLanguages() {
        return new Language[]{TerragruntLanguage.INSTANCE};
    }

    @Override
    public boolean acceptElement(@NotNull PsiElement element) {
        return element instanceof TerragruntBlock || element instanceof TerragruntAttribute
                || element instanceof TerragruntObjectElem;
    }

    @Override
    public @NotNull String getElementInfo(@NotNull PsiElement element) {
        if (element instanceof TerragruntBlock block) {
            String name = block.getIdentifier().getText();
            List<TerragruntLabel> labels = block.getLabelList();
            if (!labels.isEmpty()) {
                name += " \"" + TerragruntPsiUtil.getLabelText(labels.get(0)) + "\"";
            }
            return name;
        } else if (element instanceof TerragruntAttribute attr) {
            return attr.getIdentifier().getText();
        } else if (element instanceof TerragruntObjectElem elem) {
            if (elem.getIdentifier() != null) {
                return elem.getIdentifier().getText();
            }
            // Quoted key: extract from first expression (the key expression)
            var exprs = elem.getExpressionList();
            if (!exprs.isEmpty()) {
                String text = exprs.get(0).getText();
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    return text.substring(1, text.length() - 1);
                }
                return text;
            }
        }
        return "";
    }

    @Override
    public @Nullable String getElementTooltip(@NotNull PsiElement element) {
        return null;
    }
}
