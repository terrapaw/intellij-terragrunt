package com.github.terrapaw.terragrunt.lang;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Overrides file type for .hcl files that are in Terragrunt projects.
 * This runs before the cached file type lookup, so it can claim files
 * that would otherwise be handled by the bundled HCL plugin.
 */
public class TerragruntFileTypeOverrider implements FileTypeOverrider {
    private static final Set<String> KNOWN_FILENAMES = Set.of(
            "terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl", "terragrunt.values.hcl"
    );
    private static final Set<String> TERRAGRUNT_MARKERS = Set.of(
            "terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl"
    );

    @Override
    public @Nullable FileType getOverriddenFileType(@NotNull VirtualFile file) {
        String name = file.getName();
        if (!name.endsWith(".hcl")) return null;

        // Always claim known Terragrunt filenames
        if (KNOWN_FILENAMES.contains(name)) {
            return TerragruntFileType.INSTANCE;
        }

        // For other .hcl files, check if they're in a Terragrunt project
        if (isInTerragruntProject(file)) {
            return TerragruntFileType.INSTANCE;
        }

        return null;
    }

    private boolean isInTerragruntProject(VirtualFile file) {
        VirtualFile dir = file.getParent();
        // Check current directory and up to 3 parent levels
        for (int i = 0; i < 4 && dir != null; i++) {
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
