package com.github.terrapaw.terragrunt.reference;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.github.terrapaw.terragrunt.settings.TerragruntSettings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Resolves file paths in include/dependency blocks to actual PsiFiles.
 */
public class TerragruntFileResolver {

    /**
     * Resolves the file referenced by an include block's path attribute.
     */
    @Nullable
    public static PsiFile resolveInclude(TerragruntBlock includeBlock) {
        if (!"include".equals(TerragruntPsiUtil.getBlockType(includeBlock))) return null;
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
            // No args — default to finding terragrunt.hcl in parent folders
            return findInParentFolders(sourceFile, "terragrunt.hcl");
        }

        // Check for plain string literal path
        TerragruntStringLit stringLit = PsiTreeUtil.findChildOfType(attr, TerragruntStringLit.class);
        if (stringLit != null) {
            String path = extractStringContent(stringLit.getText());
            if (path != null) {
                if (!path.contains("${")) {
                    return resolveRelativePath(sourceFile, path);
                }
                // Try to evaluate interpolations containing known functions
                String evaluated = evaluateInterpolatedPath(path, sourceFile);
                if (evaluated != null) {
                    return resolveRelativePath(sourceFile, evaluated);
                }
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

        // Handle absolute paths (from function evaluation)
        if (path.startsWith("/")) {
            // Navigate from the filesystem root that contains our source file
            VirtualFile root = vFile;
            while (root.getParent() != null) root = root.getParent();
            VirtualFile target = root;
            for (String part : path.substring(1).split("/")) {
                if (target == null) return null;
                target = target.findChild(part);
            }
            if (target != null && !target.isDirectory()) {
                return PsiManager.getInstance(sourceFile.getProject()).findFile(target);
            }
            return null;
        }

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
        return TerragruntPsiUtil.findBlock(file, "include", labelName);
    }

    /**
     * Finds an attribute in a locals block of the given file.
     */
    @Nullable
    public static TerragruntAttribute findLocalAttribute(PsiFile file, String name) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(TerragruntPsiUtil.getBlockType(block))) continue;
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

