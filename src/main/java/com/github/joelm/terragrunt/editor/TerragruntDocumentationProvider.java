package com.github.joelm.terragrunt.editor;

import com.github.joelm.terragrunt.lang.psi.TerragruntTypes;
import com.github.joelm.terragrunt.schema.TerragruntSchema;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TerragruntDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement, int targetOffset) {
        // Return the element itself so generateDoc gets called with it
        return contextElement;
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        PsiElement target = originalElement != null ? originalElement : element;
        if (target == null) return null;

        String name = target.getText();
        if (name == null || name.isEmpty()) return null;

        return getFunctionDoc(name);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        PsiElement target = originalElement != null ? originalElement : element;
        if (target == null) return null;

        String name = target.getText();
        for (var func : TerragruntSchema.getFunctions()) {
            if (func.name().equals(name)) {
                return func.name() + func.signature();
            }
        }
        return null;
    }

    private String getFunctionDoc(String name) {
        for (var func : TerragruntSchema.getFunctions()) {
            if (func.name().equals(name)) {
                return "<html><body>" +
                        "<h3>" + func.name() + "</h3>" +
                        "<p><code>" + func.name() + func.signature() + "</code></p>" +
                        "<p>" + func.description() + "</p>" +
                        "</body></html>";
            }
        }
        return null;
    }
}
