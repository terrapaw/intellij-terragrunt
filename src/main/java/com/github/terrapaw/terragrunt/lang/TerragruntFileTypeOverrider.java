package com.github.terrapaw.terragrunt.lang;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Overrides file type for .hcl files that are in Terragrunt projects.
 * This runs before the cached file type lookup, so it can claim files
 * that would otherwise be handled by the bundled HCL plugin.
 */
public class TerragruntFileTypeOverrider implements FileTypeOverrider {

    @Override
    public @Nullable FileType getOverriddenFileType(@NotNull VirtualFile file) {
        String name = file.getName();
        if (!name.endsWith(".hcl")) return null;
        if (TerragruntFileDetection.isExcluded(name)) return null;

        if (TerragruntFileDetection.KNOWN_FILENAMES.contains(name)) {
            return TerragruntFileType.INSTANCE;
        }

        if (TerragruntFileDetection.isInTerragruntProject(file)) {
            return TerragruntFileType.INSTANCE;
        }

        return null;
    }
}
