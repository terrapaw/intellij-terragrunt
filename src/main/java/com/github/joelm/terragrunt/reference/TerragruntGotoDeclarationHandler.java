package com.github.joelm.terragrunt.reference;

import com.github.joelm.terragrunt.lang.psi.*;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TerragruntGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) return null;
        PsiElement parent = sourceElement.getParent();

        // Case 1: On an IDENTIFIER inside a GetAttr (e.g. local.app_name -> go to definition)
        if (parent instanceof TerragruntGetAttr getAttr) {
            return handleGetAttrNavigation(getAttr, sourceElement);
        }

        // Case 2: On an IDENTIFIER that is an attribute name inside locals/dependency/feature block
        // (e.g. Ctrl+B on "app_name" in locals { app_name = "x" } -> find usages)
        if (parent instanceof TerragruntAttribute attr) {
            return handleDefinitionToUsages(attr, sourceElement);
        }

        return null;
    }

    private PsiElement[] handleGetAttrNavigation(TerragruntGetAttr getAttr, PsiElement sourceElement) {
        PsiElement grandParent = getAttr.getParent();
        if (!(grandParent instanceof TerragruntPostfixExpr postfix)) return null;

        TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
        if (primary == null) return null;
        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
        if (varExpr == null) return null;
        String rootVar = varExpr.getIdentifier().getText();

        PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
        if (getAttrs == null || getAttrs.length == 0 || getAttrs[0] != getAttr) return null;

        String attrName = getAttr.getIdentifier().getText();
        PsiFile file = sourceElement.getContainingFile();

        if ("local".equals(rootVar)) return resolveLocal(file, attrName);
        if ("dependency".equals(rootVar)) return resolveDependency(file, attrName);
        if ("feature".equals(rootVar)) return resolveFeature(file, attrName);
        return null;
    }

    private PsiElement[] handleDefinitionToUsages(TerragruntAttribute attr, PsiElement sourceElement) {
        // Only if this attribute is the name identifier
        if (sourceElement != attr.getIdentifier()) return null;

        // Check if we're inside a locals block
        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
        if (block == null) return null;
        String blockType = block.getIdentifier().getText();

        String name = attr.getIdentifier().getText();
        PsiFile file = sourceElement.getContainingFile();

        if ("locals".equals(blockType)) {
            return findLocalUsages(file, name);
        }
        return null;
    }

    private PsiElement[] findLocalUsages(PsiFile file, String name) {
        List<PsiElement> usages = new ArrayList<>();
        Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class);
        for (TerragruntGetAttr getAttr : allGetAttrs) {
            if (!name.equals(getAttr.getIdentifier().getText())) continue;
            // Check it's the first get_attr after "local"
            PsiElement parent = getAttr.getParent();
            if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
            TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
            if (primary == null) continue;
            TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
            if (varExpr == null || !"local".equals(varExpr.getIdentifier().getText())) continue;
            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
            if (getAttrs != null && getAttrs.length > 0 && getAttrs[0] == getAttr) {
                usages.add(getAttr.getIdentifier());
            }
        }
        return usages.isEmpty() ? null : usages.toArray(PsiElement.EMPTY_ARRAY);
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
