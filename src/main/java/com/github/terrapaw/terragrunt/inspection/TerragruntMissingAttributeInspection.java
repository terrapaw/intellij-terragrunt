package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.github.terrapaw.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TerragruntMissingAttributeInspection extends TerragruntBaseInspection {
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
                Set<String> existingAttrs = body == null ? Set.of() :
                        PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class).stream()
                                .map(a -> a.getIdentifier().getText())
                                .collect(Collectors.toSet());

                List<TerragruntSchema.AttrDef> missing = new ArrayList<>();
                for (var attr : def.attributes()) {
                    if (attr.required() && !existingAttrs.contains(attr.name())) {
                        missing.add(attr);
                    }
                }

                if (!missing.isEmpty()) {
                    String message = missing.size() == 1
                            ? "Missing required attribute '" + missing.get(0).name() + "' in '" + blockType + "' block"
                            : "Missing " + missing.size() + " required attributes in '" + blockType + "' block";

                    holder.registerProblem(block.getIdentifier(), message,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            new InsertMissingAttributesFix(missing));
                }
            }
        };
    }

    private static class InsertMissingAttributesFix implements LocalQuickFix {
        private final List<TerragruntSchema.AttrDef> missingAttrs;

        InsertMissingAttributesFix(List<TerragruntSchema.AttrDef> missingAttrs) {
            this.missingAttrs = missingAttrs;
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return "Insert missing attributes";
        }

        @NotNull
        @Override
        public String getName() {
            if (missingAttrs.size() == 1) {
                return "Insert '" + missingAttrs.get(0).name() + "'";
            }
            return "Insert all missing required attributes";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            TerragruntBlock block = PsiTreeUtil.getParentOfType(element, TerragruntBlock.class, false);
            if (block == null) return;
            TerragruntBody body = block.getBody();
            if (body == null) return;

            StringBuilder sb = new StringBuilder();
            for (var attr : missingAttrs) {
                sb.append("  ").append(attr.name()).append(" = ").append(defaultValue(attr.type())).append("\n");
            }

            // Insert before the closing brace of the block
            PsiElement lastChild = block.getLastChild(); // RBRACE
            if (lastChild == null) return;
            int insertOffset = lastChild.getTextOffset();
            var document = block.getContainingFile().getViewProvider().getDocument();
            if (document != null) {
                document.insertString(insertOffset, sb.toString());
            }
        }

        private String defaultValue(String type) {
            return switch (type) {
                case "string" -> "\"\"";
                case "bool" -> "false";
                case "number" -> "0";
                case "list" -> "[]";
                case "map" -> "{}";
                default -> "\"\"";
            };
        }
    }
}
