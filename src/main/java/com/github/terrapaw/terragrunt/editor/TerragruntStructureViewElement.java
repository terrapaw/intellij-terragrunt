package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntLabel;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectElem;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectExpr;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TerragruntStructureViewElement implements StructureViewTreeElement {

    private final PsiElement element;

    public TerragruntStructureViewElement(@NotNull PsiElement element) {
        this.element = element;
    }

    @Override
    public Object getValue() {
        return element;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (element instanceof com.intellij.pom.Navigatable nav) {
            nav.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return element instanceof com.intellij.pom.Navigatable nav && nav.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        if (element instanceof TerragruntBlock block) {
            String name = TerragruntPsiUtil.getBlockType(block);
            if (name == null) name = "?";
            List<TerragruntLabel> labels = block.getLabelList();
            if (!labels.isEmpty()) {
                name += " \"" + TerragruntPsiUtil.getLabelText(labels.get(0)) + "\"";
            }
            return new PresentationData(name, null, null, null);
        } else if (element instanceof TerragruntAttribute attr) {
            return new PresentationData(attr.getIdentifier().getText(), null, null, null);
        } else if (element instanceof TerragruntObjectElem elem) {
            String name = elem.getIdentifier() != null ? elem.getIdentifier().getText() : "?";
            return new PresentationData(name, null, null, null);
        } else if (element instanceof PsiFile file) {
            return new PresentationData(file.getName(), null, null, null);
        }
        return new PresentationData(element.getText(), null, null, null);
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
        List<TreeElement> children = new ArrayList<>();
        if (element instanceof PsiFile file) {
            // Top-level blocks and attributes
            TerragruntBody body = PsiTreeUtil.getChildOfType(file, TerragruntBody.class);
            if (body != null) {
                for (TerragruntBlock block : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntBlock.class)) {
                    children.add(new TerragruntStructureViewElement(block));
                }
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    children.add(new TerragruntStructureViewElement(attr));
                }
            }
        } else if (element instanceof TerragruntBlock block) {
            // Nested blocks and attributes inside block body
            TerragruntBody body = PsiTreeUtil.getChildOfType(block, TerragruntBody.class);
            if (body != null) {
                for (TerragruntBlock nested : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntBlock.class)) {
                    children.add(new TerragruntStructureViewElement(nested));
                }
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    children.add(new TerragruntStructureViewElement(attr));
                }
            }
        } else if (element instanceof TerragruntAttribute attr) {
            // Show object keys as children
            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
            if (obj != null) {
                for (TerragruntObjectElem elem : obj.getObjectElemList()) {
                    if (elem.getIdentifier() != null) {
                        children.add(new TerragruntStructureViewElement(elem));
                    }
                }
            }
        } else if (element instanceof TerragruntObjectElem elem) {
            // Nested objects inside object elements
            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(elem, TerragruntObjectExpr.class);
            if (obj != null) {
                for (TerragruntObjectElem nested : obj.getObjectElemList()) {
                    if (nested.getIdentifier() != null) {
                        children.add(new TerragruntStructureViewElement(nested));
                    }
                }
            }
        }
        return children.toArray(TreeElement.EMPTY_ARRAY);
    }
}
