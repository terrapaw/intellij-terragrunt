package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class RemoveDuplicateBlockQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
        return "Remove duplicate block";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement label = descriptor.getPsiElement();
        if (label == null || !label.isValid()) return;

        // Walk up to the block
        PsiElement parent = label.getParent();
        while (parent != null && !(parent instanceof TerragruntBlock)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            parent.delete();
        }
    }
}
