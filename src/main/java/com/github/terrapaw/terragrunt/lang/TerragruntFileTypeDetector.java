package com.github.terrapaw.terragrunt.lang;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

public class TerragruntFileTypeDetector implements FileTypeRegistry.FileTypeDetector {
    private static final Set<String> KNOWN_FILENAMES = Set.of(
            "terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl"
    );
    private static final Set<String> TERRAGRUNT_SIBLING_NAMES = Set.of(
            "terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl", "terragrunt.values.hcl"
    );
    private static final Pattern TERRAGRUNT_PATTERN = Pattern.compile(
            "^\\s*(include|dependency|remote_state|generate|feature|exclude|errors)\\s",
            Pattern.MULTILINE
    );

    @Nullable
    @Override
    public FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        String name = file.getName();

        // Exact filename match
        if (KNOWN_FILENAMES.contains(name)) {
            return TerragruntFileType.INSTANCE;
        }

        if (!name.endsWith(".hcl")) return null;

        // Content heuristic — contains Terragrunt-specific blocks
        if (firstCharsIfText != null && TERRAGRUNT_PATTERN.matcher(firstCharsIfText).find()) {
            return TerragruntFileType.INSTANCE;
        }

        // Directory heuristic — sibling or parent contains Terragrunt files
        if (isInTerragruntProject(file)) {
            return TerragruntFileType.INSTANCE;
        }

        return null;
    }

    private boolean isInTerragruntProject(VirtualFile file) {
        // Check siblings in same directory
        VirtualFile dir = file.getParent();
        if (dir != null) {
            for (String siblingName : TERRAGRUNT_SIBLING_NAMES) {
                if (dir.findChild(siblingName) != null) {
                    return true;
                }
            }
        }

        // Check parent directories (up to 3 levels)
        VirtualFile parent = dir;
        for (int i = 0; i < 3 && parent != null; i++) {
            parent = parent.getParent();
            if (parent == null) break;
            for (String name : TERRAGRUNT_SIBLING_NAMES) {
                if (parent.findChild(name) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public int getDesiredContentPrefixLength() {
        return 4096;
    }
}
