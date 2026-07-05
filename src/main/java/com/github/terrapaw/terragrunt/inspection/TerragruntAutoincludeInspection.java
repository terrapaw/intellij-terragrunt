package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
                String type = TerragruntPsiUtil.getBlockType(block);
                if ("autoinclude".equals(type)) {
                    checkAutoincludeBody(block, holder);
                } else if ("unit".equals(type) || "stack".equals(type)) {
                    checkDuplicateAutoinclude(block, holder);
                }
            }
        };
    }

    private void checkAutoincludeBody(TerragruntBlock autoincludeBlock, ProblemsHolder holder) {
        TerragruntBody body = autoincludeBlock.getBody();
        if (body == null) return;

        // Check for duplicate labeled blocks within the same autoinclude (e.g. two dependency "vpc")
        Map<String, TerragruntBlock> seenBlocks = new java.util.HashMap<>();
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
            // Check for duplicate labeled blocks
            var labels = child.getLabelList();
            if (!labels.isEmpty() && type != null) {
                String label = TerragruntPsiUtil.getLabelText(labels.get(0));
                String key = type + ":" + label;
                if (seenBlocks.containsKey(key) && !isSuppressedFor(child)) {
                    holder.registerProblem(labels.get(0),
                            "Duplicate " + type + " block '" + label + "' in autoinclude",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                } else {
                    seenBlocks.put(key, child);
                }
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

    private void checkDuplicateAutoinclude(TerragruntBlock unitOrStackBlock, ProblemsHolder holder) {
        TerragruntBody body = unitOrStackBlock.getBody();
        if (body == null) return;
        boolean seenAutoinclude = false;
        for (TerragruntBlock child : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntBlock.class)) {
            if ("autoinclude".equals(TerragruntPsiUtil.getBlockType(child))) {
                if (seenAutoinclude && !isSuppressedFor(child)) {
                    holder.registerProblem(child.getIdentifier(),
                            "Only one autoinclude block is allowed per unit/stack",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                seenAutoinclude = true;
            }
        }
    }
}
