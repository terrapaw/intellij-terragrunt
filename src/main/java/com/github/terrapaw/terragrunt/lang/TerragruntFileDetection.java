package com.github.terrapaw.terragrunt.lang;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Set;

/**
 * Shared file detection logic used by both TerragruntFileTypeOverrider and TerragruntFileTypeDetector.
 */
public final class TerragruntFileDetection {
    public static final Set<String> KNOWN_FILENAMES = Set.of(
            "terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl", "terragrunt.values.hcl"
    );
    public static final Set<String> TERRAGRUNT_MARKERS = Set.of(
            "terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl"
    );
    private static final Set<String> EXCLUDED_FILENAMES = Set.of(
            ".terraform.lock.hcl"
    );

    private TerragruntFileDetection() {}

    public static boolean isExcluded(String fileName) {
        return EXCLUDED_FILENAMES.contains(fileName);
    }

    public static boolean isInTerragruntProject(VirtualFile file) {
        VirtualFile dir = file.getParent();
        for (int i = 0; i < 4 && dir != null && dir.isValid(); i++) {
            for (String marker : TERRAGRUNT_MARKERS) {
                if (dir.findChild(marker) != null) {
                    return true;
                }
            }
            dir = dir.getParent();
        }
        return false;
    }
}