    /**
     * Finds a key in the inputs map of the given file. Returns the key element for precise navigation.
     */
    @Nullable
    public static PsiElement findInputKey(PsiFile file, String name) {
        for (TerragruntAttribute attr : PsiTreeUtil.findChildrenOfType(file, TerragruntAttribute.class)) {
            if (!"inputs".equals(attr.getIdentifier().getText())) continue;
            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
            if (obj == null) continue;
            for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                PsiElement key = elem.getFirstChild();
                if (key != null && name.equals(key.getText())) {
                    return key;
                }
            }
        }
        return null;
    }

    /**
     * Evaluates a path string containing ${...} interpolations with known Terragrunt functions.
     * Returns the resolved path string, or null if any interpolation can't be evaluated.
     */
    @Nullable
    private static String evaluateInterpolatedPath(String path, PsiFile sourceFile) {
        VirtualFile vFile = sourceFile.getVirtualFile();
        if (vFile == null) return null;
        VirtualFile sourceDir = vFile.getParent();
        if (sourceDir == null) return null;

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < path.length()) {
            if (i < path.length() - 1 && path.charAt(i) == '$' && path.charAt(i + 1) == '{') {
                int end = path.indexOf('}', i + 2);
                if (end == -1) return null;
                String expr = path.substring(i + 2, end).trim();
                String evaluated = evaluateFunction(expr, sourceDir, sourceFile);
                if (evaluated == null) return null;
                result.append(evaluated);
                i = end + 1;
            } else {
                result.append(path.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * Evaluates a single Terragrunt function expression to a path string.
     */
    @Nullable
    private static String evaluateFunction(String expr, VirtualFile sourceDir, PsiFile sourceFile) {
        // get_terragrunt_dir() — directory of the current file
        if (expr.equals("get_terragrunt_dir()")) {
            return sourceDir.getPath();
        }
        // get_parent_terragrunt_dir() — directory of the included parent config
        if (expr.equals("get_parent_terragrunt_dir()") || expr.startsWith("get_parent_terragrunt_dir(")) {
            return resolveParentTerragruntDir(expr, sourceDir, sourceFile);
        }
        // get_root_terragrunt_dir() — walk up to find the topmost dir with root.hcl or terragrunt.hcl
        if (expr.equals("get_root_terragrunt_dir()")) {
            VirtualFile dir = sourceDir.getParent();
            VirtualFile topmost = null;
            while (dir != null) {
                if (dir.findChild("root.hcl") != null) return dir.getPath();
                if (dir.findChild("terragrunt.hcl") != null) topmost = dir;
                dir = dir.getParent();
            }
            return topmost != null ? topmost.getPath() : null;
        }
        // get_original_terragrunt_dir() — same as get_terragrunt_dir() for IDE purposes
        if (expr.equals("get_original_terragrunt_dir()")) {
            return sourceDir.getPath();
        }
        // get_repo_root() — walk up to find .git directory
        if (expr.equals("get_repo_root()")) {
            VirtualFile dir = sourceDir;
            while (dir != null) {
                if (dir.findChild(".git") != null) return dir.getPath();
                dir = dir.getParent();
            }
            return null;
        }
        // find_in_parent_folders("X") — walk up to find file, return full path
        if (expr.startsWith("find_in_parent_folders(")) {
            String arg = extractFunctionArg(expr);
            if (arg == null) arg = "terragrunt.hcl";
            VirtualFile dir = sourceDir.getParent();
            while (dir != null) {
                VirtualFile target = dir.findChild(arg);
                if (target != null && !target.isDirectory()) return target.getPath();
                dir = dir.getParent();
            }
            return null;
        }
        // dirname(X) — evaluate inner expression, return parent directory
        if (expr.startsWith("dirname(") && expr.endsWith(")")) {
            String inner = expr.substring("dirname(".length(), expr.length() - 1);
            String innerResult = evaluateFunction(inner, sourceDir, sourceFile);
            if (innerResult == null) return null;
            int lastSlash = innerResult.lastIndexOf('/');
            return lastSlash > 0 ? innerResult.substring(0, lastSlash) : innerResult;
        }
        // basename(X) — evaluate inner expression, return last path component
        if (expr.startsWith("basename(") && expr.endsWith(")")) {
            String inner = expr.substring("basename(".length(), expr.length() - 1);
            String innerResult = evaluateFunction(inner, sourceDir, sourceFile);
            if (innerResult == null) return null;
            int lastSlash = innerResult.lastIndexOf('/');
            return lastSlash >= 0 ? innerResult.substring(lastSlash + 1) : innerResult;
        }
        // get_path_to_repo_root() — relative path from current dir to git root
        if (expr.equals("get_path_to_repo_root()")) {
            VirtualFile dir = sourceDir;
            StringBuilder rel = new StringBuilder();
            while (dir != null) {
                if (dir.findChild(".git") != null) return rel.length() == 0 ? "." : rel.toString();
                rel.append(rel.length() == 0 ? ".." : "/..");
                dir = dir.getParent();
            }
            return null;
        }
        // get_path_from_repo_root() — path from git root to current dir
        if (expr.equals("get_path_from_repo_root()")) {
            VirtualFile dir = sourceDir;
            java.util.List<String> parts = new java.util.ArrayList<>();
            while (dir != null) {
                if (dir.findChild(".git") != null) {
                    java.util.Collections.reverse(parts);
                    return String.join("/", parts);
                }
                parts.add(dir.getName());
                dir = dir.getParent();
            }
            return null;
        }
        return null;
    }

    /**
     * Resolves get_parent_terragrunt_dir() or get_parent_terragrunt_dir("name").
     * Rules:
     * 1. File is NOT terragrunt.hcl → it's a parent config → return its own directory
     * 2. File IS terragrunt.hcl with include block(s) → resolve the included file's directory
     * 3. File IS terragrunt.hcl without include → no parent → null
     */
    private static final ThreadLocal<Boolean> resolvingParentDir = ThreadLocal.withInitial(() -> false);

    @Nullable
    private static String resolveParentTerragruntDir(String expr, VirtualFile sourceDir, PsiFile sourceFile) {
        // Guard against recursion (get_parent_terragrunt_dir() used inside include path)
        if (resolvingParentDir.get()) return null;

        VirtualFile vFile = sourceFile.getVirtualFile();
        if (vFile == null) return null;

        // Rule 1: not an entry point → it's a parent config → return own directory
        if (!TerragruntSettings.getInstance().isEntryPoint(vFile.getName())) {
            return sourceDir.getPath();
        }

        // Rule 2: terragrunt.hcl → find include block and resolve its path
        resolvingParentDir.set(true);
        try {
            String includeName = extractFunctionArg(expr); // null if no arg
            Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(sourceFile, TerragruntBlock.class);
            for (TerragruntBlock block : blocks) {
                if (!"include".equals(TerragruntPsiUtil.getBlockType(block))) continue;
                if (includeName != null) {
                    boolean matches = false;
                    for (TerragruntLabel label : block.getLabelList()) {
                        if (includeName.equals(TerragruntPsiUtil.getLabelText(label))) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) continue;
                }
                PsiFile resolved = resolveInclude(block);
                if (resolved != null && resolved.getVirtualFile() != null) {
                    VirtualFile parent = resolved.getVirtualFile().getParent();
                    if (parent != null) return parent.getPath();
                }
            }
        } finally {
            resolvingParentDir.set(false);
        }

        // Rule 3: no include resolved — can't determine parent
        return null;
    }

    @Nullable
    private static String extractFunctionArg(String expr) {
        int start = expr.indexOf('"');
        if (start < 0) return null;
        int end = expr.indexOf('"', start + 1);
        if (end < 0) return null;
        return expr.substring(start + 1, end);
    }

    @Nullable
    private static String extractStringContent(String quotedText) {
        if (quotedText == null || quotedText.length() < 2) return null;
        // Remove surrounding quotes (may be multiple STRING_LITERAL tokens concatenated)
        String content = quotedText.replaceAll("^\"+|\"+$", "");
        return content.isEmpty() ? null : content;
    }

    /**
     * Resolves read_terragrunt_config(find_in_parent_folders("X")) or read_terragrunt_config("path").
     * If resolution fails from the current file's directory, tries resolving from includer directories.
     */
    @Nullable
    public static PsiFile resolveReadTerragruntConfig(TerragruntFunctionCall funcCall, PsiFile sourceFile) {
        // Try resolving from includer directories first (stack context — runtime-accurate)
        PsiFile result = resolveReadTerragruntConfigFromIncluders(funcCall, sourceFile);
        if (result != null) return result;

        // For find_in_parent_folders, only fall back to direct resolution if this file
        // is an entry point (terragrunt.hcl). Non-entry-point files depend on context.
        TerragruntArgList argList = funcCall.getArgList();
        if (argList != null) {
            TerragruntFunctionCall nestedFunc = PsiTreeUtil.findChildOfType(argList, TerragruntFunctionCall.class);
            if (nestedFunc != null && "find_in_parent_folders".equals(nestedFunc.getIdentifier().getText())) {
                String fileName = sourceFile.getName();
                if (!TerragruntSettings.getInstance().isEntryPoint(fileName)) {
                    return null; // Context-dependent — don't guess
                }
            }
        }
        return resolveReadTerragruntConfigDirect(funcCall, sourceFile);
    }

    @Nullable
    private static PsiFile resolveReadTerragruntConfigDirect(TerragruntFunctionCall funcCall, PsiFile sourceFile) {
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
            if (path != null) {
                if (!path.contains("${")) {
                    return resolveRelativePath(sourceFile, path);
                }
                String evaluated = evaluateInterpolatedPath(path, sourceFile);
                if (evaluated != null) {
                    return resolveRelativePath(sourceFile, evaluated);
                }
            }
        }

        return null;
    }

    /**
     * Tries resolving a read_terragrunt_config call from the directories of files that include sourceFile.
     * This handles the stack context case where find_in_parent_folders needs to search from the includer.
     */
    @Nullable
    private static PsiFile resolveReadTerragruntConfigFromIncluders(TerragruntFunctionCall funcCall, PsiFile sourceFile) {
        VirtualFile sourceVFile = sourceFile.getVirtualFile();
        if (sourceVFile == null) return null;
        String sourceFileName = sourceVFile.getName();

        TerragruntArgList argList = funcCall.getArgList();
        if (argList == null) return null;

        // Only handle find_in_parent_folders case (the common pattern)
        TerragruntFunctionCall nestedFunc = PsiTreeUtil.findChildOfType(argList, TerragruntFunctionCall.class);
        if (nestedFunc == null || !"find_in_parent_folders".equals(nestedFunc.getIdentifier().getText())) return null;

        String targetFileName = "terragrunt.hcl";
        TerragruntArgList nestedArgs = nestedFunc.getArgList();
        if (nestedArgs != null) {
            TerragruntStringLit stringLit = PsiTreeUtil.findChildOfType(nestedArgs, TerragruntStringLit.class);
            if (stringLit != null) {
                String extracted = extractStringContent(stringLit.getText());
                if (extracted != null) targetFileName = extracted;
            }
        }

        // Find files that include sourceFile via find_in_parent_folders("sourceFileName")
        List<VirtualFile> includerDirs = findIncluderDirectories(sourceFile, sourceFileName);
        if (includerDirs.isEmpty()) return null;

        // Try resolving find_in_parent_folders from each includer's directory
        for (VirtualFile includerDir : includerDirs) {
            VirtualFile dir = includerDir.getParent();
            while (dir != null) {
                VirtualFile target = dir.findChild(targetFileName);
                if (target != null && !target.isDirectory()) {
                    return PsiManager.getInstance(sourceFile.getProject()).findFile(target);
                }
                dir = dir.getParent();
            }
        }
        return null;
    }

    /**
     * Finds directories of files that include the given file via find_in_parent_folders.
     */
    private static List<VirtualFile> findIncluderDirectories(PsiFile sourceFile, String sourceFileName) {
        List<VirtualFile> dirs = new java.util.ArrayList<>();
        com.intellij.openapi.project.Project project = sourceFile.getProject();
        com.intellij.openapi.roots.ProjectRootManager rootManager =
                com.intellij.openapi.roots.ProjectRootManager.getInstance(project);
        for (VirtualFile contentRoot : rootManager.getContentRoots()) {
            findIncludersRecursive(contentRoot, project, sourceFileName, sourceFile, dirs);
        }
        return dirs;
    }

    private static void findIncludersRecursive(VirtualFile dir, com.intellij.openapi.project.Project project,
                                                String sourceFileName, PsiFile sourceFile, List<VirtualFile> dirs) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if ((child.getName().startsWith(".") && !child.getName().equals(".terragrunt-stack")) || child.getName().equals("node_modules")) continue;
                findIncludersRecursive(child, project, sourceFileName, sourceFile, dirs);
            } else if (child.getName().endsWith(".hcl")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile == null || psiFile.equals(sourceFile)) continue;
                // Check if this file has include { path = find_in_parent_folders("sourceFileName") }
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(psiFile, TerragruntBlock.class)) {
                    if (!"include".equals(TerragruntPsiUtil.getBlockType(block))) continue;
                    TerragruntBody body = block.getBody();
                    if (body == null) continue;
                    for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                        if (!"path".equals(attr.getIdentifier().getText())) continue;
                        TerragruntFunctionCall fc = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
                        if (fc != null && "find_in_parent_folders".equals(fc.getIdentifier().getText())) {
                            TerragruntArgList args = fc.getArgList();
                            if (args != null) {
                                TerragruntStringLit sl = PsiTreeUtil.findChildOfType(args, TerragruntStringLit.class);
                                if (sl != null && sourceFileName.equals(extractStringContent(sl.getText()))) {
                                    dirs.add(child.getParent());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
