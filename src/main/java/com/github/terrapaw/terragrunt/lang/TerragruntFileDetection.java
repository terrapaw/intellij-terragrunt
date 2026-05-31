package com.github.terrapaw.terragrunt.lang;

import com.github.terrapaw.terragrunt.settings.TerragruntSettings;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.Set;

/**
 * Shared file detection logic used by both TerragruntFileTypeOverrider and TerragruntFileTypeDetector.
 */
public final class TerragruntFileDetection {
    public static final Set<String> KNOWN_FILENAMES = Set.of(
            "terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl", "terragrunt.values.hcl"
    );
    private static final Set<String> DEFAULT_MARKERS = Set.of(
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
        List<String> markers = getMarkers();
        VirtualFile dir = file.getParent();
        for (int i = 0; i < 4 && dir != null && dir.isValid(); i++) {
            for (String marker : markers) {
                if (dir.findChild(marker) != null) {
                    return true;
                }
            }
            dir = dir.getParent();
        }
        return false;
    }

    private static List<String> getMarkers() {
        try {
            return TerragruntSettings.getInstance().getMarkerFilenames();
        } catch (Exception e) {
            // Settings not available (e.g. during testing without full app context)
            return List.copyOf(DEFAULT_MARKERS);
        }
    }
}
