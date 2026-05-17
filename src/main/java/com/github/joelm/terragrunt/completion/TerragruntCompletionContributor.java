package com.github.joelm.terragrunt.completion;

import com.github.joelm.terragrunt.lang.psi.TerragruntBlock;
import com.github.joelm.terragrunt.lang.psi.TerragruntBody;
import com.github.joelm.terragrunt.lang.psi.TerragruntTypes;
import com.github.joelm.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class TerragruntCompletionContributor extends CompletionContributor {
    public TerragruntCompletionContributor() {
        // Completion at identifier positions
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        PsiElement position = parameters.getPosition();
                        PsiElement parent = position.getParent();

                        // Determine context
                        TerragruntBlock enclosingBlock = PsiTreeUtil.getParentOfType(position, TerragruntBlock.class);

                        if (enclosingBlock == null || isTopLevelBody(parent)) {
                            addTopLevelCompletions(result);
                        } else {
                            addBlockAttributeCompletions(enclosingBlock, result);
                        }

                        // Always offer functions in expression context
                        addFunctionCompletions(result);
                    }
                });
    }

    private boolean isTopLevelBody(PsiElement element) {
        TerragruntBody body = PsiTreeUtil.getParentOfType(element, TerragruntBody.class);
        return body != null && body.getParent() != null &&
               !(body.getParent() instanceof TerragruntBlock);
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
        PsiElement firstChild = block.getFirstChild();
        if (firstChild == null) return;
        String blockType = firstChild.getText();
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

    private void addFunctionCompletions(@NotNull CompletionResultSet result) {
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
}
