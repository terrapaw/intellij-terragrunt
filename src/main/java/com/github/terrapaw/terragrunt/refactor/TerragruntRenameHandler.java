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

        // Case 2: On identifier inside a get_attr after "local."
        if (element.getParent() instanceof TerragruntGetAttr getAttr) {
            PsiElement postfix = getAttr.getParent();
            if (postfix instanceof TerragruntPostfixExpr) {
                TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                if (primary != null) {
                    TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                    if (varExpr != null && "local".equals(varExpr.getIdentifier().getText())) {
                        return element;
                    }
                }
            }
        }

        // Case 3: On object key inside inputs = { ... }
        if (element.getParent() instanceof TerragruntObjectElem objElem) {
            if (element == objElem.getFirstChild()) {
                TerragruntAttribute attr = PsiTreeUtil.getParentOfType(element, TerragruntAttribute.class);
                if (attr != null && "inputs".equals(attr.getIdentifier().getText())) {
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
        boolean isDeepKey = false;
        if (source != null && source.getParent() instanceof TerragruntObjectElem) {
            TerragruntAttribute attr = PsiTreeUtil.getParentOfType(source, TerragruntAttribute.class);
            if (attr != null && "inputs".equals(attr.getIdentifier().getText())) {
                isInputsKey = true;
            } else if (attr != null) {
                TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                if (block != null && "locals".equals(TerragruntPsiUtil.getBlockType(block))) {
                    isDeepKey = true;
                }
            }
        }

        if (isInputsKey) {
            // Find the inputs key definition
            elementsToRename.add(source);
            // Find cross-file usages of inputs key
            findCrossFileInputsUsages(file, oldName, crossFileElements);
        } else if (isDeepKey) {
            // Build key path by walking up nested objects
            java.util.List<String> keyPath = buildKeyPath(source);
            TerragruntAttribute attr = PsiTreeUtil.getParentOfType(source, TerragruntAttribute.class);
            String attrName = attr.getIdentifier().getText();
            // Add the definition itself
            elementsToRename.add(source);
            // Find same-file usages: local.attrName.key1.key2...
            findDeepKeyUsagesInFile(file, attrName, keyPath, elementsToRename);
            // Find cross-file usages
            findCrossFileDeepKeyUsages(file, attrName, keyPath, crossFileElements);
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
                    document.replaceString(el.getTextOffset(), el.getTextOffset() + el.getTextLength(), newName);
                }
            }
            // Rename in other files
            for (PsiElement el : crossFileElements) {
                var otherDoc = el.getContainingFile().getViewProvider().getDocument();
                if (otherDoc != null) {
                    otherDoc.replaceString(el.getTextOffset(), el.getTextOffset() + el.getTextLength(), newName);
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

    private java.util.List<String> buildKeyPath(PsiElement keyElement) {
        java.util.List<String> path = new java.util.ArrayList<>();
        path.add(keyElement.getText().replace("\"", ""));
        PsiElement current = keyElement.getParent(); // TerragruntObjectElem
        while (current != null) {
            if (current instanceof TerragruntObjectElem) {
                current = current.getParent(); // TerragruntObjectExpr
            } else if (current instanceof TerragruntObjectExpr) {
                PsiElement objParent = current.getParent();
                if (objParent instanceof TerragruntObjectElem parentElem) {
                    PsiElement parentKey = parentElem.getFirstChild();
                    if (parentKey != null) path.add(parentKey.getText().replace("\"", ""));
                    current = parentElem;
                } else {
                    break;
                }
            } else {
                break;
            }
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
                    // Verify the alias resolves to our source file
                    String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                    PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(psiFile, aliasName);
                    if (matchesFile(resolved, sourceFile)) usages.add(getAttr.getIdentifier());
                }
            }
        }
    }

    private boolean matchesFile(PsiFile resolved, PsiFile sourceFile) {
        return resolved != null && resolved.getVirtualFile() != null &&
                sourceFile.getVirtualFile() != null &&
                resolved.getVirtualFile().getPath().equals(sourceFile.getVirtualFile().getPath());
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
