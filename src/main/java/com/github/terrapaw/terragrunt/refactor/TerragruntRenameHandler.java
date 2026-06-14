package com.github.terrapaw.terragrunt.refactor;

import com.github.terrapaw.terragrunt.lang.TerragruntFile;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.github.terrapaw.terragrunt.reference.TerragruntChainResolver;
import com.github.terrapaw.terragrunt.reference.TerragruntFileResolver;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TerragruntRenameHandler implements RenameHandler {
    @Override
    public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
        PsiElement element = findTargetElement(dataContext);
        return element != null;
    }

    @Override
    public boolean isRenaming(@NotNull DataContext dataContext) {
        return isAvailableOnDataContext(dataContext);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        PsiElement element = findTargetElement(dataContext);
        if (element == null) return;

        String oldName = element.getText();
        String newName = Messages.showInputDialog(project, "Rename '" + oldName + "' to:", "Rename", null, oldName, null);
        if (newName == null || newName.equals(oldName) || newName.isBlank()) return;

        performRename(project, file, element, oldName, newName);
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
        if (elements.length == 0) return;
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile file = elements[0].getContainingFile();
        if (editor != null && file != null) {
            invoke(project, editor, file, dataContext);
        }
    }

    private PsiElement findTargetElement(DataContext dataContext) {
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        if (editor == null || !(file instanceof TerragruntFile)) return null;

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) return null;

        // Case 1: On attribute name inside locals block
        if (element.getParent() instanceof TerragruntAttribute attr) {
            if (element == attr.getIdentifier()) {
                TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                if (block != null && "locals".equals(TerragruntPsiUtil.getBlockType(block))) {
                    return element;
                }
            }
        }

        // Case 2: On identifier inside a get_attr after "local." or "include."
        if (element.getParent() instanceof TerragruntGetAttr getAttr) {
            PsiElement postfix = getAttr.getParent();
            if (postfix instanceof TerragruntPostfixExpr) {
                TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                if (primary != null) {
                    TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                    if (varExpr != null) {
                        String rootVar = varExpr.getIdentifier().getText();
                        if ("local".equals(rootVar)) return element;
                        if ("include".equals(rootVar)) {
                            // Allow rename on include.X.locals.Y or include.X.inputs.Y (at idx 2+)
                            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                            if (getAttrs != null && getAttrs.length >= 3) {
                                int idx = -1;
                                for (int i = 0; i < getAttrs.length; i++) {
                                    if (getAttrs[i] == getAttr) { idx = i; break; }
                                }
                                if (idx >= 2) {
                                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                                    if ("locals".equals(section) || "inputs".equals(section)) return element;
                                }
                            }
                        }
                        if ("dependency".equals(rootVar)) {
                            // Allow rename on dependency.X.outputs.Y (at idx 2)
                            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                            if (getAttrs != null && getAttrs.length >= 3) {
                                int idx = -1;
                                for (int i = 0; i < getAttrs.length; i++) {
                                    if (getAttrs[i] == getAttr) { idx = i; break; }
                                }
                                if (idx == 2) {
                                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                                    if ("outputs".equals(section)) return element;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Case 3: On object key inside inputs = { ... }, mock_outputs = { ... }, or locals (nested objects)
        if (element.getParent() instanceof TerragruntObjectElem objElem) {
            if (element == objElem.getFirstChild()) {
                TerragruntAttribute attr = PsiTreeUtil.getParentOfType(element, TerragruntAttribute.class);
                if (attr != null && "inputs".equals(attr.getIdentifier().getText())) {
                    return element;
                }
                if (attr != null && "mock_outputs".equals(attr.getIdentifier().getText())) {
                    return element;
                }
                // Case 4: Object key inside locals block (nested objects)
                if (attr != null) {
                    TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                    if (block != null && "locals".equals(TerragruntPsiUtil.getBlockType(block))) {
                        return element;
                    }
                }
            }
        }

        // Case 3b/4b: On STRING_LITERAL inside a quoted object key ("vpc_cidr" = ...)
        if (element.getParent() instanceof TerragruntStringLit) {
            TerragruntObjectElem objElem = PsiTreeUtil.getParentOfType(element, TerragruntObjectElem.class);
            if (objElem != null) {
                PsiElement firstChild = objElem.getFirstChild();
                if (firstChild != null && PsiTreeUtil.isAncestor(firstChild, element, false)) {
                    TerragruntAttribute attr = PsiTreeUtil.getParentOfType(objElem, TerragruntAttribute.class);
                    if (attr != null && "inputs".equals(attr.getIdentifier().getText())) {
                        return element;
                    }
                    if (attr != null) {
                        TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                        if (block != null && "locals".equals(TerragruntPsiUtil.getBlockType(block))) {
                            return element;
                        }
                    }
                }
            }
        }

        return null;
    }

    private void performRename(Project project, PsiFile file, PsiElement source, String oldName, String newName) {
        performRenameForTest(project, file, source, oldName, newName);
    }

    public void performRenameForTest(Project project, PsiFile file, PsiElement source, String oldName, String newName) {
        List<PsiElement> elementsToRename = new ArrayList<>();
        List<PsiElement> crossFileElements = new ArrayList<>();

        // Determine rename type based on source context
        boolean isInputsKey = false;
        boolean isMockOutputsKey = false;
        boolean isDeepKey = false;
        PsiElement keyElement = source;
        // Handle quoted keys: source is STRING_LITERAL inside StringLit
        if (source != null && source.getParent() instanceof TerragruntStringLit stringLit) {
            TerragruntObjectElem objElem = PsiTreeUtil.getParentOfType(source, TerragruntObjectElem.class);
            if (objElem != null && PsiTreeUtil.isAncestor(objElem.getFirstChild(), source, false)) {
                keyElement = stringLit;
            }
        }
        TerragruntObjectElem parentObjElem = PsiTreeUtil.getParentOfType(keyElement, TerragruntObjectElem.class);
        if (parentObjElem != null && PsiTreeUtil.isAncestor(parentObjElem.getFirstChild(), keyElement, false)) {
            TerragruntAttribute attr = PsiTreeUtil.getParentOfType(parentObjElem, TerragruntAttribute.class);
            if (attr != null && "inputs".equals(attr.getIdentifier().getText())) {
                isInputsKey = true;
            } else if (attr != null && "mock_outputs".equals(attr.getIdentifier().getText())) {
                isMockOutputsKey = true;
            } else if (attr != null) {
                TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                if (block != null && "locals".equals(TerragruntPsiUtil.getBlockType(block))) {
                    isDeepKey = true;
                }
            }
        }

        // Check if renaming from a usage (get_attr inside local.X.Y... or include.X.Y... chain)
        boolean isUsageSideRename = false;
        if (!isInputsKey && !isDeepKey && source != null && source.getParent() instanceof TerragruntGetAttr getAttr) {
            PsiElement postfix = getAttr.getParent();
            if (postfix instanceof TerragruntPostfixExpr postfixExpr) {
                TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfixExpr, TerragruntPrimaryExpr.class);
                if (primary != null) {
                    TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                    if (varExpr != null) {
                        String rootVar = varExpr.getIdentifier().getText();
                        PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfixExpr, TerragruntGetAttr.class);
                        if (getAttrs != null && getAttrs.length > 1) {
                            int idx = -1;
                            for (int i = 0; i < getAttrs.length; i++) {
                                if (getAttrs[i] == getAttr) { idx = i; break; }
                            }
                            if ("local".equals(rootVar) && idx > 0) isUsageSideRename = true;
                            if ("include".equals(rootVar) && idx >= 2) isUsageSideRename = true;
                            if ("dependency".equals(rootVar) && idx == 2) {
                                String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                                if ("outputs".equals(section)) isUsageSideRename = true;
                            }
                        }
                    }
                }
            }
        }

        if (isInputsKey) {
            // Find the inputs key definition
            elementsToRename.add(source);
            // Find cross-file usages of inputs key
            findCrossFileInputsUsages(file, oldName, crossFileElements);
        } else if (isMockOutputsKey) {
            // Find the mock_outputs key definition and dependency.X.outputs.Y usages
            elementsToRename.add(source);
            TerragruntBlock depBlock = PsiTreeUtil.getParentOfType(source, TerragruntBlock.class);
            if (depBlock != null && "dependency".equals(TerragruntPsiUtil.getBlockType(depBlock))) {
                List<TerragruntLabel> labels = depBlock.getLabelList();
                if (!labels.isEmpty()) {
                    String depName = TerragruntPsiUtil.getLabelText(labels.get(0));
                    findMockOutputUsagesInFile(file, depName, oldName, elementsToRename);
                }
            }
        } else if (isDeepKey) {
            // Build key path by walking up nested objects from the ObjectElem
            java.util.List<String> keyPath = buildKeyPath(parentObjElem);
            TerragruntAttribute attr = PsiTreeUtil.getParentOfType(parentObjElem, TerragruntAttribute.class);
            String attrName = attr.getIdentifier().getText();
            // Add the definition itself
            elementsToRename.add(source);
            // Find same-file usages: local.attrName.key1.key2...
            findDeepKeyUsagesInFile(file, attrName, keyPath, elementsToRename);
            // Find cross-file usages
            findCrossFileDeepKeyUsages(file, attrName, keyPath, crossFileElements);
        } else if (isUsageSideRename) {
            // Renaming from a usage like local.X.Y or local.alias.locals.X.Y
            // Resolve the chain to find the definition, then rename as deep key
            performUsageSideRename(project, file, source, oldName, newName);
            return;
        } else {
            // Find the definition in locals block
            for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                if (!"locals".equals(TerragruntPsiUtil.getBlockType(block))) continue;
                TerragruntBody body = block.getBody();
                if (body == null) continue;
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    if (oldName.equals(attr.getIdentifier().getText())) {
                        elementsToRename.add(attr.getIdentifier());
                    }
                }
            }
            // Find all local.X usages in same file
            findLocalUsagesInFile(file, oldName, elementsToRename);
            // Find cross-file usages
            findCrossFileUsages(file, oldName, crossFileElements);
        }

        // Perform the rename
        WriteCommandAction.runWriteCommandAction(project, "Rename '" + oldName + "' to '" + newName + "'", null, () -> {
            // Rename in current file
            var document = file.getViewProvider().getDocument();
            if (document != null) {
                elementsToRename.sort((a, b) -> b.getTextOffset() - a.getTextOffset());
                for (PsiElement el : elementsToRename) {
                    replaceElementText(document, el, newName);
                }
            }
            // Rename in other files
            for (PsiElement el : crossFileElements) {
                var otherDoc = el.getContainingFile().getViewProvider().getDocument();
                if (otherDoc != null) {
                    replaceElementText(otherDoc, el, newName);
                }
            }
        });
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

    private void findCrossFileUsages(PsiFile sourceFile, String name, List<PsiElement> usages) {
        Project project = sourceFile.getProject();
        for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
            findCrossFileUsagesRecursive(contentRoot, project, sourceFile, name, usages);
        }
    }

    private void findCrossFileUsagesRecursive(VirtualFile dir, Project project, PsiFile sourceFile, String name, List<PsiElement> usages) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if ((child.getName().startsWith(".") && !child.getName().equals(".terragrunt-stack")) || child.getName().equals("node_modules")) continue;
                findCrossFileUsagesRecursive(child, project, sourceFile, name, usages);
            } else if (child.getName().endsWith(".hcl")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile == null || psiFile.equals(sourceFile)) continue;
                findUsagesInOtherFile(psiFile, name, sourceFile, usages);
            }
        }
    }

    private void findUsagesInOtherFile(PsiFile file, String name, PsiFile sourceFile, List<PsiElement> usages) {
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
            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
            if (getAttrs == null) continue;

            if ("include".equals(rootVar)) {
                // include.X.locals.<name> — getAttr at index 2
                if (getAttrs.length >= 3 && getAttrs[2] == getAttr) {
                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                    if (!"locals".equals(section)) continue;
                    String includeName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                    if (includeBlock == null) continue;
                    PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
                    if (matchesFile(resolved, sourceFile)) usages.add(getAttr.getIdentifier());
                }
            } else if ("local".equals(rootVar)) {
                // local.alias.<name> (depth 1) where alias resolves to sourceFile
                if (getAttrs.length >= 2 && getAttrs[1] == getAttr) {
                    String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(file, aliasName);
                    if (matchesFile(resolved, sourceFile)) usages.add(getAttr.getIdentifier());
                }
                // local.alias.locals.<name> (depth 2)
                if (getAttrs.length >= 3 && getAttrs[2] == getAttr) {
                    String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                    if ("locals".equals(section)) {
                        PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(file, aliasName);
                        if (matchesFile(resolved, sourceFile)) usages.add(getAttr.getIdentifier());
                    }
                }
            }
        }
    }

    private void performUsageSideRename(Project project, PsiFile file, PsiElement source, String oldName, String newName) {
        // source is a get_attr identifier inside local.X.Y... or include.X.Y... chain
        TerragruntGetAttr getAttr = (TerragruntGetAttr) source.getParent();
        TerragruntPostfixExpr postfix = (TerragruntPostfixExpr) getAttr.getParent();
        TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
        String rootVar = varExpr.getIdentifier().getText();
        PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
        if (getAttrs == null || getAttrs.length < 2) return;

        int idx = -1;
        for (int i = 0; i < getAttrs.length; i++) {
            if (getAttrs[i] == getAttr) { idx = i; break; }
        }
        if (idx < 0) return;

        String localName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();

        // Case D: dependency.X.outputs.Y — find mock_outputs key and rename
        if ("dependency".equals(rootVar) && idx == 2 && getAttrs.length >= 3) {
            String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
            if ("outputs".equals(section)) {
                String depName = localName;
                // Find the dependency block and mock_outputs key
                PsiElement mockKey = findMockOutputKeyElement(file, depName, oldName);
                if (mockKey != null) {
                    performRenameForTest(project, file, mockKey, oldName, newName);
                }
                return;
            }
        }

        // Case A: local.attrName.key... (same-file object)
        // Resolve to find the definition attribute in this file or another
        TerragruntAttribute defAttr = TerragruntFileResolver.findLocalAttribute(file, localName);
        PsiFile defFile = file;

        // Case B: include.X.locals.attrName or include.X.inputs.attrName (cross-file)
        if ("include".equals(rootVar) && getAttrs.length >= 3 && idx >= 2) {
            String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
            String includeName = localName;
            TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
            if (includeBlock != null) {
                PsiFile resolvedFile = TerragruntFileResolver.resolveInclude(includeBlock);
                if (resolvedFile != null) {
                    if (idx == 2) {
                        // Renaming the attribute itself
                        String remoteAttrName = ((TerragruntGetAttr) getAttrs[2]).getIdentifier().getText();
                        if ("locals".equals(section)) {
                            TerragruntAttribute remoteAttr = TerragruntFileResolver.findLocalAttribute(resolvedFile, remoteAttrName);
                            if (remoteAttr != null) {
                                performRenameForTest(project, resolvedFile, remoteAttr.getIdentifier(), oldName, newName);
                            }
                        } else if ("inputs".equals(section)) {
                            PsiElement inputKey = TerragruntFileResolver.findInputKey(resolvedFile, remoteAttrName);
                            if (inputKey != null) {
                                performRenameForTest(project, resolvedFile, inputKey, oldName, newName);
                            }
                        }
                        return;
                    }
                    String attrName = ((TerragruntGetAttr) getAttrs[2]).getIdentifier().getText();
                    defAttr = TerragruntFileResolver.findLocalAttribute(resolvedFile, attrName);
                    defFile = resolvedFile;
                }
            }
        }

        // Case C: local.alias.locals.attrName.key... (cross-file)
        if (getAttrs.length >= 3 && idx >= 2) {
            String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
            if ("locals".equals(section) || "inputs".equals(section)) {
                PsiFile resolvedFile = TerragruntChainResolver.resolveLocalAlias(file, localName);
                if (resolvedFile != null) {
                    // idx==2 means renaming the attribute itself (e.g. "a" in local.alias.locals.a)
                    if (idx == 2) {
                        // This is a cross-file locals/inputs attribute rename
                        String remoteAttrName = ((TerragruntGetAttr) getAttrs[2]).getIdentifier().getText();
                        performRenameForTest(project, resolvedFile, 
                                TerragruntFileResolver.findLocalAttribute(resolvedFile, remoteAttrName) != null ?
                                TerragruntFileResolver.findLocalAttribute(resolvedFile, remoteAttrName).getIdentifier() : null,
                                oldName, newName);
                        return;
                    }

                    String attrName = ((TerragruntGetAttr) getAttrs[2]).getIdentifier().getText();
                    defAttr = TerragruntFileResolver.findLocalAttribute(resolvedFile, attrName);
                    defFile = resolvedFile;

                    // If defAttr's value is itself a reference, follow it
                    if (defAttr != null && PsiTreeUtil.findChildOfType(defAttr, TerragruntObjectExpr.class) == null) {
                        // Try resolving through nested alias
                        TerragruntPostfixExpr valPostfix = PsiTreeUtil.findChildOfType(defAttr, TerragruntPostfixExpr.class);
                        if (valPostfix != null) {
                            TerragruntPrimaryExpr valPrimary = PsiTreeUtil.getChildOfType(valPostfix, TerragruntPrimaryExpr.class);
                            if (valPrimary != null) {
                                TerragruntVariableExpr valVar = PsiTreeUtil.getChildOfType(valPrimary, TerragruntVariableExpr.class);
                                if (valVar != null && "local".equals(valVar.getIdentifier().getText())) {
                                    PsiElement[] valGetAttrs = PsiTreeUtil.getChildrenOfType(valPostfix, TerragruntGetAttr.class);
                                    if (valGetAttrs != null && valGetAttrs.length >= 3) {
                                        String valAlias = ((TerragruntGetAttr) valGetAttrs[0]).getIdentifier().getText();
                                        String valSection = ((TerragruntGetAttr) valGetAttrs[1]).getIdentifier().getText();
                                        if ("locals".equals(valSection)) {
                                            PsiFile deepFile = TerragruntChainResolver.resolveLocalAlias(resolvedFile, valAlias);
                                            if (deepFile != null) {
                                                String deepAttr = ((TerragruntGetAttr) valGetAttrs[2]).getIdentifier().getText();
                                                defAttr = TerragruntFileResolver.findLocalAttribute(deepFile, deepAttr);
                                                defFile = deepFile;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (defAttr == null) return;

        // Find the object key in the definition
        PsiElement defKey = findObjectKeyByPath(defAttr, getAttrs, idx, localName);
        if (defKey == null) return;

        // Now delegate: find the enclosing ObjectElem and do definition-side rename
        TerragruntObjectElem defObjElem = PsiTreeUtil.getParentOfType(defKey, TerragruntObjectElem.class);
        if (defObjElem == null) return;

        java.util.List<String> keyPath = buildKeyPath(defObjElem);
        TerragruntAttribute topAttr = PsiTreeUtil.getParentOfType(defObjElem, TerragruntAttribute.class);
        if (topAttr == null) return;
        String attrName = topAttr.getIdentifier().getText();

        List<PsiElement> elementsToRename = new ArrayList<>();
        List<PsiElement> crossFileElements = new ArrayList<>();

        elementsToRename.add(defKey);
        findDeepKeyUsagesInFile(defFile, attrName, keyPath, elementsToRename);
        findCrossFileDeepKeyUsages(defFile, attrName, keyPath, crossFileElements);

        // Also find usages in the current file if it's different from defFile
        if (!defFile.equals(file)) {
            findDeepKeyUsagesInFile(file, attrName, keyPath, crossFileElements);
        }

        final PsiFile finalDefFile = defFile;
        WriteCommandAction.runWriteCommandAction(project, "Rename '" + oldName + "' to '" + newName + "'", null, () -> {
            var defDoc = finalDefFile.getViewProvider().getDocument();
            if (defDoc != null) {
                elementsToRename.sort((a, b) -> b.getTextOffset() - a.getTextOffset());
                for (PsiElement el : elementsToRename) {
                    replaceElementText(defDoc, el, newName);
                }
            }
            for (PsiElement el : crossFileElements) {
                var otherDoc = el.getContainingFile().getViewProvider().getDocument();
                if (otherDoc != null) {
                    replaceElementText(otherDoc, el, newName);
                    otherDoc.replaceString(el.getTextOffset(), el.getTextOffset() + el.getTextLength(), newName);
                }
            }
        });
    }

    @Nullable
    private PsiElement findObjectKeyByPath(TerragruntAttribute attr, PsiElement[] getAttrs, int targetIdx, String localName) {
        TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
        if (obj == null) return null;

        // Determine start index for key walking
        int startIdx = 1; // after local.attrName
        // If chain is local.alias.locals.attrName.key..., start after attrName
        if (getAttrs.length >= 3) {
            String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
            if ("locals".equals(section) || "inputs".equals(section)) {
                startIdx = 3; // after alias.locals.attrName
            }
        }

        for (int i = startIdx; i <= targetIdx; i++) {
            String keyName = ((TerragruntGetAttr) getAttrs[i]).getIdentifier().getText();
            PsiElement foundKey = null;
            for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                PsiElement key = elem.getFirstChild();
                if (key != null && keyName.equals(key.getText().replace("\"", ""))) {
                    foundKey = key;
                    if (i == targetIdx) return key;
                    obj = PsiTreeUtil.findChildOfType(elem, TerragruntObjectExpr.class);
                    break;
                }
            }
            if (foundKey == null || obj == null) return null;
        }
        return null;
    }

    private java.util.List<String> buildKeyPath(PsiElement startElement) {
        java.util.List<String> path = new java.util.ArrayList<>();
        // Start from an ObjectElem — get its key
        if (startElement instanceof TerragruntObjectElem objElem) {
            PsiElement key = objElem.getFirstChild();
            if (key != null) path.add(key.getText().replace("\"", ""));
        }
        // Walk up through parent ObjectElems until we reach the attribute
        PsiElement current = startElement;
        while (true) {
            TerragruntObjectElem parentElem = PsiTreeUtil.getParentOfType(current, TerragruntObjectElem.class);
            if (parentElem == null) break;
            PsiElement parentKey = parentElem.getFirstChild();
            if (parentKey != null) path.add(parentKey.getText().replace("\"", ""));
            current = parentElem;
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private void findDeepKeyUsagesInFile(PsiFile file, String attrName, java.util.List<String> keyPath, List<PsiElement> usages) {
        Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class);
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
            // Expected: local.attrName.key1.key2...
            int expectedLen = keyPath.size() + 1; // attrName + keys
            if (getAttrs.length < expectedLen) continue;
            if (!attrName.equals(((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText())) continue;
            boolean matches = true;
            for (int i = 0; i < keyPath.size(); i++) {
                if (!keyPath.get(i).equals(((TerragruntGetAttr) getAttrs[i + 1]).getIdentifier().getText())) {
                    matches = false;
                    break;
                }
            }
            if (matches) usages.add(getAttr.getIdentifier());
        }
    }

    private void findCrossFileDeepKeyUsages(PsiFile sourceFile, String attrName, java.util.List<String> keyPath, List<PsiElement> usages) {
        Project project = sourceFile.getProject();
        for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
            findCrossFileDeepKeyUsagesRecursive(contentRoot, project, sourceFile, attrName, keyPath, usages);
        }
    }

    private void findCrossFileDeepKeyUsagesRecursive(VirtualFile dir, Project project, PsiFile sourceFile, String attrName, java.util.List<String> keyPath, List<PsiElement> usages) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if ((child.getName().startsWith(".") && !child.getName().equals(".terragrunt-stack")) || child.getName().equals("node_modules")) continue;
                findCrossFileDeepKeyUsagesRecursive(child, project, sourceFile, attrName, keyPath, usages);
            } else if (child.getName().endsWith(".hcl")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile == null || psiFile.equals(sourceFile)) continue;
                // Look for local.alias.locals.attrName.key1.key2...
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

    private void findMockOutputUsagesInFile(PsiFile file, String depName, String outputName, List<PsiElement> usages) {
        // Find dependency.depName.outputs.outputName
        Collection<TerragruntGetAttr> allGetAttrs = PsiTreeUtil.findChildrenOfType(file, TerragruntGetAttr.class);
        for (TerragruntGetAttr getAttr : allGetAttrs) {
            if (!outputName.equals(getAttr.getIdentifier().getText())) continue;
            PsiElement parent = getAttr.getParent();
            if (!(parent instanceof TerragruntPostfixExpr postfix)) continue;
            TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
            if (primary == null) continue;
            TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
            if (varExpr == null || !"dependency".equals(varExpr.getIdentifier().getText())) continue;
            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
            if (getAttrs == null || getAttrs.length < 3 || getAttrs[2] != getAttr) continue;
            if (!depName.equals(((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText())) continue;
            if (!"outputs".equals(((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText())) continue;
            usages.add(getAttr.getIdentifier());
        }
    }

    @Nullable
    private PsiElement findMockOutputKeyElement(PsiFile file, String depName, String keyName) {
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
                    if (key != null && keyName.equals(key.getText().replace("\"", ""))) {
                        return key;
                    }
                }
            }
        }
        return null;
    }

    private void replaceElementText(com.intellij.openapi.editor.Document doc, PsiElement el, String newName) {
        int start = el.getTextOffset();
        int end = start + el.getTextLength();
        // For quoted keys, replace only the content between quotes
        String text = el.getText();
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            start += 1;
            end -= 1;
        }
        doc.replaceString(start, end, newName);
    }

    private boolean matchesFile(PsiFile resolved, PsiFile sourceFile) {
        return resolved != null && resolved.getVirtualFile() != null &&
                sourceFile.getVirtualFile() != null &&
                resolved.getVirtualFile().getPath().equals(sourceFile.getVirtualFile().getPath());
    }

    private boolean aliasResolvesToFile(PsiFile fromFile, String aliasName, String attrName, PsiFile sourceFile) {
        PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(fromFile, aliasName);
        if (resolved == null) return false;
        if (matchesFile(resolved, sourceFile)) return true;
        // Transitive: check if the attribute in the resolved file chains to sourceFile
        TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(resolved, attrName);
        if (attr == null) return false;
        return attributeValueResolvesToFile(attr, resolved, sourceFile, 10);
    }

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
        if (matchesFile(targetFile, sourceFile)) return true;
        TerragruntAttribute targetAttr = TerragruntFileResolver.findLocalAttribute(targetFile, targetAttrName);
        if (targetAttr == null) return false;
        return attributeValueResolvesToFile(targetAttr, targetFile, sourceFile, maxDepth - 1);
    }

    private void findCrossFileInputsUsages(PsiFile sourceFile, String name, List<PsiElement> usages) {
        Project project = sourceFile.getProject();
        for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
            findCrossFileInputsUsagesRecursive(contentRoot, project, sourceFile, name, usages);
        }
    }

    private void findCrossFileInputsUsagesRecursive(VirtualFile dir, Project project, PsiFile sourceFile, String name, List<PsiElement> usages) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if ((child.getName().startsWith(".") && !child.getName().equals(".terragrunt-stack")) || child.getName().equals("node_modules")) continue;
                findCrossFileInputsUsagesRecursive(child, project, sourceFile, name, usages);
            } else if (child.getName().endsWith(".hcl")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile == null || psiFile.equals(sourceFile)) continue;
                findInputsUsagesInOtherFile(psiFile, name, sourceFile, usages);
            }
        }
    }

    private void findInputsUsagesInOtherFile(PsiFile file, String name, PsiFile sourceFile, List<PsiElement> usages) {
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
            PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
            if (getAttrs == null) continue;

            if ("include".equals(rootVar)) {
                // include.X.inputs.<name> — getAttr at index 2
                if (getAttrs.length >= 3 && getAttrs[2] == getAttr) {
                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                    if (!"inputs".equals(section)) continue;
                    String includeName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                    if (includeBlock == null) continue;
                    PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
                    if (matchesFile(resolved, sourceFile)) usages.add(getAttr.getIdentifier());
                }
            } else if ("local".equals(rootVar)) {
                // local.alias.inputs.<name> (depth 2)
                if (getAttrs.length >= 3 && getAttrs[2] == getAttr) {
                    String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                    if ("inputs".equals(section)) {
                        PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(file, aliasName);
                        if (matchesFile(resolved, sourceFile)) usages.add(getAttr.getIdentifier());
                    }
                }
            }
        }
    }
}
