package com.github.joelm.terragrunt.inspection;

import com.github.joelm.terragrunt.lang.psi.TerragruntBlock;
import com.github.joelm.terragrunt.lang.psi.TerragruntBody;
import com.github.joelm.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class TerragruntUnknownBlockInspection extends LocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntBlock block)) return;
                // Only check top-level blocks
                if (!(block.getParent() instanceof TerragruntBody body)) return;
                if (body.getParent() instanceof TerragruntBlock) return;

                String name = block.getIdentifier().getText();
                if (!TerragruntSchema.isKnownBlock(name)) {
                    holder.registerProblem(block.getIdentifier(),
                            "Unknown Terragrunt block '" + name + "'",
                            ProblemHighlightType.WARNING);
                }
            }
        };
    }
}
