package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Flags invalid content inside autoinclude blocks:
 * - locals blocks (not allowed)
 * - values attribute (not allowed)
 * - nested autoinclude blocks (not allowed)
 */
public class TerragruntAutoincludeInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntBlock block)) return;
                if (!"autoinclude".equals(TerragruntPsiUtil.getBlockType(block))) return;
                checkAutoincludeBody(block, holder);
            }
        };
    }

    private void checkAutoincludeBody(TerragruntBlock autoincludeBlock, ProblemsHolder holder) {
        TerragruntBody body = autoincludeBlock.getBody();
        if (body == null) return;

        for (TerragruntBlock child : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntBlock.class)) {
            String type = TerragruntPsiUtil.getBlockType(child);
            if ("locals".equals(type) && !isSuppressedFor(child)) {
                holder.registerProblem(child.getIdentifier(),
                        "locals blocks are not allowed in autoinclude",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
            if ("autoinclude".equals(type) && !isSuppressedFor(child)) {
                holder.registerProblem(child.getIdentifier(),
                        "Nested autoinclude blocks are not allowed",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }

        for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
            if ("values".equals(attr.getIdentifier().getText()) && !isSuppressedFor(attr)) {
                holder.registerProblem(attr.getIdentifier(),
                        "values attribute is not allowed in autoinclude",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }
    }
}
