package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntFunctionCall;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntStringLit;
import com.intellij.codeInspection.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

public class TerragruntUnresolvedPathInspection extends LocalInspectionTool {
    private static final Set<String> PATH_ATTRS = Set.of("config_path", "path");

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntAttribute attr)) return;
                PsiElement nameElement = attr.getFirstChild();
                if (nameElement == null) return;
                String attrName = nameElement.getText();
                if (!PATH_ATTRS.contains(attrName)) return;

                // Check parent block type
                TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
                if (block == null) return;
                String blockType = block.getFirstChild() != null ? block.getFirstChild().getText() : "";
                if (!blockType.equals("include") && !blockType.equals("dependency")) return;

                // Try to find string literal value (skip if it contains function calls)
                TerragruntStringLit stringLit = PsiTreeUtil.findChildOfType(attr, TerragruntStringLit.class);
                TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
                if (funcCall != null) return; // Can't statically resolve function calls
                if (stringLit == null) return;

                String path = stringLit.getText();
                if (path.length() < 3) return;
                path = path.substring(1, path.length() - 1); // strip quotes
                if (path.contains("${")) return; // skip interpolated strings

                // Resolve relative path
                PsiFile file = attr.getContainingFile();
                VirtualFile vFile = file.getVirtualFile();
                if (vFile == null) return;
                File baseDir = new File(vFile.getParent().getPath());
                File target = new File(baseDir, path);

                if (blockType.equals("dependency")) {
                    // dependency config_path points to a directory
                    File tgFile = new File(target, "terragrunt.hcl");
                    if (!target.isDirectory() && !tgFile.exists()) {
                        holder.registerProblem(stringLit,
                                "Cannot resolve path '" + path + "'",
                                ProblemHighlightType.WARNING);
                    }
                } else {
                    // include path points to a file
                    if (!target.exists()) {
                        holder.registerProblem(stringLit,
                                "Cannot resolve file '" + path + "'",
                                ProblemHighlightType.WARNING);
                    }
                }
            }
        };
    }
}
