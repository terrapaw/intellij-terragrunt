package com.github.terrapaw.terragrunt.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class RemoveExtraLabelQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
        return "Remove extra label";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement label = descriptor.getPsiElement();
        if (label != null && label.isValid()) {
            label.delete();
        }
    }
}
