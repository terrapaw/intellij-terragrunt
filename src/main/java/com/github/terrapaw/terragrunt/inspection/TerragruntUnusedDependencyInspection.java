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

public class TerragruntUnusedDependencyInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                // Collect all dependency.X references
                Set<String> usedDeps = new HashSet<>();
                for (TerragruntGetAttr getAttr : PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class)) {
                    PsiElement parent = getAttr.getParent();
                    if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
                    TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                    if (primary == null) continue;
                    TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                    if (varExpr == null || !"dependency".equals(varExpr.getIdentifier().getText())) continue;
                    PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                    if (getAttrs != null && getAttrs.length > 0) {
                        usedDeps.add(((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText());
                    }
                }

                // Also check dependencies { paths = [...] } references (the list-based form)
                // These don't produce dependency.X usages but are valid

                // Check each dependency block
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                    if (!"dependency".equals(TerragruntPsiUtil.getBlockType(block))) continue;
                    var labels = block.getLabelList();
                    if (labels.isEmpty()) continue;
                    String name = TerragruntPsiUtil.getLabelText(labels.get(0));
                    if (name == null || name.isEmpty()) continue;
                    if (!usedDeps.contains(name) && !isSuppressedFor(block)) {
                        holder.registerProblem(labels.get(0),
                                "Unused dependency '" + name + "'",
                                ProblemHighlightType.LIKE_UNUSED_SYMBOL);
                    }
                }
            }
        };
    }
}
