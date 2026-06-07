package com.github.terrapaw.terragrunt.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class RenameDuplicateBlockQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
        return "Change block label";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement label = descriptor.getPsiElement();
        if (label == null || !label.isValid()) return;

        com.intellij.openapi.editor.Editor editor =
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                        .getSelectedTextEditor();
        if (editor == null) return;

        // Select the label text (inside quotes) so user can type a new name
        int start = label.getTextRange().getStartOffset();
        int end = label.getTextRange().getEndOffset();
        String text = label.getText();
        if (text.startsWith("\"") && text.endsWith("\"")) {
            start++;
            end--;
        }
        editor.getCaretModel().moveToOffset(start);
        editor.getSelectionModel().setSelection(start, end);
    }
}
