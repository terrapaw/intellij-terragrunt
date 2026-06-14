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

        return null;
    }

    private void performRename(Project project, PsiFile file, PsiElement source, String oldName, String newName) {
        performRenameForTest(project, file, source, oldName, newName);
    }

    public void performRenameForTest(Project project, PsiFile file, PsiElement source, String oldName, String newName) {
        List<PsiElement> elementsToRename = new ArrayList<>();

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
        List<PsiElement> crossFileElements = new ArrayList<>();
        findCrossFileUsages(file, oldName, crossFileElements);

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

    private boolean matchesFile(PsiFile resolved, PsiFile sourceFile) {
        return resolved != null && resolved.getVirtualFile() != null &&
                sourceFile.getVirtualFile() != null &&
                resolved.getVirtualFile().getPath().equals(sourceFile.getVirtualFile().getPath());
    }
}
