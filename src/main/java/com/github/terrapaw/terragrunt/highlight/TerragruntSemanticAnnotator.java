package com.github.terrapaw.terragrunt.highlight;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class TerragruntSemanticAnnotator implements Annotator {

    private static final TextAttributesKey LABEL_KEY = TextAttributesKey.createTextAttributesKey(
            "TG_LABEL", DefaultLanguageHighlighterColors.CLASS_NAME);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof TerragruntBlock block) {
            // Color block type identifier as keyword
            PsiElement id = block.getIdentifier();
            if (id != null) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(id)
                        .textAttributes(TerragruntSyntaxHighlighter.KEYWORD)
                        .create();
            }
            // Color labels as plain text (override string color from lexer)
            for (TerragruntLabel label : block.getLabelList()) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(label)
                        .textAttributes(LABEL_KEY)
                        .create();
            }
        } else if (element instanceof TerragruntAttribute attr) {
            // Color attribute name as instance field/property
            PsiElement id = attr.getIdentifier();
            if (id != null && attr.getParent() instanceof TerragruntBody) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(id)
                        .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                        .create();
            }
        } else if (element instanceof TerragruntGetAttr getAttr) {
            // Color dot-accessed identifiers as property (e.g. local.region → "region" purple)
            PsiElement id = getAttr.getIdentifier();
            if (id != null) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(id)
                        .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                        .create();
            }
        } else if (element instanceof TerragruntObjectElem elem) {
            // Color object keys — both identifier and quoted
            PsiElement id = elem.getIdentifier();
            if (id != null) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(id)
                        .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                        .create();
            } else {
                // Quoted key: first expression is the key (a string literal)
                var exprs = elem.getExpressionList();
                if (exprs.size() >= 2) {
                    PsiElement keyExpr = exprs.get(0);
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(keyExpr)
                            .textAttributes(DefaultLanguageHighlighterColors.INSTANCE_FIELD)
                            .create();
                }
            }
        }
    }
}
