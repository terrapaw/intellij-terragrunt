package com.github.joelm.terragrunt.reference;

import com.github.joelm.terragrunt.lang.psi.*;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TerragruntGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) return null;

        // Check if we're on an IDENTIFIER inside a GetAttr
        PsiElement parent = sourceElement.getParent();
        if (!(parent instanceof TerragruntGetAttr getAttr)) return null;

        // Walk up to find the postfix expression
        PsiElement grandParent = getAttr.getParent();
        if (!(grandParent instanceof TerragruntPostfixExpr postfix)) return null;

        // Get the root variable
        TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
        if (primary == null) return null;
        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
        if (varExpr == null) return null;
        String rootVar = varExpr.getIdentifier().getText();

        // Only handle the first get_attr (the name part)
        PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
        if (getAttrs == null || getAttrs.length == 0 || getAttrs[0] != parent) return null;

        String attrName = getAttr.getIdentifier().getText();
        PsiFile file = sourceElement.getContainingFile();

        if ("local".equals(rootVar)) {
            return resolveLocal(file, attrName);
        }
        if ("dependency".equals(rootVar)) {
            return resolveDependency(file, attrName);
        }
        if ("feature".equals(rootVar)) {
            return resolveFeature(file, attrName);
        }

        return null;
    }

    private PsiElement[] resolveLocal(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (name.equals(attr.getIdentifier().getText())) {
                    return new PsiElement[]{attr};
                }
            }
        }
        return null;
    }

    private PsiElement[] resolveDependency(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"dependency".equals(block.getIdentifier().getText())) continue;
            for (TerragruntLabel label : block.getLabelList()) {
                if (name.equals(label.getText().replace("\"", ""))) {
                    return new PsiElement[]{block};
                }
            }
        }
        return null;
    }

    private PsiElement[] resolveFeature(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"feature".equals(block.getIdentifier().getText())) continue;
            for (TerragruntLabel label : block.getLabelList()) {
                if (name.equals(label.getText().replace("\"", ""))) {
                    return new PsiElement[]{block};
                }
            }
        }
        return null;
    }
}
