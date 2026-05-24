package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.TerragruntLanguage;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectExpr;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class TerragruntLiveTemplateContext extends TemplateContextType {

    public TerragruntLiveTemplateContext() {
        super("Terragrunt HCL");
    }

    @Override
    public boolean isInContext(@NotNull TemplateActionContext context) {
        if (!context.getFile().getLanguage().is(TerragruntLanguage.INSTANCE)) return false;

        int offset = context.getStartOffset();
        PsiElement element = context.getFile().findElementAt(offset);
        if (element == null) return true; // empty file

        // Walk up — reject if inside any block, object, tuple, or attribute value
        PsiElement current = element;
        while (current != null && !(current instanceof com.intellij.psi.PsiFile)) {
            if (current instanceof TerragruntBlock) return false;
            if (current instanceof TerragruntObjectExpr) return false;
            if (current instanceof com.github.terrapaw.terragrunt.lang.psi.TerragruntTupleExpr) return false;
            if (current instanceof com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute) return false;
            current = current.getParent();
        }
        return true;
    }
}
