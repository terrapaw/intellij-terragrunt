package com.github.joelm.terragrunt.completion;

import com.github.joelm.terragrunt.lang.psi.*;
import com.github.joelm.terragrunt.reference.TerragruntFileResolver;
import com.github.joelm.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class TerragruntCompletionContributor extends CompletionContributor {
    public TerragruntCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        PsiElement position = parameters.getPosition();

                        if (isInBody(position)) {
                            // Inside a block body or top-level — offer attributes/blocks
                            TerragruntBlock enclosingBlock = getDirectEnclosingBlock(position);
                            if (enclosingBlock == null) {
                                addTopLevelCompletions(result);
                            } else {
                                addBlockAttributeCompletions(enclosingBlock, result);
                            }
                        } else if (isInExpression(position)) {
                            if (isAfterDot(position)) {
                                addDotCompletions(position, result);
                            } else {
                                addExpressionCompletions(result);
                            }
                        }
                    }
                });
    }

    private boolean isInBody(PsiElement position) {
        // Walk up to find if we're directly in a body (not inside an expression value)
        // During completion, structure is: leaf -> dummy/error -> possibly attribute -> body
        if (PsiTreeUtil.getParentOfType(position, TerragruntGetAttr.class) != null) return false;
        if (PsiTreeUtil.getParentOfType(position, TerragruntObjectExpr.class) != null) return false;
        if (PsiTreeUtil.getParentOfType(position, TerragruntTupleExpr.class) != null) return false;
        // Check if there's an attribute ancestor with an expression (meaning we're in the value part)
        TerragruntAttribute attr = PsiTreeUtil.getParentOfType(position, TerragruntAttribute.class);
        if (attr != null && attr.getExpression() != null && attr.getExpression().getTextRange().contains(position.getTextOffset())) {
            return false;
        }
        return PsiTreeUtil.getParentOfType(position, TerragruntBody.class) != null;
    }

    private boolean isInExpression(PsiElement position) {
        if (PsiTreeUtil.getParentOfType(position, TerragruntGetAttr.class) != null) return true;
        if (PsiTreeUtil.getParentOfType(position, TerragruntObjectExpr.class) != null) return true;
        if (PsiTreeUtil.getParentOfType(position, TerragruntTupleExpr.class) != null) return true;
        TerragruntAttribute attr = PsiTreeUtil.getParentOfType(position, TerragruntAttribute.class);
        return attr != null && attr.getExpression() != null && attr.getExpression().getTextRange().contains(position.getTextOffset());
    }

    private boolean isAfterDot(PsiElement position) {
        // Check if we're inside a GetAttr (i.e., after a dot)
        return PsiTreeUtil.getParentOfType(position, TerragruntGetAttr.class) != null;
    }

    private TerragruntBlock getDirectEnclosingBlock(PsiElement position) {
        TerragruntBody body = PsiTreeUtil.getParentOfType(position, TerragruntBody.class);
        if (body != null && body.getParent() instanceof TerragruntBlock block) {
            return block;
        }
        return null;
    }

    private void addTopLevelCompletions(@NotNull CompletionResultSet result) {
        for (var entry : TerragruntSchema.getAllBlocks().entrySet()) {
            String name = entry.getKey();
            boolean hasLabel = entry.getValue().hasLabel();
            String template = hasLabel ? name + " \"\" {\n  \n}" : name + " {\n  \n}";
            result.addElement(LookupElementBuilder.create(name)
                    .withTypeText("block")
                    .withInsertHandler((ctx, item) -> {
                        ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), template);
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + template.indexOf('\n') + 3);
                    })
                    .bold());
        }
        for (var entry : TerragruntSchema.getTopLevelAttributes().entrySet()) {
            result.addElement(LookupElementBuilder.create(entry.getKey())
                    .withTypeText(entry.getValue().type())
                    .withInsertHandler((ctx, item) -> {
                        String insert = entry.getKey() + " = ";
                        ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), insert);
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + insert.length());
                    }));
        }
    }

    private void addBlockAttributeCompletions(@NotNull TerragruntBlock block, @NotNull CompletionResultSet result) {
        String blockType = block.getIdentifier().getText();
        TerragruntSchema.BlockDef def = TerragruntSchema.getBlock(blockType);
        if (def == null) return;

        for (var attr : def.attributes()) {
            result.addElement(LookupElementBuilder.create(attr.name())
                    .withTypeText(attr.type() + (attr.required() ? " (required)" : ""))
                    .withInsertHandler((ctx, item) -> {
                        String insert = attr.name() + " = ";
                        ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), insert);
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + insert.length());
                    }));
        }
        for (String nested : def.nestedBlocks()) {
            result.addElement(LookupElementBuilder.create(nested)
                    .withTypeText("block")
                    .withInsertHandler((ctx, item) -> {
                        String insert = nested + " {\n  \n}";
                        ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), insert);
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + insert.indexOf('\n') + 3);
                    })
                    .bold());
        }
    }
    private void addExpressionCompletions(@NotNull CompletionResultSet result) {
        // Variable prefixes
        result.addElement(LookupElementBuilder.create("local").withTypeText("locals reference").bold());
        result.addElement(LookupElementBuilder.create("dependency").withTypeText("dependency reference").bold());
        result.addElement(LookupElementBuilder.create("feature").withTypeText("feature reference").bold());
        result.addElement(LookupElementBuilder.create("include").withTypeText("include reference").bold());
        result.addElement(LookupElementBuilder.create("values").withTypeText("stack values reference").bold());

        // Functions
        for (var func : TerragruntSchema.getFunctions()) {
            result.addElement(LookupElementBuilder.create(func.name())
                    .withTailText(func.signature())
                    .withTypeText(func.description())
                    .withInsertHandler((ctx, item) -> {
                        String insert = func.name() + "()";
                        ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), insert);
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + insert.length() - 1);
                    }));
        }
    }

    private void addDotCompletions(@NotNull PsiElement position, @NotNull CompletionResultSet result) {
        TerragruntPostfixExpr postfix = PsiTreeUtil.getParentOfType(position, TerragruntPostfixExpr.class);
        if (postfix == null) return;

        TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
        if (primary == null) return;
        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
        if (varExpr == null) return;
        String rootVar = varExpr.getIdentifier().getText();

        // Count completed get_attrs before the current one (the one being typed)
        TerragruntGetAttr currentGetAttr = PsiTreeUtil.getParentOfType(position, TerragruntGetAttr.class);
        PsiElement[] getAttrs = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
        int depth = 0;
        if (getAttrs != null) {
            for (PsiElement ga : getAttrs) {
                if (ga != currentGetAttr && ga.getTextOffset() < position.getTextOffset()) depth++;
            }
        }

        PsiFile file = position.getContainingFile();

        if ("dependency".equals(rootVar)) {
            if (depth == 0) {
                // dependency. -> suggest dependency names
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                    if ("dependency".equals(block.getIdentifier().getText())) {
                        for (TerragruntLabel label : block.getLabelList()) {
                            String name = label.getText().replace("\"", "");
                            result.addElement(LookupElementBuilder.create(name).withTypeText("dependency").bold());
                        }
                    }
                }
            } else if (depth == 1) {
                // dependency.vpc. -> suggest "outputs"
                result.addElement(LookupElementBuilder.create("outputs").withTypeText("dependency outputs").bold());
            } else if (depth == 2) {
                // dependency.vpc.outputs. -> suggest mock_outputs keys
                String depName = getAttrs != null && getAttrs.length > 0
                        ? ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText() : null;
                if (depName != null) {
                    for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                        if (!"dependency".equals(block.getIdentifier().getText())) continue;
                        for (TerragruntLabel label : block.getLabelList()) {
                            if (!depName.equals(label.getText().replace("\"", ""))) continue;
                        }
                        // Find mock_outputs attribute in this block
                        TerragruntBody body = block.getBody();
                        if (body == null) continue;
                        for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                            if (!"mock_outputs".equals(attr.getIdentifier().getText())) continue;
                            // Find object keys in mock_outputs value
                            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
                            if (obj == null) continue;
                            for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                                PsiElement key = elem.getFirstChild();
                                if (key != null) {
                                    result.addElement(LookupElementBuilder.create(key.getText())
                                            .withTypeText("output").bold());
                                }
                            }
                        }
                    }
                }
            }
        } else if ("local".equals(rootVar)) {
            if (depth == 0) {
                // local. -> suggest locals attributes
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                    if ("locals".equals(block.getIdentifier().getText())) {
                        TerragruntBody body = block.getBody();
                        if (body == null) continue;
                        for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                            result.addElement(LookupElementBuilder.create(attr.getIdentifier().getText()).withTypeText("local"));
                        }
                    }
                }
            }
        } else if ("feature".equals(rootVar)) {
            if (depth == 0) {
                // feature. -> suggest feature names
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                    if ("feature".equals(block.getIdentifier().getText())) {
                        for (TerragruntLabel label : block.getLabelList()) {
                            String name = label.getText().replace("\"", "");
                            result.addElement(LookupElementBuilder.create(name).withTypeText("feature").bold());
                        }
                    }
                }
            } else if (depth == 1) {
                // feature.X. -> suggest "value"
                result.addElement(LookupElementBuilder.create("value").withTypeText("feature value").bold());
            }
        } else if ("include".equals(rootVar)) {
            if (depth == 0) {
                // include. -> suggest include labels
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                    if ("include".equals(block.getIdentifier().getText())) {
                        for (TerragruntLabel label : block.getLabelList()) {
                            String name = label.getText().replace("\"", "");
                            result.addElement(LookupElementBuilder.create(name).withTypeText("include").bold());
                        }
                    }
                }
            } else if (depth == 1) {
                // include.X. -> suggest exposed attributes
                result.addElement(LookupElementBuilder.create("locals").withTypeText("exposed config"));
                result.addElement(LookupElementBuilder.create("inputs").withTypeText("exposed config"));
                result.addElement(LookupElementBuilder.create("remote_state").withTypeText("exposed config"));
            } else if (depth == 2 && getAttrs != null && getAttrs.length >= 2) {
                // include.X.locals. -> suggest locals from included file
                String includeName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();

                TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                if (includeBlock != null) {
                    PsiFile targetFile = TerragruntFileResolver.resolveInclude(includeBlock);
                    if (targetFile != null && "locals".equals(section)) {
                        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(targetFile, TerragruntBlock.class)) {
                            if (!"locals".equals(block.getIdentifier().getText())) continue;
                            TerragruntBody body = block.getBody();
                            if (body == null) continue;
                            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                                result.addElement(LookupElementBuilder.create(attr.getIdentifier().getText())
                                        .withTypeText("included local").bold());
                            }
                        }
                    }
                }
            }
        } else if ("values".equals(rootVar)) {
            // values. -> suggest keys from any terragrunt.values.hcl or top-level attributes in same file
            // For now, look for a values block pattern or top-level attributes that look like values
            // This is a placeholder until cross-file resolution is implemented
        }
    }
}
