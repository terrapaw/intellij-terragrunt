package com.github.terrapaw.terragrunt.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
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
        if (prev instanceof PsiComment) {
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
        // Walk up to find the block or attribute being inspected
        PsiElement current = element;
        while (current != null) {
            PsiElement prev = current.getPrevSibling();
            while (prev instanceof PsiWhiteSpace) {
                prev = prev.getPrevSibling();
            }
            if (prev instanceof PsiComment) {
                String text = prev.getText();
                if (text.contains("noinspection") && text.contains(getShortName())) {
                    return true;
                }
            }
            current = current.getParent();
        }
        return false;
    }
}
