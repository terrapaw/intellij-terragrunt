package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntLabel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TerragruntDuplicateBlockInspection extends LocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);

                Map<String, TerragruntBlock> seen = new HashMap<>();

                for (TerragruntBlock block : blocks) {
                    String type = block.getIdentifier().getText();

                    // Unlabeled blocks (locals, terraform) can appear multiple times — Terragrunt merges them
                    List<TerragruntLabel> labels = block.getLabelList();
                    if (labels.isEmpty()) continue;

                    String label = labels.getFirst().getText().replace("\"", "");
                    String key = type + ":" + label;

                    if (seen.containsKey(key)) {
                        holder.registerProblem(
                                labels.getFirst(),
                                "Duplicate " + type + " block '" + label + "'",
                                ProblemHighlightType.WARNING
                        );
                    } else {
                        seen.put(key, block);
                    }
                }
            }
        };
    }
}
