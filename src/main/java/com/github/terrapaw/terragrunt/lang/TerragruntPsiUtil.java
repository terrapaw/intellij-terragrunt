package com.github.terrapaw.terragrunt.lang;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntLabel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared PSI utility methods for Terragrunt elements.
 */
public final class TerragruntPsiUtil {

    private TerragruntPsiUtil() {}

    /**
     * Extracts the text content of a label, stripping surrounding quotes.
     */
    @NotNull
    public static String getLabelText(@NotNull TerragruntLabel label) {
        return label.getText().replace("\"", "");
    }

    /**
     * Finds a block by type and label name in the given file.
     * Returns null if not found.
     */
    @Nullable
    public static TerragruntBlock findBlock(@NotNull PsiFile file, @NotNull String blockType, @NotNull String labelName) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!blockType.equals(block.getIdentifier().getText())) continue;
            for (TerragruntLabel label : block.getLabelList()) {
                if (labelName.equals(getLabelText(label))) return block;
            }
        }
        return null;
    }

    /**
     * Checks if a block with the given type and label exists in the file.
     */
    public static boolean blockExists(@NotNull PsiFile file, @NotNull String blockType, @NotNull String labelName) {
        return findBlock(file, blockType, labelName) != null;
    }
}
