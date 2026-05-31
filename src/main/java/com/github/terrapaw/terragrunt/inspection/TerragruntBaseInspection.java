package com.github.terrapaw.terragrunt.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for Terragrunt inspections that supports comment-based suppression.
 * Users can suppress warnings with: # noinspection ShortName
 */
public abstract class TerragruntBaseInspection extends LocalInspectionTool {

    /**
     * Checks if the given element is suppressed by a preceding comment.
     * Looks for "# noinspection ShortName" on the line above.
     */
    protected boolean isSuppressed(@NotNull PsiElement element, @NotNull String shortName) {
        PsiElement prev = element.getPrevSibling();
        while (prev instanceof PsiWhiteSpace) {
            prev = prev.getPrevSibling();
        }
        if (prev != null && isComment(prev)) {
            String text = prev.getText();
            if (text.contains("noinspection") && text.contains(shortName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SuppressQuickFix @NotNull [] getBatchSuppressActions(PsiElement element) {
        return new SuppressQuickFix[]{new TerragruntSuppressQuickFix(getShortName())};
    }

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element) {
        // Comments are skipped by the parser (in COMMENTS token set), so they don't appear
        // as PSI siblings. Check the file text directly for a comment on the preceding line.
        PsiElement block = element;
        while (block != null && !(block instanceof com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock)) {
            block = block.getParent();
        }
        if (block == null) block = element;

        String fileText = block.getContainingFile().getText();
        int offset = block.getTextOffset();
        // Find the start of the line containing this element
        int lineStart = fileText.lastIndexOf('\n', offset - 1);
        if (lineStart < 0) return false;
        // Get the previous line
        int prevLineStart = fileText.lastIndexOf('\n', lineStart - 1);
        String prevLine = fileText.substring(prevLineStart + 1, lineStart).trim();
        return prevLine.contains("noinspection") && prevLine.contains(getShortName());
    }

    private static boolean isComment(PsiElement element) {
        if (element instanceof PsiComment) return true;
        var node = element.getNode();
        if (node == null) return false;
        var type = node.getElementType();
        return type == TerragruntTypes.LINE_COMMENT || type == TerragruntTypes.BLOCK_COMMENT;
    }
}
