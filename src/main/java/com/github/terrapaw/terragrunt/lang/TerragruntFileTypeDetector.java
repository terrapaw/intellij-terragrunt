package com.github.terrapaw.terragrunt.lang;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class TerragruntFileTypeDetector implements FileTypeRegistry.FileTypeDetector {
    private static final Pattern TERRAGRUNT_PATTERN = Pattern.compile(
            "^\\s*(include|dependency|remote_state|generate|feature|exclude|errors)\\s",
            Pattern.MULTILINE
    );

    @Nullable
    @Override
    public FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        String name = file.getName();

        if (TerragruntFileDetection.KNOWN_FILENAMES.contains(name)) {
            return TerragruntFileType.INSTANCE;
        }

        if (!name.endsWith(".hcl")) return null;
        if (TerragruntFileDetection.isExcluded(name)) return null;

        if (firstCharsIfText != null && TERRAGRUNT_PATTERN.matcher(firstCharsIfText).find()) {
            return TerragruntFileType.INSTANCE;
        }

        if (TerragruntFileDetection.isInTerragruntProject(file)) {
            return TerragruntFileType.INSTANCE;
        }

        return null;
    }

    @Override
    public int getDesiredContentPrefixLength() {
        return 4096;
    }
}
