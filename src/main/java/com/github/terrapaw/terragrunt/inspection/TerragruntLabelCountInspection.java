package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntLabel;
import com.github.terrapaw.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TerragruntLabelCountInspection extends LocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);

                for (TerragruntBlock block : blocks) {
                    String type = block.getIdentifier().getText();
                    var blockDef = TerragruntSchema.getBlock(type);
                    if (blockDef == null) continue;

                    var labels = block.getLabelList();

                    // Check for missing label on blocks that require one
                    if (blockDef.hasLabel() && labels.isEmpty()) {
                        holder.registerProblem(
                                block.getIdentifier(),
                                "'" + type + "' block requires a label",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                        );
                        continue;
                    }

                    // Check for multiple label nodes (unquoted identifier labels)
                    if (blockDef.hasLabel() && labels.size() > 1) {
                        holder.registerProblem(
                                labels.get(1),
                                "'" + type + "' block expects 1 label, found " + labels.size(),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                        );
                        continue;
                    }

                    for (TerragruntLabel label : labels) {
                        String text = label.getText().trim();

                        if (!blockDef.hasLabel()) {
                            holder.registerProblem(
                                    label,
                                    "'" + type + "' block does not take a label",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            );
                        } else {
                            // Count quoted strings in the label text (our lexer merges them into one label node)
                            int quoteCount = 0;
                            for (char c : text.toCharArray()) {
                                if (c == '"') quoteCount++;
                            }
                            int labelCount = quoteCount / 2;
                            if (labelCount > 1) {
                                // Highlight only from the second quoted string onwards
                                int firstClose = text.indexOf('"', text.indexOf('"') + 1) + 1;
                                int secondOpen = text.indexOf('"', firstClose);
                                if (secondOpen >= 0) {
                                    var range = com.intellij.openapi.util.TextRange.create(secondOpen, text.length());
                                    holder.registerProblem(
                                            label,
                                            "'" + type + "' block expects 1 label, found " + labelCount,
                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                            range
                                    );
                                } else {
                                    holder.registerProblem(
                                            label,
                                            "'" + type + "' block expects 1 label, found " + labelCount,
                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                    );
                                }
                            }
                        }
                    }
                }
            }
        };
    }
}
