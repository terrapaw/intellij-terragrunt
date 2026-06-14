package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.TerragruntLanguage;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectExpr;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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

        // Use the original file (before any modifications) to avoid dummy identifier issues
        PsiFile file = context.getFile().getOriginalFile();

        // Check if offset falls inside any top-level block or top-level attribute value
        TerragruntBody body = PsiTreeUtil.getChildOfType(file, TerragruntBody.class);
        if (body == null) return true;

        for (PsiElement child : body.getChildren()) {
            if (child instanceof TerragruntBlock block) {
                if (offset > child.getTextRange().getStartOffset() && offset < child.getTextRange().getEndOffset()) {
                    // If the offset is at/near the block's identifier (typing a new block name), allow it
                    PsiElement id = block.getIdentifier();
                    if (id != null && offset <= id.getTextRange().getEndOffset()) {
                        continue;
                    }
                    return false;
                }
            }
            if (child instanceof com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute attr) {
                int eqOffset = attr.getText().indexOf('=');
                if (eqOffset >= 0 && offset > attr.getTextOffset() + eqOffset) {
                    return false;
                }
            }
        }
        return true;
    }
}
