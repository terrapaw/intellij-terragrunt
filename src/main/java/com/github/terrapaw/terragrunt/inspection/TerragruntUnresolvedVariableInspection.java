package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

public class TerragruntUnresolvedVariableInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntGetAttr getAttr)) return;

                PsiElement parent = getAttr.getParent();
                if (!(parent instanceof TerragruntPostfixExpr postfix)) return;

                TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                if (primary == null) return;
                TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                if (varExpr == null) return;
                String rootVar = varExpr.getIdentifier().getText();

                // Only check the first get_attr
                PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                if (getAttrs == null || getAttrs.length == 0 || getAttrs[0] != element) return;

                String name = getAttr.getIdentifier().getText();
                PsiFile file = element.getContainingFile();

                switch (rootVar) {
                    case "local" -> {
                        if (!localExists(file, name)) {
                            Set<String> locals = getLocalNames(file);
                            String closest = ReplaceIdentifierQuickFix.findClosest(name, locals);
                            if (closest != null) {
                                holder.registerProblem(getAttr.getIdentifier(),
                                        "Unresolved local variable '" + name + "'",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        new ReplaceIdentifierQuickFix(closest));
                            } else {
                                holder.registerProblem(getAttr.getIdentifier(),
                                        "Unresolved local variable '" + name + "'",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                            }
                        }
                    }
                    case "dependency" -> {
                        if (!dependencyExists(file, name)) {
                            Set<String> deps = getBlockLabels(file, "dependency");
                            String closest = ReplaceIdentifierQuickFix.findClosest(name, deps);
                            if (closest != null) {
                                holder.registerProblem(getAttr.getIdentifier(),
                                        "Unresolved dependency '" + name + "'",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        new ReplaceIdentifierQuickFix(closest));
                            } else {
                                holder.registerProblem(getAttr.getIdentifier(),
                                        "Unresolved dependency '" + name + "'",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                            }
                        }
                    }
                    case "feature" -> {
                        if (!featureExists(file, name)) {
                            Set<String> features = getBlockLabels(file, "feature");
                            String closest = ReplaceIdentifierQuickFix.findClosest(name, features);
                            if (closest != null) {
                                holder.registerProblem(getAttr.getIdentifier(),
                                        "Unresolved feature '" + name + "'",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        new ReplaceIdentifierQuickFix(closest));
                            } else {
                                holder.registerProblem(getAttr.getIdentifier(),
                                        "Unresolved feature '" + name + "'",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                            }
                        }
                    }
                }
            }
        };
    }

    private boolean localExists(PsiFile file, String name) {
        return getLocalNames(file).contains(name);
    }

    private Set<String> getLocalNames(PsiFile file) {
        Set<String> names = new java.util.HashSet<>();
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)
                    .forEach(a -> names.add(a.getIdentifier().getText()));
        }
        return names;
    }

    private Set<String> getBlockLabels(PsiFile file, String blockType) {
        Set<String> labels = new java.util.HashSet<>();
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!blockType.equals(block.getIdentifier().getText())) continue;
            var labelList = block.getLabelList();
            if (!labelList.isEmpty()) {
                labels.add(TerragruntPsiUtil.getLabelText(labelList.getFirst()));
            }
        }
        return labels;
    }

    private boolean dependencyExists(PsiFile file, String name) {
        return TerragruntPsiUtil.blockExists(file, "dependency", name);
    }

    private boolean featureExists(PsiFile file, String name) {
        return TerragruntPsiUtil.blockExists(file, "feature", name);
    }
}
