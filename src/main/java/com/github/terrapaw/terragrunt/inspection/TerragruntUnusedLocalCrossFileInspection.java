package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.github.terrapaw.terragrunt.reference.TerragruntChainResolver;
import com.github.terrapaw.terragrunt.reference.TerragruntFileResolver;
import com.intellij.codeInspection.*;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Cross-file unused local inspection. Scans the entire project to determine if
 * a local variable is referenced from other files via include.X.locals.Y,
 * read_terragrunt_config aliases, etc.
 *
 * Disabled by default due to performance cost on large projects.
 */
public class TerragruntUnusedLocalCrossFileInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                VirtualFile vFile = file.getVirtualFile();
                if (vFile == null) return;

                // Collect local.X references within this file
                Set<String> usedLocally = new HashSet<>();
                for (TerragruntGetAttr getAttr : PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class)) {
                    PsiElement parent = getAttr.getParent();
                    if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
                    TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                    if (primary == null) continue;
                    TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                    if (varExpr == null || !"local".equals(varExpr.getIdentifier().getText())) continue;
                    PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                    if (getAttrs != null && getAttrs.length > 0) {
                        usedLocally.add(((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText());
                    }
                }

                // Get externally-referenced locals (cached)
                Set<String> usedExternally = getExternallyReferencedLocals(file);

                // Check each locals block attribute
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                    if (!"locals".equals(TerragruntPsiUtil.getBlockType(block))) continue;
                    TerragruntBody body = block.getBody();
                    if (body == null) continue;
                    for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                        String name = attr.getIdentifier().getText();
                        if (!usedLocally.contains(name) && !usedExternally.contains(name) && !isSuppressedFor(attr)) {
                            holder.registerProblem(attr.getIdentifier(),
                                    "Unused local variable '" + name + "' (not referenced locally or from other files)",
                                    ProblemHighlightType.LIKE_UNUSED_SYMBOL);
                        }
                    }
                }
            }
        };
    }

    private Set<String> getExternallyReferencedLocals(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            Set<String> refs = scanProjectForReferences(file);
            return CachedValueProvider.Result.create(refs, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    private Set<String> scanProjectForReferences(PsiFile sourceFile) {
        Set<String> referenced = new HashSet<>();
        VirtualFile sourceVFile = sourceFile.getVirtualFile();
        if (sourceVFile == null) return referenced;

        var project = sourceFile.getProject();
        for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
            scanDirForReferences(contentRoot, project, sourceFile, sourceVFile, referenced);
        }
        return referenced;
    }

    private void scanDirForReferences(VirtualFile dir, com.intellij.openapi.project.Project project,
                                       PsiFile sourceFile, VirtualFile sourceVFile, Set<String> referenced) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if (child.getName().startsWith(".") && !child.getName().equals(".terragrunt-stack")) continue;
                if ("node_modules".equals(child.getName())) continue;
                scanDirForReferences(child, project, sourceFile, sourceVFile, referenced);
            } else if (child.getName().endsWith(".hcl") && !child.equals(sourceVFile)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile == null) continue;
                findReferencesToFile(psiFile, sourceFile, referenced);
            }
        }
    }

    private void findReferencesToFile(PsiFile consumer, PsiFile sourceFile, Set<String> referenced) {
        for (TerragruntGetAttr getAttr : PsiTreeUtil.findChildrenOfType(consumer, TerragruntGetAttr.class)) {
            PsiElement parent = getAttr.getParent();
            if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
            TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
            if (primary == null) continue;
            TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
            if (varExpr == null) continue;
            String rootVar = varExpr.getIdentifier().getText();
            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
            if (getAttrs == null || getAttrs.length < 3) continue;

            String alias = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
            String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
            if (!"locals".equals(section)) continue;
            String attrName = ((TerragruntGetAttr) getAttrs[2]).getIdentifier().getText();

            // include.X.locals.Y — check if include X resolves to sourceFile
            if ("include".equals(rootVar)) {
                PsiFile resolved = resolveIncludeByLabel(consumer, alias);
                if (resolved != null && isSameFile(resolved, sourceFile)) {
                    referenced.add(attrName);
                }
            }
            // local.X.locals.Y — X is a read_terragrunt_config alias
            else if ("local".equals(rootVar)) {
                PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(consumer, alias);
                if (resolved != null && isSameFile(resolved, sourceFile)) {
                    referenced.add(attrName);
                }
            }
        }
    }

    private boolean isSameFile(PsiFile a, PsiFile b) {
        VirtualFile va = a.getVirtualFile();
        VirtualFile vb = b.getVirtualFile();
        return va != null && vb != null && va.getPath().equals(vb.getPath());
    }

    @org.jetbrains.annotations.Nullable
    private PsiFile resolveIncludeByLabel(PsiFile file, String label) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"include".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            var labels = block.getLabelList();
            if (!labels.isEmpty() && label.equals(TerragruntPsiUtil.getLabelText(labels.get(0)))) {
                return TerragruntFileResolver.resolveInclude(block);
            }
        }
        return null;
    }
}
