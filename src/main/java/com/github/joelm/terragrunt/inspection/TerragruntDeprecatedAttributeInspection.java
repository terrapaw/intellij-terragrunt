package com.github.joelm.terragrunt.inspection;

import com.github.joelm.terragrunt.lang.psi.TerragruntAttribute;
import com.github.joelm.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class TerragruntDeprecatedAttributeInspection extends LocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntAttribute attr)) return;
                PsiElement nameElement = attr.getIdentifier();
                String name = nameElement.getText();
                if (TerragruntSchema.isDeprecated(name)) {
                    holder.registerProblem(nameElement,
                            "Deprecated attribute '" + name + "'. Use 'mock_outputs_merge_strategy_with_state' instead.",
                            ProblemHighlightType.LIKE_DEPRECATED);
                }
            }
        };
    }
}
