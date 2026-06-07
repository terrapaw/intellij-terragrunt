package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class TerragruntUnknownBlockInspection extends TerragruntBaseInspection {
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

                if (block.getIdentifier() == null) return;
                String name = TerragruntPsiUtil.getBlockType(block);
                if (!TerragruntSchema.isKnownBlock(name)) {
                    String closest = ReplaceIdentifierQuickFix.findClosest(name, TerragruntSchema.getAllBlocks().keySet());
                    if (closest != null) {
                        holder.registerProblem(block.getIdentifier(),
                                "Unknown Terragrunt block '" + name + "'",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                new ReplaceIdentifierQuickFix(closest));
                    } else {
                        holder.registerProblem(block.getIdentifier(),
                                "Unknown Terragrunt block '" + name + "'",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }
        };
    }
}
