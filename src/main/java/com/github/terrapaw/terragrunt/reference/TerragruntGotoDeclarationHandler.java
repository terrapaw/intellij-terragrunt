package com.github.terrapaw.terragrunt.reference;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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

        // Case 3: On an IDENTIFIER that is a key inside an inputs map
        // (e.g. Ctrl+B on "notification_email" in inputs = { notification_email = "x" })
        if (parent instanceof TerragruntObjectElem) {
            // Check if this is the key (first child) of the object elem
            if (sourceElement == parent.getFirstChild()) {
                return handleInputsKeyUsages(sourceElement);
            }
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
        if (getAttrs == null || getAttrs.length == 0) return null;

        // Determine which get_attr the cursor is on
        int cursorIndex = -1;
        for (int i = 0; i < getAttrs.length; i++) {
            if (getAttrs[i] == getAttr) { cursorIndex = i; break; }
        }
        if (cursorIndex < 0) return null;

        // Build the chain of names
        String[] chain = new String[getAttrs.length];
        for (int i = 0; i < getAttrs.length; i++) {
            chain[i] = ((TerragruntGetAttr) getAttrs[i]).getIdentifier().getText();
        }

        PsiFile file = sourceElement.getContainingFile();

        // Handle include.X.locals.Y / include.X.inputs.Y
        if ("include".equals(rootVar) && getAttrs.length >= 3 && cursorIndex == 2) {
            String includeName = chain[0];
            String section = chain[1]; // "locals" or "inputs"
            String attrName = chain[2];

            TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
            if (includeBlock == null) return null;
            PsiFile targetFile = TerragruntFileResolver.resolveInclude(includeBlock);
            if (targetFile == null) return null;

            if ("locals".equals(section)) {
                TerragruntAttribute resolved = TerragruntFileResolver.findLocalAttribute(targetFile, attrName);
                if (resolved != null) return new PsiElement[]{resolved};
            } else if ("inputs".equals(section)) {
                TerragruntAttribute resolved = TerragruntFileResolver.findInputAttribute(targetFile, attrName);
                if (resolved != null) return new PsiElement[]{resolved};
            }
        }

        // Handle first get_attr (depth 0)
        if (cursorIndex == 0) {
            String attrName = chain[0];
            if ("local".equals(rootVar)) return resolveLocal(file, attrName);
            if ("dependency".equals(rootVar)) return resolveDependency(file, attrName);
            if ("feature".equals(rootVar)) return resolveFeature(file, attrName);
            if ("include".equals(rootVar)) {
                // include.X -> navigate to the include block
                TerragruntBlock block = TerragruntFileResolver.findIncludeBlock(file, attrName);
                if (block != null) return new PsiElement[]{block};
            }
        }

        // Handle include.X at depth 1 — navigate to the included file
        if ("include".equals(rootVar) && cursorIndex == 1) {
            String includeName = chain[0];
            TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
            if (includeBlock == null) return null;
            PsiFile targetFile = TerragruntFileResolver.resolveInclude(includeBlock);
            if (targetFile != null) return new PsiElement[]{targetFile};
        }

        // Handle local.alias.Y at depth 1 — resolve alias to included file's locals
        if ("local".equals(rootVar) && cursorIndex == 1 && getAttrs.length >= 2) {
            String aliasName = chain[0];
            String attrName = chain[1];
            // Find the alias assignment: locals { alias = include.X.locals }
            PsiFile resolvedFile = resolveLocalAlias(file, aliasName);
            if (resolvedFile != null) {
                TerragruntAttribute resolved = TerragruntFileResolver.findLocalAttribute(resolvedFile, attrName);
                if (resolved != null) return new PsiElement[]{resolved};
            }
        }

        // Handle local.X.locals.Y at depth 2 — resolve via read_terragrunt_config or include alias
        if ("local".equals(rootVar) && cursorIndex == 2 && getAttrs.length >= 3) {
            String aliasName = chain[0];
            String section = chain[1]; // "locals" or "inputs"
            String attrName = chain[2];
            if ("locals".equals(section)) {
                PsiFile resolvedFile = resolveLocalAlias(file, aliasName);
                if (resolvedFile != null) {
                    TerragruntAttribute resolved = TerragruntFileResolver.findLocalAttribute(resolvedFile, attrName);
                    if (resolved != null) return new PsiElement[]{resolved};
                }
            } else if ("inputs".equals(section)) {
                PsiFile resolvedFile = resolveLocalAlias(file, aliasName);
                if (resolvedFile != null) {
                    TerragruntAttribute resolved = TerragruntFileResolver.findInputAttribute(resolvedFile, attrName);
                    if (resolved != null) return new PsiElement[]{resolved};
                }
            }
        }

        return null;
    }

    /**
     * Resolves a local variable that is assigned include.X.locals or read_terragrunt_config(...) to the target file.
     */
    @Nullable
    private PsiFile resolveLocalAlias(PsiFile file, String aliasName) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (!aliasName.equals(attr.getIdentifier().getText())) continue;

                // Pattern 1: include.X.locals
                TerragruntPostfixExpr postfix = PsiTreeUtil.findChildOfType(attr, TerragruntPostfixExpr.class);
                if (postfix != null) {
                    TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                    if (primary != null) {
                        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                        if (varExpr != null && "include".equals(varExpr.getIdentifier().getText())) {
                            PsiElement[] aliasGetAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                            if (aliasGetAttrs != null && aliasGetAttrs.length >= 2) {
                                String section = ((TerragruntGetAttr) aliasGetAttrs[1]).getIdentifier().getText();
                                if ("locals".equals(section)) {
                                    String includeName = ((TerragruntGetAttr) aliasGetAttrs[0]).getIdentifier().getText();
                                    TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                                    if (includeBlock != null) {
                                        PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
                                        if (resolved != null) return resolved;
                                    }
                                }
                            }
                        }

                        // Pattern 2: read_terragrunt_config(find_in_parent_folders("X")) or read_terragrunt_config("path")
                        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(primary, TerragruntFunctionCall.class);
                        if (funcCall != null && "read_terragrunt_config".equals(funcCall.getIdentifier().getText())) {
                            PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
                            if (resolved != null) return resolved;
                        }
                    }
                }

                // Pattern 2 fallback: read_terragrunt_config at expression level
                TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
                if (funcCall != null && "read_terragrunt_config".equals(funcCall.getIdentifier().getText())) {
                    PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
                    if (resolved != null) return resolved;
                }
            }
        }
        return null;
    }

    private PsiElement[] handleDefinitionToUsages(TerragruntAttribute attr, PsiElement sourceElement) {
        // Only if this is the attribute name identifier (not part of the value expression)
        if (sourceElement.getTextOffset() != attr.getIdentifier().getTextOffset()) return null;

        // Check if we're inside a locals block
        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
        if (block == null) return null;
        String blockType = block.getIdentifier().getText();

        String name = attr.getIdentifier().getText();
        PsiFile file = sourceElement.getContainingFile();

        if ("locals".equals(blockType)) {
            List<PsiElement> usages = new ArrayList<>();
            // Find local.X usages in same file
            findLocalUsagesInFile(file, name, usages);
            // Find include.*.locals.X usages in project files
            findCrossFileLocalUsages(file, name, usages);
            return usages.isEmpty() ? null : usages.toArray(PsiElement.EMPTY_ARRAY);
        }
        return null;
    }

    private PsiElement[] handleInputsKeyUsages(PsiElement sourceElement) {
        String name = sourceElement.getText();
        PsiFile file = sourceElement.getContainingFile();

        // Verify this key is inside an "inputs" attribute
        TerragruntAttribute inputsAttr = PsiTreeUtil.getParentOfType(sourceElement, TerragruntAttribute.class);
        if (inputsAttr == null || !"inputs".equals(inputsAttr.getIdentifier().getText())) return null;

        // Search project files for include.*.inputs.<name> and local.*.inputs.<name>
        List<PsiElement> usages = new ArrayList<>();
        com.intellij.openapi.roots.ProjectRootManager rootManager =
                com.intellij.openapi.roots.ProjectRootManager.getInstance(file.getProject());
        for (com.intellij.openapi.vfs.VirtualFile contentRoot : rootManager.getContentRoots()) {
            findHclFilesRecursive(contentRoot, file.getProject(), file, name, usages);
        }
        return usages.isEmpty() ? null : usages.toArray(PsiElement.EMPTY_ARRAY);
    }

    private void findLocalUsagesInFile(PsiFile file, String name, List<PsiElement> usages) {
        Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class);
        for (TerragruntGetAttr getAttr : allGetAttrs) {
            if (!name.equals(getAttr.getIdentifier().getText())) continue;
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
    }

    private void findCrossFileLocalUsages(PsiFile sourceFile, String name, List<PsiElement> usages) {
        com.intellij.openapi.project.Project project = sourceFile.getProject();
        // Use content roots to find all project directories
        com.intellij.openapi.roots.ProjectRootManager rootManager =
                com.intellij.openapi.roots.ProjectRootManager.getInstance(project);
        for (com.intellij.openapi.vfs.VirtualFile contentRoot : rootManager.getContentRoots()) {
            findHclFilesRecursive(contentRoot, project, sourceFile, name, usages);
        }
    }

    private void findHclFilesRecursive(com.intellij.openapi.vfs.VirtualFile dir,
                                        com.intellij.openapi.project.Project project,
                                        PsiFile sourceFile, String name, List<PsiElement> usages) {
        for (com.intellij.openapi.vfs.VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                findHclFilesRecursive(child, project, sourceFile, name, usages);
            } else if (child.getName().endsWith(".hcl")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile != null && !psiFile.equals(sourceFile)) {
                    findIncludeLocalsUsagesInFile(psiFile, name, sourceFile, usages);
                }
            }
        }
    }

    private void findIncludeLocalsUsagesInFile(PsiFile file, String name, PsiFile sourceFile, List<PsiElement> usages) {
        // Pattern 1: include.X.locals.<name> directly
        Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class);
        for (TerragruntGetAttr getAttr : allGetAttrs) {
            if (!name.equals(getAttr.getIdentifier().getText())) continue;
            PsiElement parent = getAttr.getParent();
            if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
            TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
            if (primary == null) continue;
            TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
            if (varExpr == null) continue;

            String rootVar = varExpr.getIdentifier().getText();

            if ("include".equals(rootVar)) {
                // Check chain: include.X.locals.<name> — getAttr should be at index 2
                PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                if (getAttrs == null || getAttrs.length < 3 || getAttrs[2] != getAttr) continue;
                String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                if (!"locals".equals(section) && !"inputs".equals(section)) continue;

                String includeName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                if (includeBlock == null) continue;
                PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
                if (resolved != null && resolved.getVirtualFile() != null &&
                        sourceFile.getVirtualFile() != null &&
                        resolved.getVirtualFile().getPath().equals(sourceFile.getVirtualFile().getPath())) {
                    usages.add(getAttr.getIdentifier());
                }
            } else if ("local".equals(rootVar)) {
                PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                if (getAttrs == null) continue;

                // Pattern 2a: local.alias.<name> (depth 1) where alias = include.X.locals
                if (getAttrs.length >= 2 && getAttrs[1] == getAttr) {
                    String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    if (isAliasForIncludeLocals(file, aliasName, sourceFile)) {
                        usages.add(getAttr.getIdentifier());
                    }
                }

                // Pattern 2b: local.alias.locals.<name> or local.alias.inputs.<name> (depth 2)
                if (getAttrs.length >= 3 && getAttrs[2] == getAttr) {
                    String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                    if (("locals".equals(section) || "inputs".equals(section)) && isAliasForIncludeLocals(file, aliasName, sourceFile)) {
                        usages.add(getAttr.getIdentifier());
                    }
                }
            }
        }
    }

    private boolean isAliasForIncludeLocals(PsiFile file, String aliasName, PsiFile sourceFile) {
        // Find: locals { aliasName = include.X.locals } or locals { aliasName = read_terragrunt_config(...) }
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (!aliasName.equals(attr.getIdentifier().getText())) continue;

                // Pattern 1: include.X.locals
                TerragruntPostfixExpr postfix = PsiTreeUtil.findChildOfType(attr, TerragruntPostfixExpr.class);
                if (postfix != null) {
                    TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                    if (primary != null) {
                        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                        if (varExpr != null && "include".equals(varExpr.getIdentifier().getText())) {
                            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                            if (getAttrs != null && getAttrs.length >= 2) {
                                String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                                if ("locals".equals(section)) {
                                    String includeName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                                    TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                                    if (includeBlock != null) {
                                        PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
                                        if (matchesSourceFile(resolved, sourceFile)) return true;
                                    }
                                }
                            }
                        }
                    }
                }

                // Pattern 2: read_terragrunt_config(...)
                TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
                if (funcCall != null && "read_terragrunt_config".equals(funcCall.getIdentifier().getText())) {
                    PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
                    if (matchesSourceFile(resolved, sourceFile)) return true;
                }
            }
        }
        return false;
    }

    private boolean matchesSourceFile(@Nullable PsiFile resolved, PsiFile sourceFile) {
        return resolved != null && resolved.getVirtualFile() != null &&
                sourceFile.getVirtualFile() != null &&
                resolved.getVirtualFile().getPath().equals(sourceFile.getVirtualFile().getPath());
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
