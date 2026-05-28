package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

public class TerragruntUnresolvedVariableInspection extends LocalInspectionTool {
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
                            holder.registerProblem(getAttr.getIdentifier(),
                                    "Unresolved local variable '" + name + "'",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        }
                    }
                    case "dependency" -> {
                        if (!dependencyExists(file, name)) {
                            holder.registerProblem(getAttr.getIdentifier(),
                                    "Unresolved dependency '" + name + "'",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        }
                    }
                    case "feature" -> {
                        if (!featureExists(file, name)) {
                            holder.registerProblem(getAttr.getIdentifier(),
                                    "Unresolved feature '" + name + "'",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        }
                    }
                }
            }
        };
    }

    private boolean localExists(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            Set<String> attrs = PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class).stream()
                    .map(a -> a.getIdentifier().getText())
                    .collect(Collectors.toSet());
            if (attrs.contains(name)) return true;
        }
        return false;
    }

    private boolean dependencyExists(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"dependency".equals(block.getIdentifier().getText())) continue;
            for (TerragruntLabel label : block.getLabelList()) {
                if (name.equals(label.getText().replace("\"", ""))) return true;
            }
        }
        return false;
    }

    private boolean featureExists(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"feature".equals(block.getIdentifier().getText())) continue;
            for (TerragruntLabel label : block.getLabelList()) {
                if (name.equals(label.getText().replace("\"", ""))) return true;
            }
        }
        return false;
    }
}
