package com.github.terrapaw.terragrunt.reference;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Resolves file paths in include/dependency blocks to actual PsiFiles.
 */
public class TerragruntFileResolver {

    /**
     * Resolves the file referenced by an include block's path attribute.
     */
    @Nullable
    public static PsiFile resolveInclude(TerragruntBlock includeBlock) {
        if (!"include".equals(includeBlock.getIdentifier().getText())) return null;
        TerragruntBody body = includeBlock.getBody();
        if (body == null) return null;

        for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
            if (!"path".equals(attr.getIdentifier().getText())) continue;
            return resolvePathExpression(attr, includeBlock.getContainingFile());
        }
        return null;
    }

    /**
     * Resolves a path expression — handles find_in_parent_folders("X") and string literals.
     */
    @Nullable
    private static PsiFile resolvePathExpression(TerragruntAttribute attr, PsiFile sourceFile) {
        // Check for find_in_parent_folders("X") function call
        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
        if (funcCall != null && "find_in_parent_folders".equals(funcCall.getIdentifier().getText())) {
            TerragruntArgList argList = funcCall.getArgList();
            if (argList != null) {
                TerragruntStringLit stringLit = PsiTreeUtil.findChildOfType(argList, TerragruntStringLit.class);
                if (stringLit != null) {
                    String fileName = extractStringContent(stringLit.getText());
                    return findInParentFolders(sourceFile, fileName);
                }
            }
            // No args — default to finding terragrunt.hcl or root.hcl
            return findInParentFolders(sourceFile, "root.hcl");
        }

        // Check for plain string literal path
        TerragruntStringLit stringLit = PsiTreeUtil.findChildOfType(attr, TerragruntStringLit.class);
        if (stringLit != null) {
            String path = extractStringContent(stringLit.getText());
            if (path != null && !path.contains("${")) {
                return resolveRelativePath(sourceFile, path);
            }
        }

        return null;
    }

    @Nullable
    private static PsiFile findInParentFolders(PsiFile sourceFile, String fileName) {
        VirtualFile vFile = sourceFile.getVirtualFile();
        if (vFile == null) return null;

        VirtualFile dir = vFile.getParent();
        while (dir != null) {
            dir = dir.getParent();
            if (dir == null) break;
            VirtualFile target = dir.findChild(fileName);
            if (target != null && !target.isDirectory()) {
                return PsiManager.getInstance(sourceFile.getProject()).findFile(target);
            }
        }
        return null;
    }

    @Nullable
    private static PsiFile resolveRelativePath(PsiFile sourceFile, String path) {
        VirtualFile vFile = sourceFile.getVirtualFile();
        if (vFile == null) return null;
        VirtualFile dir = vFile.getParent();
        if (dir == null) return null;

        // Navigate the relative path using VirtualFile API
        String[] parts = path.split("/");
        VirtualFile current = dir;
        for (String part : parts) {
            if (current == null) return null;
            if ("..".equals(part)) {
                current = current.getParent();
            } else if (!".".equals(part)) {
                current = current.findChild(part);
            }
        }
        if (current == null || current.isDirectory()) return null;
        return PsiManager.getInstance(sourceFile.getProject()).findFile(current);
    }

    /**
     * Finds an include block by its label name.
     */
    @Nullable
    public static TerragruntBlock findIncludeBlock(PsiFile file, String labelName) {
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        for (TerragruntBlock block : blocks) {
            if (!"include".equals(block.getIdentifier().getText())) continue;
            for (TerragruntLabel label : block.getLabelList()) {
                if (labelName.equals(label.getText().replace("\"", ""))) {
                    return block;
                }
            }
        }
        return null;
    }

    /**
     * Finds an attribute in a locals block of the given file.
     */
    @Nullable
    public static TerragruntAttribute findLocalAttribute(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (name.equals(attr.getIdentifier().getText())) {
                    return attr;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String extractStringContent(String quotedText) {
        if (quotedText == null || quotedText.length() < 2) return null;
        // Remove surrounding quotes (may be multiple STRING_LITERAL tokens concatenated)
        String content = quotedText.replaceAll("^\"+|\"+$", "");
        return content.isEmpty() ? null : content;
    }

    /**
     * Resolves read_terragrunt_config(find_in_parent_folders("X")) or read_terragrunt_config("path")
     */
    @Nullable
    public static PsiFile resolveReadTerragruntConfig(TerragruntFunctionCall funcCall, PsiFile sourceFile) {
        TerragruntArgList argList = funcCall.getArgList();
        if (argList == null) return null;

        // Check for nested find_in_parent_folders("X") as first argument
        TerragruntFunctionCall nestedFunc = PsiTreeUtil.findChildOfType(argList, TerragruntFunctionCall.class);
        if (nestedFunc != null && "find_in_parent_folders".equals(nestedFunc.getIdentifier().getText())) {
            TerragruntArgList nestedArgs = nestedFunc.getArgList();
            if (nestedArgs != null) {
                TerragruntStringLit stringLit = PsiTreeUtil.findChildOfType(nestedArgs, TerragruntStringLit.class);
                if (stringLit != null) {
                    String fileName = extractStringContent(stringLit.getText());
                    if (fileName != null) return findInParentFolders(sourceFile, fileName);
                }
            }
            return findInParentFolders(sourceFile, "root.hcl");
        }

        // Check for plain string path as first argument
        TerragruntStringLit stringLit = PsiTreeUtil.findChildOfType(argList, TerragruntStringLit.class);
        if (stringLit != null) {
            String path = extractStringContent(stringLit.getText());
            if (path != null && !path.contains("${")) {
                return resolveRelativePath(sourceFile, path);
            }
        }

        return null;
    }
}
