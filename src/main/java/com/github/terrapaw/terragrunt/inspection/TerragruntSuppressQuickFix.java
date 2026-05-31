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

        // Walk up to find the containing block
        PsiElement target = element;
        while (target != null) {
            if (target instanceof com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock) break;
            target = target.getParent();
        }
        if (target == null) target = element;

        // Create a file with just the comment, extract the comment + whitespace elements
        PsiFile dummyFile = PsiFileFactory.getInstance(project)
                .createFileFromText("dummy.hcl", TerragruntFileType.INSTANCE,
                        "# noinspection " + shortName + "\n");
        // Insert all elements from the dummy file before the target
        PsiElement child = dummyFile.getFirstChild();
        PsiElement parent = target.getParent();
        while (child != null) {
            PsiElement next = child.getNextSibling();
            parent.addBefore(child, target);
            child = next;
        }
    }
}
