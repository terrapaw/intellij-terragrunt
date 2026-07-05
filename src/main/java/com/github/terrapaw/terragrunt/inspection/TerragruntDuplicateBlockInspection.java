package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntLabel;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TerragruntDuplicateBlockInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);

                Map<String, TerragruntBlock> seen = new HashMap<>();

                for (TerragruntBlock block : blocks) {
                    if (block.getIdentifier() == null) continue;
                    String type = TerragruntPsiUtil.getBlockType(block);

                    // Skip blocks nested inside autoinclude — they're scoped per unit/stack
                    if (isInsideAutoinclude(block)) continue;

                    // Unlabeled blocks: locals cannot appear multiple times (Terragrunt errors)
                    // Other unlabeled blocks (terraform, remote_state) are singletons too
                    // Skip autoinclude — it's scoped per parent unit/stack block, not globally
                    List<TerragruntLabel> labels = block.getLabelList();
                    if (labels.isEmpty()) {
                        if ("autoinclude".equals(type)) continue;
                        String key = type + ":_unlabeled_";
                        if (seen.containsKey(key)) {
                            holder.registerProblem(
                                    block.getIdentifier(),
                                    "Duplicate '" + type + "' block (Terragrunt does not support multiple " + type + " blocks)",
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    new RemoveDuplicateBlockQuickFix()
                            );
                        } else {
                            seen.put(key, block);
                        }
                        continue;
                    }

                    String label = TerragruntPsiUtil.getLabelText(labels.getFirst());
                    String key = type + ":" + label;

                    if (seen.containsKey(key)) {
                        holder.registerProblem(
                                labels.getFirst(),
                                "Duplicate " + type + " block '" + label + "'",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                new RenameDuplicateBlockQuickFix(),
                                new RemoveDuplicateBlockQuickFix()
                        );
                    } else {
                        seen.put(key, block);
                    }
                }
            }
        };
    }

    private boolean isInsideAutoinclude(TerragruntBlock block) {
        TerragruntBlock parent = PsiTreeUtil.getParentOfType(block, TerragruntBlock.class);
        while (parent != null) {
            if ("autoinclude".equals(TerragruntPsiUtil.getBlockType(parent))) return true;
            parent = PsiTreeUtil.getParentOfType(parent, TerragruntBlock.class);
        }
        return false;
    }
}
