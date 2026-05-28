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

                    for (TerragruntLabel label : block.getLabelList()) {
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
        };
    }
}
