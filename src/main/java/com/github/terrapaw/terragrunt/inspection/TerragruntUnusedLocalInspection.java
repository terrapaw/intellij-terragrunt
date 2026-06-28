package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class TerragruntUnusedLocalInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                // Skip files that are likely shared/included configs (they export locals cross-file).
                // A file is a leaf unit if it has include or dependency blocks.
                if (!hasIncludeOrDependency(file)) return;

                // Collect all local.X references
                Set<String> usedLocals = new HashSet<>();
                for (TerragruntGetAttr getAttr : PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class)) {
                    PsiElement parent = getAttr.getParent();
                    if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
                    TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                    if (primary == null) continue;
                    TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                    if (varExpr == null || !"local".equals(varExpr.getIdentifier().getText())) continue;
                    PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                    if (getAttrs != null && getAttrs.length > 0) {
                        usedLocals.add(((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText());
                    }
                }

                // Check each locals block attribute
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                    if (!"locals".equals(TerragruntPsiUtil.getBlockType(block))) continue;
                    TerragruntBody body = block.getBody();
                    if (body == null) continue;
                    for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                        String name = attr.getIdentifier().getText();
                        if (!usedLocals.contains(name) && !isSuppressedFor(attr)) {
                            holder.registerProblem(attr.getIdentifier(),
                                    "Unused local variable '" + name + "'",
                                    ProblemHighlightType.LIKE_UNUSED_SYMBOL);
                        }
                    }
                }
            }
        };
    }

    private boolean hasIncludeOrDependency(PsiFile file) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            String type = TerragruntPsiUtil.getBlockType(block);
            if ("include".equals(type) || "dependency".equals(type)) return true;
        }
        return false;
    }
}
