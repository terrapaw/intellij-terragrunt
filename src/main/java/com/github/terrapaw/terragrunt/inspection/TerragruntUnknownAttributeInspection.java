package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.github.terrapaw.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TerragruntUnknownAttributeInspection extends TerragruntBaseInspection {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof TerragruntAttribute attr)) return;

                // Only check attributes directly inside a block body
                if (!(attr.getParent() instanceof TerragruntBody body)) return;
                if (!(body.getParent() instanceof TerragruntBlock block)) return;

                String blockType = block.getIdentifier().getText();
                TerragruntSchema.BlockDef def = TerragruntSchema.getBlock(blockType);
                if (def == null) return;

                // locals block allows any attribute
                if ("locals".equals(blockType)) return;

                String attrName = attr.getIdentifier().getText();

                // Check if attribute is known for this block
                Set<String> validAttrs = def.attributes().stream()
                        .map(TerragruntSchema.AttrDef::name)
                        .collect(Collectors.toSet());
                Set<String> validNestedBlocks = Set.copyOf(def.nestedBlocks());

                if (!validAttrs.contains(attrName) && !validNestedBlocks.contains(attrName)) {
                    String closest = ReplaceIdentifierQuickFix.findClosest(attrName,
                            Stream.concat(validAttrs.stream(), validNestedBlocks.stream()).toList());
                    if (closest != null) {
                        holder.registerProblem(attr.getIdentifier(),
                                "Unknown attribute '" + attrName + "' in '" + blockType + "' block",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                new ReplaceIdentifierQuickFix(closest));
                    } else {
                        holder.registerProblem(attr.getIdentifier(),
                                "Unknown attribute '" + attrName + "' in '" + blockType + "' block",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }
        };
    }
}
