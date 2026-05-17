package com.github.joelm.terragrunt.reference;

import com.github.joelm.terragrunt.lang.psi.*;
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
import java.util.Collection;
import java.util.Set;

public class TerragruntReferenceContributor extends PsiReferenceContributor {
    private static final Set<String> PATH_ATTRS = Set.of("config_path", "path");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // File path references in include/dependency blocks
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(TerragruntStringLit.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                        TerragruntAttribute attr = PsiTreeUtil.getParentOfType(element, TerragruntAttribute.class);
                        if (attr == null) return PsiReference.EMPTY_ARRAY;
                        String attrName = attr.getIdentifier().getText();
                        if (!PATH_ATTRS.contains(attrName)) return PsiReference.EMPTY_ARRAY;

                        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                        if (block == null) return PsiReference.EMPTY_ARRAY;
                        String blockType = block.getIdentifier().getText();
                        if (!blockType.equals("include") && !blockType.equals("dependency")) return PsiReference.EMPTY_ARRAY;

                        String text = element.getText();
                        if (text.length() < 3 || text.contains("${")) return PsiReference.EMPTY_ARRAY;
                        String path = text.substring(1, text.length() - 1);

                        return new PsiReference[]{new TerragruntFileReference(element, path, blockType.equals("dependency"))};
                    }
                });

        // local.X, dependency.X, feature.X references
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(TerragruntGetAttr.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                        if (!(element instanceof TerragruntGetAttr getAttr)) return PsiReference.EMPTY_ARRAY;

                        // Walk up to find the postfix expression
                        PsiElement parent = getAttr.getParent();
                        if (!(parent instanceof TerragruntPostfixExpr postfix)) return PsiReference.EMPTY_ARRAY;

                        // Get the root variable name
                        TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                        if (primary == null) return PsiReference.EMPTY_ARRAY;
                        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                        if (varExpr == null) return PsiReference.EMPTY_ARRAY;
                        String rootVar = varExpr.getIdentifier().getText();

                        String attrName = getAttr.getIdentifier().getText();

                        // Check this is the first get_attr
                        PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                        if (getAttrs == null || getAttrs.length == 0 || getAttrs[0] != element) return PsiReference.EMPTY_ARRAY;

                        // Range covers just the identifier part (after the dot)
                        PsiElement id = getAttr.getIdentifier();
                        int offset = id.getStartOffsetInParent();
                        TextRange range = new TextRange(offset, offset + id.getTextLength());

                        if (rootVar.equals("local")) {
                            return new PsiReference[]{new LocalVarReference(element, range, attrName)};
                        }
                        if (rootVar.equals("dependency")) {
                            return new PsiReference[]{new DependencyBlockReference(element, range, attrName)};
                        }
                        if (rootVar.equals("feature")) {
                            return new PsiReference[]{new FeatureBlockReference(element, range, attrName)};
                        }

                        return PsiReference.EMPTY_ARRAY;
                    }
                });
    }

    private static class LocalVarReference extends PsiReferenceBase<PsiElement> {
        private final String varName;

        LocalVarReference(@NotNull PsiElement element, TextRange range, String varName) {
            super(element, range);
            this.varName = varName;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            PsiFile file = myElement.getContainingFile();
            Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
            for (TerragruntBlock block : blocks) {
                if (!"locals".equals(block.getIdentifier().getText())) continue;
                TerragruntBody body = block.getBody();
                if (body == null) continue;
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    if (varName.equals(attr.getIdentifier().getText())) {
                        return attr.getIdentifier();
                    }
                }
            }
            return null;
        }
    }

    private static class DependencyBlockReference extends PsiReferenceBase<PsiElement> {
        private final String depName;

        DependencyBlockReference(@NotNull PsiElement element, TextRange range, String depName) {
            super(element, range);
            this.depName = depName;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            PsiFile file = myElement.getContainingFile();
            Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
            for (TerragruntBlock block : blocks) {
                if (!"dependency".equals(block.getIdentifier().getText())) continue;
                for (TerragruntLabel label : block.getLabelList()) {
                    String labelText = label.getText().replace("\"", "");
                    if (depName.equals(labelText)) {
                        return block.getIdentifier();
                    }
                }
            }
            return null;
        }
    }

    private static class FeatureBlockReference extends PsiReferenceBase<PsiElement> {
        private final String featureName;

        FeatureBlockReference(@NotNull PsiElement element, TextRange range, String featureName) {
            super(element, range);
            this.featureName = featureName;
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            PsiFile file = myElement.getContainingFile();
            Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
            for (TerragruntBlock block : blocks) {
                if (!"feature".equals(block.getIdentifier().getText())) continue;
                for (TerragruntLabel label : block.getLabelList()) {
                    String labelText = label.getText().replace("\"", "");
                    if (featureName.equals(labelText)) {
                        return block.getIdentifier();
                    }
                }
            }
            return null;
        }
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
