package com.github.terrapaw.terragrunt.reference;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
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

        // Case 3: On an IDENTIFIER that is a key inside an object
        if (parent instanceof TerragruntObjectElem) {
            // Check if this is the key (first child) of the object elem
            if (sourceElement == parent.getFirstChild()) {
                // Check if inside a values attribute in a unit block
                TerragruntAttribute valuesAttr = PsiTreeUtil.getParentOfType(sourceElement, TerragruntAttribute.class);
                if (valuesAttr != null && "values".equals(valuesAttr.getIdentifier().getText())) {
                    TerragruntBlock unitBlock = PsiTreeUtil.getParentOfType(valuesAttr, TerragruntBlock.class);
                    if (unitBlock != null && "unit".equals(TerragruntPsiUtil.getBlockType(unitBlock))) {
                        return handleValuesKeyUsages(sourceElement, unitBlock);
                    }
                }
                // Check if inside inputs attribute
                TerragruntAttribute inputsAttr = PsiTreeUtil.getParentOfType(sourceElement, TerragruntAttribute.class);
                if (inputsAttr != null && "inputs".equals(inputsAttr.getIdentifier().getText())) {
                    return handleInputsKeyUsages(sourceElement);
                }
                // Check if inside a locals attribute value — find usages of local.attr.key...
                return handleLocalsObjectKeyUsages(sourceElement);
            }
        }

        // Case 3b: On a STRING_LITERAL inside a quoted object key (e.g. "vpc_cidr" = ...)
        if (parent instanceof TerragruntStringLit) {
            TerragruntObjectElem objElem = PsiTreeUtil.getParentOfType(sourceElement, TerragruntObjectElem.class);
            if (objElem != null) {
                PsiElement firstChild = objElem.getFirstChild();
                // Check the string_lit is in key position (part of the first expression)
                if (firstChild != null && PsiTreeUtil.isAncestor(firstChild, sourceElement, false)) {
                    TerragruntAttribute inputsAttr = PsiTreeUtil.getParentOfType(objElem, TerragruntAttribute.class);
                    if (inputsAttr != null && "inputs".equals(inputsAttr.getIdentifier().getText())) {
                        return handleInputsKeyUsages(sourceElement);
                    }
                    return handleLocalsObjectKeyUsages(firstChild);
                }
            }
        }

        // Case 4: On a label of a feature/dependency block — find usages
        if (parent instanceof TerragruntLabel labelNode) {
            TerragruntBlock block = PsiTreeUtil.getParentOfType(parent, TerragruntBlock.class);
            if (block != null) {
                String blockType = TerragruntPsiUtil.getBlockType(block);
                String labelName = TerragruntPsiUtil.getLabelText(labelNode);
                PsiFile file = sourceElement.getContainingFile();
                if ("feature".equals(blockType) || "dependency".equals(blockType)) {
                    return findBlockReferenceUsages(file, blockType, labelName);
                }
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

        // Handle include.X.locals.Y / include.X.inputs.Y (and deeper chains)
        if ("include".equals(rootVar) && getAttrs.length >= 3 && cursorIndex >= 2) {
            String includeName = chain[0];
            String section = chain[1]; // "locals" or "inputs"

            TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
            if (includeBlock == null) return null;
            PsiFile targetFile = TerragruntFileResolver.resolveInclude(includeBlock);
            if (targetFile == null) return null;

            if (cursorIndex == 2) {
                // Simple case: include.X.locals.Y
                String attrName = chain[2];
                if ("locals".equals(section)) {
                    TerragruntAttribute resolved = TerragruntFileResolver.findLocalAttribute(targetFile, attrName);
                    if (resolved != null) return new PsiElement[]{resolved};
                } else if ("inputs".equals(section)) {
                    PsiElement resolved = TerragruntFileResolver.findInputKey(targetFile, attrName);
                    if (resolved != null) return new PsiElement[]{resolved};
                }
            } else if ("locals".equals(section) && cursorIndex > 2) {
                // Deep chain: include.X.locals.alias.locals.Y...
                // Walk the chain from index 2 onward, resolving through aliases
                return resolveDeepChain(targetFile, chain, 2, cursorIndex, file);
            }
        }

        // Handle first get_attr (depth 0)
        if (cursorIndex == 0) {
            String attrName = chain[0];
            if ("local".equals(rootVar)) return resolveLocal(file, attrName);
            if ("dependency".equals(rootVar)) return resolveDependency(file, attrName);
            if ("feature".equals(rootVar)) return resolveFeature(file, attrName);
            if ("values".equals(rootVar)) return resolveValuesKey(file, attrName);
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

        // Handle dependency.X.outputs.Y at depth 2 — navigate to mock_outputs key
        if ("dependency".equals(rootVar) && cursorIndex == 2 && getAttrs.length >= 3) {
            String depName = chain[0];
            String section = chain[1];
            String outputName = chain[2];
            if ("outputs".equals(section)) {
                PsiElement target = findMockOutputKey(file, depName, outputName);
                if (target != null) return new PsiElement[]{target};
            }
        }

        // Handle feature.X.value at depth 1 — navigate to the default attribute
        if ("feature".equals(rootVar) && cursorIndex == 1 && getAttrs.length >= 2) {
            String featureName = chain[0];
            String attr = chain[1];
            if ("value".equals(attr)) {
                PsiElement target = findFeatureDefault(file, featureName);
                if (target != null) return new PsiElement[]{target};
            }
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
            // Fallback: navigate into object value keys
            TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(file, aliasName);
            if (attr != null) {
                PsiElement key = findObjectKey(attr, attrName);
                if (key != null) return new PsiElement[]{key};
            }
        }

        // Handle local.X.locals.Y at depth 2+ — resolve via read_terragrunt_config or include alias
        if ("local".equals(rootVar) && cursorIndex >= 2 && getAttrs.length >= 3) {
            String aliasName = chain[0];
            String section = chain[1]; // "locals" or "inputs"
            if (cursorIndex == 2) {
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
                        PsiElement resolved = TerragruntFileResolver.findInputKey(resolvedFile, attrName);
                        if (resolved != null) return new PsiElement[]{resolved};
                    }
                }
            } else if ("locals".equals(section) || "inputs".equals(section)) {
                // Deep chain: local.alias.locals.nested_alias.locals.Y...
                PsiFile resolvedFile = resolveLocalAlias(file, aliasName);
                if (resolvedFile != null) {
                    PsiElement[] result = resolveDeepChain(resolvedFile, chain, 2, cursorIndex, file);
                    if (result != null) return result;
                    // Fallback: navigate into object keys in the resolved file
                    // e.g. local.common.locals.network.vpc_cidr → find "network" attr in resolved file, walk into object
                    if ("locals".equals(section) && cursorIndex > 2) {
                        String attrName = chain[2];
                        TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(resolvedFile, attrName);
                        if (attr != null) {
                            PsiElement key = findNestedObjectKey(attr, chain, 3, cursorIndex);
                            if (key != null) return new PsiElement[]{key};
                            // If attr value is a reference (not object literal), resolve through the alias chain
                            PsiElement resolved = resolveAttributeValueChain(attr, resolvedFile, chain, 3, cursorIndex);
                            if (resolved != null) return new PsiElement[]{resolved};
                        }
                    }
                }
            }
            // Fallback: navigate into object value keys at any depth
            TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(file, aliasName);
            if (attr != null) {
                PsiElement key = findNestedObjectKey(attr, chain, 1, cursorIndex);
                if (key != null) return new PsiElement[]{key};
            }
        }

        // Fallback for local.X.Y where X is an object (cursorIndex == 1 already handled above)
        if ("local".equals(rootVar) && cursorIndex >= 1) {
            String localName = chain[0];
            TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(file, localName);
            if (attr != null) {
                PsiElement key = findNestedObjectKey(attr, chain, 1, cursorIndex);
                if (key != null) return new PsiElement[]{key};
            }
        }

        return null;
    }

    /**
     * Resolves a deep chain like include.X.locals.alias.locals.Y by walking through aliases.
     * At each step, resolves the current name as a local alias to a file, then continues.
     */
    @Nullable
    private PsiElement[] resolveDeepChain(PsiFile currentFile, String[] chain, int startIndex, int cursorIndex, PsiFile originFile) {
        PsiFile file = currentFile;
        int i = startIndex;

        // Walk through the chain, resolving aliases until we reach the cursor position
        while (i <= cursorIndex && file != null) {
            String name = chain[i];

            if (i == cursorIndex) {
                // We're at the cursor — look up this name in the current file
                String prevSection = (i > 0) ? chain[i - 1] : "";
                if ("locals".equals(prevSection)) {
                    TerragruntAttribute resolved = TerragruntFileResolver.findLocalAttribute(file, name);
                    if (resolved != null) return new PsiElement[]{resolved};
                } else if ("inputs".equals(prevSection)) {
                    PsiElement resolved = TerragruntFileResolver.findInputKey(file, name);
                    if (resolved != null) return new PsiElement[]{resolved};
                }
                return null;
            }

            // Check if next element is "locals" or "inputs" — if so, this name is an alias to resolve
            if (i + 1 < chain.length && ("locals".equals(chain[i + 1]) || "inputs".equals(chain[i + 1]))) {
                PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(file, name);
                if (resolved == null) return null;
                file = resolved;
                i += 2; // skip alias name and "locals"/"inputs"
            } else {
                i++;
            }
        }

        return null;
    }

    /**
     * Resolves a local variable that is assigned include.X.locals or read_terragrunt_config(...) to the target file.
     */
    @Nullable
    private PsiFile resolveLocalAlias(PsiFile file, String aliasName) {
        return TerragruntChainResolver.resolveLocalAlias(file, aliasName);
    }

    private PsiElement[] handleDefinitionToUsages(TerragruntAttribute attr, PsiElement sourceElement) {
        // Only if this is the attribute name identifier (not part of the value expression)
        if (sourceElement.getTextOffset() != attr.getIdentifier().getTextOffset()) return null;

        // Check if we're inside a locals block
        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
        if (block == null) return null;
        String blockType = TerragruntPsiUtil.getBlockType(block);

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
                if ((child.getName().startsWith(".") && !child.getName().equals(".terragrunt-stack")) || child.getName().equals("node_modules")) continue;
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
            if (!"locals".equals(TerragruntPsiUtil.getBlockType(block))) continue;
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

    @Nullable
    private PsiElement[] findBlockReferenceUsages(PsiFile file, String blockType, String labelName) {
        List<PsiElement> usages = new ArrayList<>();
        Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class);
        for (TerragruntGetAttr getAttr : allGetAttrs) {
            if (!labelName.equals(getAttr.getIdentifier().getText())) continue;
            PsiElement parent = getAttr.getParent();
            if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
            TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
            if (primary == null) continue;
            TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
            if (varExpr == null || !blockType.equals(varExpr.getIdentifier().getText())) continue;
            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
            if (getAttrs != null && getAttrs.length > 0 && getAttrs[0] == getAttr) {
                usages.add(getAttr.getIdentifier());
            }
        }
        return usages.isEmpty() ? null : usages.toArray(PsiElement.EMPTY_ARRAY);
    }

    @Nullable
    private PsiElement findFeatureDefault(PsiFile file, String featureName) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"feature".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            List<TerragruntLabel> labels = block.getLabelList();
            if (labels.isEmpty() || !featureName.equals(TerragruntPsiUtil.getLabelText(labels.get(0)))) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if ("default".equals(attr.getIdentifier().getText())) {
                    return attr.getIdentifier();
                }
            }
        }
        return null;
    }

    @Nullable
    private PsiElement findMockOutputKey(PsiFile file, String depName, String outputName) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"dependency".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            List<TerragruntLabel> labels = block.getLabelList();
            if (labels.isEmpty() || !depName.equals(TerragruntPsiUtil.getLabelText(labels.get(0)))) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (!"mock_outputs".equals(attr.getIdentifier().getText())) continue;
                TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
                if (obj == null) continue;
                for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                    PsiElement key = elem.getFirstChild();
                    if (key != null && outputName.equals(key.getText())) {
                        return key;
                    }
                }
            }
        }
        return null;
    }

    private PsiElement[] resolveLocal(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(TerragruntPsiUtil.getBlockType(block))) continue;
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

    /**
     * Finds usages of an object key inside a locals attribute value.
     * Builds the path (local.attrName.key1.key2...) and searches for matching get_attr chains.
     */
    @Nullable
    private PsiElement[] handleLocalsObjectKeyUsages(PsiElement keyElement) {
        // Build the key path by walking up through nested objects
        java.util.List<String> path = new java.util.ArrayList<>();
        path.add(keyElement.getText().replace("\"", ""));
        PsiElement current = keyElement.getParent(); // TerragruntObjectElem
        while (current != null) {
            if (current instanceof TerragruntObjectElem) {
                current = current.getParent(); // TerragruntObjectExpr
            } else if (current instanceof TerragruntObjectExpr) {
                PsiElement objParent = current.getParent();
                if (objParent instanceof TerragruntObjectElem) {
                    // Nested object — add parent key to path
                    PsiElement parentKey = objParent.getFirstChild();
                    if (parentKey != null) path.add(parentKey.getText().replace("\"", ""));
                    current = objParent;
                } else {
                    // Walk up through expression wrappers to find the attribute
                    PsiElement walker = objParent;
                    while (walker != null && !(walker instanceof TerragruntAttribute)) {
                        if (walker instanceof TerragruntObjectElem) {
                            // Hit a parent object elem — add its key
                            PsiElement parentKey = walker.getFirstChild();
                            if (parentKey != null) path.add(parentKey.getText().replace("\"", ""));
                        }
                        walker = walker.getParent();
                    }
                    if (walker instanceof TerragruntAttribute attr) {
                        String attrName = attr.getIdentifier().getText();
                        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                        if (block == null || !"locals".equals(TerragruntPsiUtil.getBlockType(block))) return null;

                        java.util.Collections.reverse(path);
                        PsiFile file = keyElement.getContainingFile();
                        List<PsiElement> usages = new ArrayList<>();
                        findObjectKeyUsages(file, attrName, path, usages);
                        // Also search other project files for cross-file usages
                        findCrossFileObjectKeyUsages(file, attrName, path, usages);
                        return usages.isEmpty() ? null : usages.toArray(PsiElement.EMPTY_ARRAY);
                    }
                    break;
                }
            } else {
                break;
            }
        }
        return null;
    }

    private void findObjectKeyUsages(PsiFile file, String localName, java.util.List<String> keyPath, List<PsiElement> usages) {
        Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class);
        for (TerragruntGetAttr getAttr : allGetAttrs) {
            // Check if this matches the last key in our path
            String lastKey = keyPath.get(keyPath.size() - 1);
            if (!lastKey.equals(getAttr.getIdentifier().getText())) continue;

            PsiElement parent = getAttr.getParent();
            if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
            TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
            if (primary == null) continue;
            TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
            if (varExpr == null || !"local".equals(varExpr.getIdentifier().getText())) continue;

            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
            if (getAttrs == null) continue;

            // Check chain matches: local.localName.key1.key2...lastKey
            int expectedLen = keyPath.size() + 1; // +1 for localName
            if (getAttrs.length < expectedLen) continue;

            // First get_attr should be the localName
            if (!localName.equals(((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText())) continue;

            // Remaining should match keyPath
            boolean matches = true;
            for (int i = 0; i < keyPath.size(); i++) {
                if (!keyPath.get(i).equals(((TerragruntGetAttr) getAttrs[i + 1]).getIdentifier().getText())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                usages.add(getAttr.getIdentifier());
            }
        }
    }

    /**
     * Scans project files for cross-file object key usages.
     * Looks for local.X.locals.attrName.key1.key2... where X resolves to sourceFile.
     */
    private void findCrossFileObjectKeyUsages(PsiFile sourceFile, String attrName, java.util.List<String> keyPath, List<PsiElement> usages) {
        com.intellij.openapi.project.Project project = sourceFile.getProject();
        com.intellij.openapi.roots.ProjectRootManager rootManager =
                com.intellij.openapi.roots.ProjectRootManager.getInstance(project);
        for (com.intellij.openapi.vfs.VirtualFile contentRoot : rootManager.getContentRoots()) {
            findCrossFileObjectKeyUsagesRecursive(contentRoot, project, sourceFile, attrName, keyPath, usages);
        }
    }

    private void findCrossFileObjectKeyUsagesRecursive(com.intellij.openapi.vfs.VirtualFile dir,
                                                        com.intellij.openapi.project.Project project,
                                                        PsiFile sourceFile, String attrName,
                                                        java.util.List<String> keyPath, List<PsiElement> usages) {
        for (com.intellij.openapi.vfs.VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if ((child.getName().startsWith(".") && !child.getName().equals(".terragrunt-stack")) || child.getName().equals("node_modules")) continue;
                findCrossFileObjectKeyUsagesRecursive(child, project, sourceFile, attrName, keyPath, usages);
            } else if (child.getName().endsWith(".hcl")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile == null || psiFile.equals(sourceFile)) continue;
                // Look for local.X.locals.attrName.key... chains
                Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(psiFile, TerragruntGetAttr.class);
                for (TerragruntGetAttr getAttr : allGetAttrs) {
                    String lastKey = keyPath.get(keyPath.size() - 1);
                    if (!lastKey.equals(getAttr.getIdentifier().getText())) continue;

                    PsiElement parent = getAttr.getParent();
                    if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
                    TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                    if (primary == null) continue;
                    TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                    if (varExpr == null || !"local".equals(varExpr.getIdentifier().getText())) continue;

                    PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                    if (getAttrs == null) continue;

                    // Expected: local.alias.locals.attrName.key1.key2...
                    int expectedLen = keyPath.size() + 3; // alias + "locals" + attrName + keys
                    if (getAttrs.length < expectedLen) continue;

                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                    if (!"locals".equals(section)) continue;

                    String remoteAttr = ((TerragruntGetAttr) getAttrs[2]).getIdentifier().getText();
                    if (!attrName.equals(remoteAttr)) continue;

                    // Check remaining keys match
                    boolean matches = true;
                    for (int i = 0; i < keyPath.size(); i++) {
                        if (getAttrs.length <= i + 3) { matches = false; break; }
                        if (!keyPath.get(i).equals(((TerragruntGetAttr) getAttrs[i + 3]).getIdentifier().getText())) {
                            matches = false;
                            break;
                        }
                    }
                    if (!matches) continue;

                    // Verify the alias resolves to our source file (directly or transitively)
                    String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    if (aliasResolvesToFile(psiFile, aliasName, remoteAttr, sourceFile)) {
                        usages.add(getAttr.getIdentifier());
                    }
                }
            }
        }
    }

    /**
     * Checks if an alias chain ultimately resolves to the source file for a given attribute.
     * Handles arbitrary depth: alias → file1, file1.attr → file2, file2.attr → sourceFile.
     */
    private boolean aliasResolvesToFile(PsiFile fromFile, String aliasName, String attrName, PsiFile sourceFile) {
        PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(fromFile, aliasName);
        if (resolved == null) return false;
        // Direct match
        if (resolved.getVirtualFile() != null && sourceFile.getVirtualFile() != null &&
                resolved.getVirtualFile().getPath().equals(sourceFile.getVirtualFile().getPath())) {
            return true;
        }
        // Transitive: check if the attribute in the resolved file chains to sourceFile
        TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(resolved, attrName);
        if (attr == null) return false;
        return attributeValueResolvesToFile(attr, resolved, sourceFile, 10);
    }

    /**
     * Recursively checks if an attribute's value expression chains to the source file.
     */
    private boolean attributeValueResolvesToFile(TerragruntAttribute attr, PsiFile attrFile, PsiFile sourceFile, int maxDepth) {
        if (maxDepth <= 0) return false;
        TerragruntPostfixExpr postfix = PsiTreeUtil.findChildOfType(attr, TerragruntPostfixExpr.class);
        if (postfix == null) return false;
        TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
        if (primary == null) return false;
        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
        if (varExpr == null || !"local".equals(varExpr.getIdentifier().getText())) return false;

        PsiElement[] valueGetAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
        if (valueGetAttrs == null || valueGetAttrs.length < 3) return false;

        String valueAlias = ((TerragruntGetAttr) valueGetAttrs[0]).getIdentifier().getText();
        String valueSection = ((TerragruntGetAttr) valueGetAttrs[1]).getIdentifier().getText();
        if (!"locals".equals(valueSection)) return false;

        String targetAttrName = ((TerragruntGetAttr) valueGetAttrs[2]).getIdentifier().getText();
        PsiFile targetFile = TerragruntChainResolver.resolveLocalAlias(attrFile, valueAlias);
        if (targetFile == null) return false;

        if (targetFile.getVirtualFile() != null && sourceFile.getVirtualFile() != null &&
                targetFile.getVirtualFile().getPath().equals(sourceFile.getVirtualFile().getPath())) {
            return true;
        }
        // Recurse
        TerragruntAttribute targetAttr = TerragruntFileResolver.findLocalAttribute(targetFile, targetAttrName);
        if (targetAttr == null) return false;
        return attributeValueResolvesToFile(targetAttr, targetFile, sourceFile, maxDepth - 1);
    }

    /**
     * Finds a key in an attribute's object value.
     */
    @Nullable
    private PsiElement findObjectKey(TerragruntAttribute attr, String keyName) {
        TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
        return findKeyInObject(obj, keyName);
    }

    /**
     * Finds a key inside an object expression.
     */
    @Nullable
    private PsiElement findKeyInObject(TerragruntObjectExpr obj, String keyName) {
        if (obj == null) return null;
        for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
            PsiElement key = elem.getFirstChild();
            if (key != null && keyName.equals(key.getText().replace("\"", ""))) {
                return key;
            }
        }
        return null;
    }

    /**
     * Resolves an attribute whose value is a reference expression (e.g. local.abc.locals.a)
     * to find the actual object, then walks remaining keys into it.
     */
    @Nullable
    private PsiElement resolveAttributeValueChain(TerragruntAttribute attr, PsiFile attrFile, String[] chain, int startIndex, int targetIndex) {
        // Check if the attribute value is a postfix expression (local.X.locals.Y pattern)
        TerragruntPostfixExpr postfix = PsiTreeUtil.findChildOfType(attr, TerragruntPostfixExpr.class);
        if (postfix == null) return null;
        TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
        if (primary == null) return null;
        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
        if (varExpr == null || !"local".equals(varExpr.getIdentifier().getText())) return null;

        PsiElement[] valueGetAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
        if (valueGetAttrs == null || valueGetAttrs.length < 2) return null;

        // Resolve the alias chain in the value expression
        String valueAlias = ((TerragruntGetAttr) valueGetAttrs[0]).getIdentifier().getText();
        String valueSection = ((TerragruntGetAttr) valueGetAttrs[1]).getIdentifier().getText();
        if (!"locals".equals(valueSection) && !"inputs".equals(valueSection)) return null;

        PsiFile targetFile = TerragruntChainResolver.resolveLocalAlias(attrFile, valueAlias);
        if (targetFile == null) return null;

        // Find the referenced attribute in the target file
        if (valueGetAttrs.length >= 3) {
            String targetAttrName = ((TerragruntGetAttr) valueGetAttrs[2]).getIdentifier().getText();
            TerragruntAttribute targetAttr = TerragruntFileResolver.findLocalAttribute(targetFile, targetAttrName);
            if (targetAttr == null) return null;

            // Now walk the remaining keys from chain[startIndex..targetIndex] into this attribute's object
            PsiElement key = findNestedObjectKey(targetAttr, chain, startIndex, targetIndex);
            if (key != null) return key;
            // Recurse if this attribute is also a reference
            return resolveAttributeValueChain(targetAttr, targetFile, chain, startIndex, targetIndex);
        }
        return null;
    }

    /**
     * Navigates into nested object keys following a chain of names.
     * Returns the final key element, or null if any step fails.
     */
    @Nullable
    private PsiElement findNestedObjectKey(TerragruntAttribute attr, String[] chain, int startIndex, int targetIndex) {
        TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
        for (int i = startIndex; i <= targetIndex && obj != null; i++) {
            String keyName = chain[i];
            PsiElement key = findKeyInObject(obj, keyName);
            if (key == null) return null;
            if (i == targetIndex) return key;
            // Descend into the value's object
            PsiElement parent = key.getParent(); // TerragruntObjectElem
            obj = PsiTreeUtil.findChildOfType(parent, TerragruntObjectExpr.class);
        }
        return null;
    }

    private PsiElement[] resolveDependency(PsiFile file, String name) {
        TerragruntBlock block = TerragruntPsiUtil.findBlock(file, "dependency", name);
        return block != null ? new PsiElement[]{block} : null;
    }

    private PsiElement[] handleValuesKeyUsages(PsiElement keyElement, TerragruntBlock unitBlock) {
        String keyName = keyElement.getText();
        PsiFile stackFile = keyElement.getContainingFile();
        com.intellij.openapi.vfs.VirtualFile stackVf = stackFile.getVirtualFile();
        if (stackVf == null) return null;
        com.intellij.openapi.vfs.VirtualFile stackDir = stackVf.getParent();
        if (stackDir == null) return null;

        // Get the unit's path attribute
        TerragruntBody body = unitBlock.getBody();
        if (body == null) return null;
        String unitPath = null;
        for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
            if ("path".equals(attr.getIdentifier().getText())) {
                PsiElement value = attr.getLastChild();
                if (value != null) unitPath = value.getText().replace("\"", "").trim();
            }
        }
        if (unitPath == null) return null;

        // Find the unit's terragrunt.hcl (in .terragrunt-stack/<path>/)
        com.intellij.openapi.vfs.VirtualFile unitDir = stackDir.findFileByRelativePath(".terragrunt-stack/" + unitPath);
        if (unitDir == null || !unitDir.isDirectory()) {
            // Fallback: try without .terragrunt-stack/ prefix
            unitDir = stackDir.findFileByRelativePath(unitPath);
        }
        if (unitDir == null || !unitDir.isDirectory()) return null;
        com.intellij.openapi.vfs.VirtualFile unitFile = unitDir.findChild("terragrunt.hcl");
        if (unitFile == null) return null;

        PsiFile psiUnitFile = PsiManager.getInstance(stackFile.getProject()).findFile(unitFile);
        if (psiUnitFile == null) return null;

        // Find values.keyName references
        List<PsiElement> usages = new ArrayList<>();
        for (TerragruntGetAttr getAttr : PsiTreeUtil.findChildrenOfType(psiUnitFile, TerragruntGetAttr.class)) {
            if (!keyName.equals(getAttr.getIdentifier().getText())) continue;
            PsiElement parent = getAttr.getParent();
            if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
            TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
            if (primary == null) continue;
            TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
            if (varExpr == null || !"values".equals(varExpr.getIdentifier().getText())) continue;
            usages.add(getAttr.getIdentifier());
        }
        return usages.isEmpty() ? null : usages.toArray(PsiElement.EMPTY_ARRAY);
    }

    private PsiElement[] resolveFeature(PsiFile file, String name) {
        TerragruntBlock block = TerragruntPsiUtil.findBlock(file, "feature", name);
        return block != null ? new PsiElement[]{block} : null;
    }

    private PsiElement[] resolveValuesKey(PsiFile file, String keyName) {
        // Find the parent terragrunt.stack.hcl
        com.intellij.openapi.vfs.VirtualFile vf = file.getVirtualFile();
        if (vf == null) return null;
        com.intellij.openapi.vfs.VirtualFile dir = vf.getParent();
        if (dir == null) return null;

        // Walk up to find terragrunt.stack.hcl, tracking the relative path
        com.intellij.openapi.vfs.VirtualFile stackVf = null;
        com.intellij.openapi.vfs.VirtualFile searchDir = dir.getParent();
        String relativePath = dir.getName();
        while (searchDir != null) {
            stackVf = searchDir.findChild("terragrunt.stack.hcl");
            if (stackVf != null) break;
            relativePath = searchDir.getName() + "/" + relativePath;
            searchDir = searchDir.getParent();
        }
        if (stackVf == null) return null;

        // The unit path in the stack file is relative (without .terragrunt-stack/ prefix)
        // Strip the .terragrunt-stack/ prefix if present
        String unitRelPath = relativePath;
        if (unitRelPath.startsWith(".terragrunt-stack/")) {
            unitRelPath = unitRelPath.substring(".terragrunt-stack/".length());
        }

        PsiFile stackFile = PsiManager.getInstance(file.getProject()).findFile(stackVf);
        if (stackFile == null) return null;

        // Find the unit block whose path matches
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(stackFile, TerragruntBlock.class)) {
            if (!"unit".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;

            String unitPath = null;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if ("path".equals(attr.getIdentifier().getText())) {
                    PsiElement value = attr.getLastChild();
                    if (value != null) {
                        unitPath = value.getText().replace("\"", "").trim();
                    }
                }
            }
            if (unitPath == null || !unitPath.equals(unitRelPath)) continue;

            // Find the values attribute and navigate to the key
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if ("values".equals(attr.getIdentifier().getText())) {
                    var objExpr = PsiTreeUtil.findChildOfType(attr, com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectExpr.class);
                    if (objExpr == null) continue;
                    for (var elem : objExpr.getObjectElemList()) {
                        if (elem.getIdentifier() != null && keyName.equals(elem.getIdentifier().getText())) {
                            return new PsiElement[]{elem.getIdentifier()};
                        }
                    }
                }
            }
        }
        return null;
    }
}
