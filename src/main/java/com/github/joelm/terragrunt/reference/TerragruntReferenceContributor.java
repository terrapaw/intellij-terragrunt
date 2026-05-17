package com.github.joelm.terragrunt.reference;

import com.github.joelm.terragrunt.lang.psi.TerragruntAttribute;
import com.github.joelm.terragrunt.lang.psi.TerragruntBlock;
import com.github.joelm.terragrunt.lang.psi.TerragruntStringLit;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

public class TerragruntReferenceContributor extends PsiReferenceContributor {
    private static final Set<String> PATH_ATTRS = Set.of("config_path", "path");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(TerragruntStringLit.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                        TerragruntAttribute attr = PsiTreeUtil.getParentOfType(element, TerragruntAttribute.class);
                        if (attr == null) return PsiReference.EMPTY_ARRAY;
                        PsiElement nameEl = attr.getFirstChild();
                        if (nameEl == null || !PATH_ATTRS.contains(nameEl.getText())) return PsiReference.EMPTY_ARRAY;

                        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                        if (block == null) return PsiReference.EMPTY_ARRAY;
                        String blockType = block.getFirstChild() != null ? block.getFirstChild().getText() : "";
                        if (!blockType.equals("include") && !blockType.equals("dependency")) return PsiReference.EMPTY_ARRAY;

                        String text = element.getText();
                        if (text.length() < 3 || text.contains("${")) return PsiReference.EMPTY_ARRAY;
                        String path = text.substring(1, text.length() - 1);

                        return new PsiReference[]{new TerragruntFileReference(element, path, blockType.equals("dependency"))};
                    }
                });
    }

    private static class TerragruntFileReference extends PsiReferenceBase<PsiElement> {
        private final String path;
        private final boolean isDirectory;

        TerragruntFileReference(@NotNull PsiElement element, String path, boolean isDirectory) {
            super(element, new TextRange(1, element.getTextLength() - 1));
            this.path = path;
            this.isDirectory = isDirectory;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            VirtualFile vFile = myElement.getContainingFile().getVirtualFile();
            if (vFile == null) return null;
            File baseDir = new File(vFile.getParent().getPath());
            File target = new File(baseDir, path);
            if (isDirectory) {
                target = new File(target, "terragrunt.hcl");
            }
            VirtualFile resolved = LocalFileSystem.getInstance().findFileByIoFile(target);
            if (resolved == null) return null;
            return PsiManager.getInstance(myElement.getProject()).findFile(resolved);
        }
    }
}
