package com.github.terrapaw.terragrunt.inspection;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Quick-fix that inserts a "# noinspection X" comment above the element.
 */
public class TerragruntSuppressQuickFix implements SuppressQuickFix {
    private final String shortName;

    public TerragruntSuppressQuickFix(String shortName) {
        this.shortName = shortName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Suppress for statement";
    }

    @NotNull
    @Override
    public String getName() {
        return "Suppress '" + shortName + "'";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
        return true;
    }

    @Override
    public boolean isSuppressAll() {
        return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) return;
        // Find the line-level element (block or attribute)
        PsiElement target = element;
        while (target != null && target.getParent() != null && !(target.getParent() instanceof PsiFile)) {
            if (target.getParent().getParent() instanceof PsiFile) break;
            target = target.getParent();
        }
        if (target == null) return;

        // Create the suppression comment
        String indent = getIndent(target);
        PsiFile dummyFile = PsiFileFactory.getInstance(project)
                .createFileFromText("dummy.hcl", TerragruntFileType.INSTANCE,
                        indent + "# noinspection " + shortName + "\n");
        PsiElement comment = dummyFile.getFirstChild();
        if (comment != null) {
            target.getParent().addBefore(comment, target);
        }
    }

    private String getIndent(PsiElement element) {
        PsiElement prev = element.getPrevSibling();
        if (prev != null && prev.getText().contains("\n")) {
            String text = prev.getText();
            int lastNewline = text.lastIndexOf('\n');
            if (lastNewline >= 0) {
                return text.substring(lastNewline + 1);
            }
        }
        return "";
    }
}
