package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class TerragruntDuplicateAttributeInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntBody body)) return;
                Set<String> seen = new HashSet<>();
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    String name = attr.getIdentifier().getText();
                    if (!seen.add(name) && !isSuppressedFor(attr)) {
                        holder.registerProblem(attr.getIdentifier(),
                                "Duplicate attribute '" + name + "'",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }
        };
    }
}
