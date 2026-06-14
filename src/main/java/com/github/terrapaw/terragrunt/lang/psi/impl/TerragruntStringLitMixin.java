package com.github.terrapaw.terragrunt.lang.psi.impl;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.github.terrapaw.terragrunt.reference.TerragruntChainResolver;
import com.github.terrapaw.terragrunt.reference.TerragruntFileResolver;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Mixin for TerragruntStringLit that provides PsiReferences for path strings.
 * This ensures the full string gets a unified Ctrl+hover underline.
 */
public class TerragruntStringLitMixin extends ASTWrapperPsiElement {
    private static final Set<String> PATH_ATTRS = Set.of("config_path", "path", "source");

    public TerragruntStringLitMixin(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        String text = getText();
        if (text.length() < 3) return PsiReference.EMPTY_ARRAY;
        if (!text.contains("${")) return PsiReference.EMPTY_ARRAY; // Plain strings handled by ReferenceContributor

        // Check if inside read_terragrunt_config() argument
        TerragruntFunctionCall funcCall = PsiTreeUtil.getParentOfType(this, TerragruntFunctionCall.class);
        if (funcCall != null && "read_terragrunt_config".equals(funcCall.getIdentifier().getText())) {
            return new PsiReference[]{new InterpolatedPathReference(this)};
        }

        // Check if inside a path attribute
        TerragruntAttribute attr = PsiTreeUtil.getParentOfType(this, TerragruntAttribute.class);
        if (attr == null) return PsiReference.EMPTY_ARRAY;
        String attrName = attr.getIdentifier().getText();
        if (!PATH_ATTRS.contains(attrName)) return PsiReference.EMPTY_ARRAY;

        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
        if (block == null) return PsiReference.EMPTY_ARRAY;
        String blockType = TerragruntPsiUtil.getBlockType(block);
        if (!"include".equals(blockType) && !"dependency".equals(blockType) && !"terraform".equals(blockType)) return PsiReference.EMPTY_ARRAY;

        return new PsiReference[]{new InterpolatedPathReference(this)};
    }

    private static class InterpolatedPathReference extends PsiReferenceBase<PsiElement> {
        InterpolatedPathReference(@NotNull PsiElement element) {
            super(element, new TextRange(1, element.getTextLength() - 1));
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            PsiFile file = myElement.getContainingFile();
            VirtualFile vFile = file.getVirtualFile();
            if (vFile == null || vFile.getParent() == null) return null;

            String text = myElement.getText();
            if (text.length() < 3) return null;
            String content = text.substring(1, text.length() - 1);

            String path = TerragruntFileResolver.evaluateInterpolatedPathPublic(content, file);
            if (path == null) return null;

            VirtualFile dir = vFile.getParent();
            VirtualFile current;
            if (path.startsWith("/")) {
                VirtualFile root = vFile;
                while (root.getParent() != null) root = root.getParent();
                current = root;
                for (String part : path.substring(1).split("/")) {
                    if (current == null || part.isEmpty()) continue;
                    current = current.findChild(part);
                }
            } else {
                current = dir;
                for (String part : path.split("/")) {
                    if (current == null) break;
                    if ("..".equals(part)) current = current.getParent();
                    else if (!".".equals(part) && !part.isEmpty()) current = current.findChild(part);
                }
            }
            if (current == null) return null;

            PsiManager psiManager = PsiManager.getInstance(myElement.getProject());
            if (current.isDirectory()) return psiManager.findDirectory(current);
            return psiManager.findFile(current);
        }
    }
}
