package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class TerragruntTypedHandler extends TypedHandlerDelegate {
    @NotNull
    @Override
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (file.getFileType() != TerragruntFileType.INSTANCE) return Result.CONTINUE;

        if (c == '"') {
            int offset = editor.getCaretModel().getOffset();
            String text = editor.getDocument().getText();
            // Don't insert if the next char is already a quote (closing an existing string)
            if (offset < text.length() && text.charAt(offset) == '"') return Result.CONTINUE;
            // Insert closing quote and move caret between them
            editor.getDocument().insertString(offset, "\"");
        }

        // Auto-popup completion after / in path strings
        if (c == '/') {
            int offset = editor.getCaretModel().getOffset();
            // Check if we're inside a quoted string by scanning backwards for an unmatched "
            String text = editor.getDocument().getText();
            boolean inString = false;
            for (int i = offset - 1; i >= 0; i--) {
                if (text.charAt(i) == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (text.charAt(i) == '\n') break; // don't cross lines
            }
            if (inString) {
                com.intellij.codeInsight.AutoPopupController.getInstance(project)
                        .scheduleAutoPopup(editor, com.intellij.codeInsight.completion.CompletionType.BASIC, f -> f.getFileType() == TerragruntFileType.INSTANCE);
            }
        }

        return Result.CONTINUE;
    }
}
