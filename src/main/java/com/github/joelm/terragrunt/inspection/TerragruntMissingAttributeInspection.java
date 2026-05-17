package com.github.joelm.terragrunt.inspection;

import com.github.joelm.terragrunt.lang.psi.TerragruntAttribute;
import com.github.joelm.terragrunt.lang.psi.TerragruntBlock;
import com.github.joelm.terragrunt.lang.psi.TerragruntBody;
import com.github.joelm.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

public class TerragruntMissingAttributeInspection extends LocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntBlock block)) return;

                String blockType = block.getIdentifier().getText();
                TerragruntSchema.BlockDef def = TerragruntSchema.getBlock(blockType);
                if (def == null) return;

                TerragruntBody body = block.getBody();
                // Only check direct child attributes, not nested ones
                Set<String> existingAttrs = body == null ? Set.of() :
                        PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class).stream()
                                .map(a -> a.getIdentifier().getText())
                                .collect(Collectors.toSet());

                for (var attr : def.attributes()) {
                    if (attr.required() && !existingAttrs.contains(attr.name())) {
                        holder.registerProblem(block.getIdentifier(),
                                "Missing required attribute '" + attr.name() + "' in '" + blockType + "' block",
                                ProblemHighlightType.WARNING);
                    }
                }
            }
        };
    }
}
