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

                    if (blockDef.hasLabel()) {
                        if (labels.isEmpty()) {
                            holder.registerProblem(
                                    block.getIdentifier(),
                                    "'" + type + "' block requires a label",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            );
                        } else if (labels.size() == 1) {
                            String labelText = labels.getFirst().getText().replace("\"", "").trim();
                            if (labelText.isEmpty()) {
                                holder.registerProblem(
                                        labels.getFirst(),
                                        "'" + type + "' block label must not be empty",
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                );
                            }
                        } else {
                            // Multiple labels — highlight the second one onwards
                            holder.registerProblem(
                                    labels.get(1),
                                    "'" + type + "' block expects 1 label, found " + labels.size(),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            );
                        }
                    } else {
                        // Block doesn't take labels — flag any that exist
                        for (TerragruntLabel label : labels) {
                            holder.registerProblem(
                                    label,
                                    "'" + type + "' block does not take a label",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                            );
                        }
                    }
                }
            }
        };
    }
}
