package com.github.terrapaw.terragrunt.toolwindow;

import com.github.terrapaw.terragrunt.lang.TerragruntFileDetection;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TerragruntDependencyScanner {

    public record DependencyNode(VirtualFile file, String displayName, List<String> dependencyPaths) {}

    /**
     * Scans the project for terragrunt.hcl files and extracts their dependency relationships.
     */
    public static List<DependencyNode> scanProject(@NotNull Project project) {
        List<DependencyNode> nodes = new ArrayList<>();
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);

        // Scan content roots
        com.intellij.openapi.roots.ProjectRootManager rootManager =
                com.intellij.openapi.roots.ProjectRootManager.getInstance(project);
        for (VirtualFile contentRoot : rootManager.getContentRoots()) {
            List<VirtualFile> hclFiles = new ArrayList<>();
            collectTerragruntFiles(contentRoot, hclFiles);

            PsiManager psiManager = PsiManager.getInstance(project);
            VirtualFile displayBase = baseDir != null ? baseDir : contentRoot;
            for (VirtualFile file : hclFiles) {
                PsiFile psiFile = psiManager.findFile(file);
                if (psiFile == null) continue;

                List<String> deps = extractDependencies(psiFile, file);
                String displayName = getRelativePath(displayBase, file);
                nodes.add(new DependencyNode(file, displayName, deps));
            }
        }
        return nodes;
    }

    private static void collectTerragruntFiles(VirtualFile dir, List<VirtualFile> result) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if (!child.getName().startsWith(".") || child.getName().equals(".terragrunt-stack")) {
                    collectTerragruntFiles(child, result);
                }
            } else if ("terragrunt.hcl".equals(child.getName())) {
                result.add(child);
            }
        }
    }

    private static List<String> extractDependencies(PsiFile psiFile, VirtualFile file) {
        List<String> deps = new ArrayList<>();
        VirtualFile dir = file.getParent();
        if (dir == null) return deps;

        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(psiFile, TerragruntBlock.class)) {
            if (block.getIdentifier() == null) continue;
            String type = TerragruntPsiUtil.getBlockType(block);
            if ("dependency".equals(type)) {
                TerragruntBody body = block.getBody();
                if (body == null) continue;
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    if ("config_path".equals(attr.getIdentifier().getText())) {
                        String path = extractStringValue(attr);
                        if (path != null) {
                            VirtualFile resolved = dir.findFileByRelativePath(path);
                            if (resolved != null && resolved.isDirectory()) {
                                VirtualFile target = resolved.findChild("terragrunt.hcl");
                                if (target != null) deps.add(target.getPath());
                            }
                        }
                    }
                }
            } else if ("dependencies".equals(type)) {
                TerragruntBody body = block.getBody();
                if (body == null) continue;
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    if ("paths".equals(attr.getIdentifier().getText())) {
                        // paths = ["../vpc", "../rds"]
                        String text = attr.getText();
                        int start = text.indexOf('[');
                        int end = text.lastIndexOf(']');
                        if (start >= 0 && end > start) {
                            String listContent = text.substring(start + 1, end);
                            for (String item : listContent.split(",")) {
                                String path = item.trim().replace("\"", "").trim();
                                if (path.isEmpty()) continue;
                                VirtualFile resolved = dir.findFileByRelativePath(path);
                                if (resolved != null && resolved.isDirectory()) {
                                    VirtualFile target = resolved.findChild("terragrunt.hcl");
                                    if (target != null) deps.add(target.getPath());
                                }
                            }
                        }
                    }
                }
            }
        }
        return deps;
    }

    private static String extractStringValue(TerragruntAttribute attr) {
        String text = attr.getText();
        int eq = text.indexOf('=');
        if (eq < 0) return null;
        String value = text.substring(eq + 1).trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return null;
    }

    private static String getRelativePath(VirtualFile base, VirtualFile file) {
        String basePath = base.getPath();
        VirtualFile parent = file.getParent();
        if (parent == null) return file.getName();
        String filePath = parent.getPath();
        if (filePath.startsWith(basePath)) {
            String rel = filePath.substring(basePath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            return rel.isEmpty() ? "." : rel;
        }
        return filePath;
    }
}
