package com.github.terrapaw.terragrunt.completion;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.github.terrapaw.terragrunt.reference.TerragruntFileResolver;
import com.github.terrapaw.terragrunt.schema.TerragruntSchema;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

                        if (isAfterDot(position)) {
                            addDotCompletions(position, result);
                        } else if (isInBody(position)) {
                            // Inside a block body or top-level — offer attributes/blocks
                            TerragruntBlock enclosingBlock = getDirectEnclosingBlock(position);
                            if (enclosingBlock == null) {
                                addTopLevelCompletions(result);
                            } else {
                                addBlockAttributeCompletions(enclosingBlock, result);
                            }
                        } else if (isInExpression(position)) {
                            addExpressionCompletions(result);
                            addForVariableCompletions(position, result);
                        } else {
                            // Fallback: inside an attribute value with broken PSI
                            TerragruntAttribute attr = PsiTreeUtil.getParentOfType(position, TerragruntAttribute.class);
                            if (attr != null) {
                                addExpressionCompletions(result);
                                addForVariableCompletions(position, result);
                            }
                        }
                    }
                });
    }

    private boolean isInBody(PsiElement position) {
        if (PsiTreeUtil.getParentOfType(position, TerragruntGetAttr.class) != null) return false;
        if (PsiTreeUtil.getParentOfType(position, TerragruntObjectExpr.class) != null) return false;
        if (PsiTreeUtil.getParentOfType(position, TerragruntTupleExpr.class) != null) return false;
        TerragruntAttribute attr = PsiTreeUtil.getParentOfType(position, TerragruntAttribute.class);
        if (attr != null && attr.getExpression() != null && attr.getExpression().getTextRange().contains(position.getTextOffset())) {
            return false;
        }
        // Text-based fallback: check if we're inside a map value (not a block body)
        // by looking at what precedes the { that contains us
        String text = position.getContainingFile().getText();
        int offset = position.getTextOffset();
        int depth = 0;
        int lastOpenBraceAtDepth0 = -1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) lastOpenBraceAtDepth0 = i;
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        // If depth > 1, definitely inside a nested structure
        if (depth > 1) return false;
        // If depth == 1, check if the { was preceded by = (map) or identifier (block)
        if (depth == 1 && lastOpenBraceAtDepth0 > 0) {
            int i = lastOpenBraceAtDepth0 - 1;
            while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
            if (i >= 0 && text.charAt(i) == '=') return false; // it's a map value, not a block
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
        if (PsiTreeUtil.getParentOfType(position, TerragruntGetAttr.class) != null) return true;
        // Fallback: check if the character before the identifier is a dot
        int offset = position.getTextOffset();
        if (offset > 0) {
            String docText = position.getContainingFile().getText();
            // Walk back past whitespace to find a dot
            int i = offset - 1;
            while (i >= 0 && Character.isWhitespace(docText.charAt(i))) i--;
            if (i >= 0 && docText.charAt(i) == '.') return true;
        }
        return false;
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

        // For expressions
        result.addElement(LookupElementBuilder.create("[for")
                .withTailText(" v in list : v]")
                .withTypeText("for tuple")
                .withInsertHandler((ctx, item) -> {
                    String insert = "[for v in  : v]";
                    ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), insert);
                    ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + 10);
                }));
        result.addElement(LookupElementBuilder.create("{for")
                .withTailText(" k, v in map : k => v}")
                .withTypeText("for object")
                .withInsertHandler((ctx, item) -> {
                    String insert = "{for k, v in  : k => v}";
                    ctx.getDocument().replaceString(ctx.getStartOffset(), ctx.getTailOffset(), insert);
                    ctx.getEditor().getCaretModel().moveToOffset(ctx.getStartOffset() + 13);
                }));

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

    private void addForVariableCompletions(@NotNull PsiElement position, @NotNull CompletionResultSet result) {
        // Check if we're inside a for expression (for_tuple_expr or for_object_expr)
        TerragruntForTupleExpr forTuple = PsiTreeUtil.getParentOfType(position, TerragruntForTupleExpr.class);
        TerragruntForObjectExpr forObject = PsiTreeUtil.getParentOfType(position, TerragruntForObjectExpr.class);
        TerragruntForIntro intro = null;
        if (forTuple != null) intro = PsiTreeUtil.getChildOfType(forTuple, TerragruntForIntro.class);
        if (forObject != null) intro = PsiTreeUtil.getChildOfType(forObject, TerragruntForIntro.class);
        if (intro == null) return;

        // Extract variable names from for_intro: FOR IDENTIFIER (COMMA IDENTIFIER)? IN ...
        com.intellij.lang.ASTNode node = intro.getNode().getFirstChildNode();
        while (node != null) {
            if (node.getElementType() == TerragruntTypes.IDENTIFIER) {
                String varName = node.getText();
                result.addElement(LookupElementBuilder.create(varName)
                        .withTypeText("for variable")
                        .bold());
            }
            node = node.getTreeNext();
        }
    }

    private void addDotCompletions(@NotNull PsiElement position, @NotNull CompletionResultSet result) {
        TerragruntPostfixExpr postfix = PsiTreeUtil.getParentOfType(position, TerragruntPostfixExpr.class);
        if (postfix != null) {
            addDotCompletionsFromPsi(position, postfix, result);
            return;
        }
        // Fallback: parse the text before cursor
        addDotCompletionsFromText(position, result);
    }

    private void addDotCompletionsFromPsi(@NotNull PsiElement position, @NotNull TerragruntPostfixExpr postfix, @NotNull CompletionResultSet result) {

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

        PsiFile file = position.getContainingFile().getOriginalFile();

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
            } else if (depth == 1 && getAttrs != null && getAttrs.length >= 1) {
                // local.X. -> depends on what X is:
                // - include.X.locals alias -> suggest attributes directly
                // - read_terragrunt_config alias -> suggest "locals", "inputs"
                String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                PsiFile resolvedFile = resolveLocalAliasForCompletion(file, aliasName);
                if (resolvedFile != null) {
                    if (isIncludeLocalsAlias(file, aliasName)) {
                        // Already points to locals — suggest attributes directly
                        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(resolvedFile, TerragruntBlock.class)) {
                            if (!"locals".equals(block.getIdentifier().getText())) continue;
                            TerragruntBody body = block.getBody();
                            if (body == null) continue;
                            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                                result.addElement(LookupElementBuilder.create(attr.getIdentifier().getText())
                                        .withTypeText("from " + resolvedFile.getName()).bold());
                            }
                        }
                    } else if (isIncludeInputsAlias(file, aliasName)) {
                        // Already points to inputs — suggest input keys directly
                        for (TerragruntAttribute attr : PsiTreeUtil.findChildrenOfType(resolvedFile, TerragruntAttribute.class)) {
                            if (!"inputs".equals(attr.getIdentifier().getText())) continue;
                            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
                            if (obj == null) continue;
                            for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                                PsiElement key = elem.getFirstChild();
                                if (key != null) {
                                    result.addElement(LookupElementBuilder.create(key.getText())
                                            .withTypeText("from " + resolvedFile.getName()).bold());
                                }
                            }
                        }
                    } else {
                        // read_terragrunt_config — need to go through .locals/.inputs first
                        result.addElement(LookupElementBuilder.create("locals").withTypeText("config section").bold());
                        result.addElement(LookupElementBuilder.create("inputs").withTypeText("config section").bold());
                    }
                }
            } else if (depth == 2 && getAttrs != null && getAttrs.length >= 2) {
                // local.X.locals. or local.X.inputs. -> suggest attributes from resolved file
                String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                String section = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                if ("locals".equals(section) || "inputs".equals(section)) {
                    PsiFile resolvedFile = resolveLocalAliasForCompletion(file, aliasName);
                    if (resolvedFile != null) {
                        // Find the matching top-level block or attribute
                        if ("locals".equals(section)) {
                            for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(resolvedFile, TerragruntBlock.class)) {
                                if (!"locals".equals(block.getIdentifier().getText())) continue;
                                TerragruntBody body = block.getBody();
                                if (body == null) continue;
                                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                                    result.addElement(LookupElementBuilder.create(attr.getIdentifier().getText())
                                            .withTypeText("from " + resolvedFile.getName()).bold());
                                }
                            }
                        } else {
                            // inputs is a top-level attribute with a map value
                            for (TerragruntAttribute attr : PsiTreeUtil.findChildrenOfType(resolvedFile, TerragruntAttribute.class)) {
                                if (!"inputs".equals(attr.getIdentifier().getText())) continue;
                                TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
                                if (obj == null) continue;
                                for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                                    PsiElement key = elem.getFirstChild();
                                    if (key != null) {
                                        result.addElement(LookupElementBuilder.create(key.getText())
                                                .withTypeText("from " + resolvedFile.getName()).bold());
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (depth > 2 && getAttrs != null && getAttrs.length >= 2) {
                // Deep chain: local.alias.locals.nested_alias.locals. or local.alias.locals.nested_alias.
                String aliasName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                String firstSection = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                if ("locals".equals(firstSection) || "inputs".equals(firstSection)) {
                    PsiFile resolvedFile = resolveLocalAliasForCompletion(file, aliasName);
                    if (resolvedFile != null) {
                        String lastAttr = ((TerragruntGetAttr) getAttrs[depth - 1]).getIdentifier().getText();
                        if ("locals".equals(lastAttr) || "inputs".equals(lastAttr)) {
                            // Last was a section — suggest attributes from resolved file
                            PsiFile deepFile = resolveDeepChainForCompletion(resolvedFile, getAttrs, 2, depth, file);
                            if (deepFile != null) {
                                addCompletionsFromFile(deepFile, lastAttr, result);
                            }
                        } else {
                            // Last was an alias name — suggest locals/inputs
                            PsiFile deepFile = resolveDeepChainForCompletion(resolvedFile, getAttrs, 2, depth - 1, file);
                            if (deepFile != null) {
                                PsiFile aliasFile = resolveLocalAliasForCompletion(deepFile, lastAttr);
                                if (aliasFile != null) {
                                    if (isIncludeLocalsAlias(deepFile, lastAttr)) {
                                        addCompletionsFromFile(aliasFile, "locals", result);
                                    } else {
                                        result.addElement(LookupElementBuilder.create("locals").withTypeText("config section").bold());
                                        result.addElement(LookupElementBuilder.create("inputs").withTypeText("config section").bold());
                                    }
                                }
                            }
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
                // include.X.locals. or include.X.inputs. -> suggest from included file
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
                    } else if (targetFile != null && "inputs".equals(section)) {
                        for (TerragruntAttribute attr : PsiTreeUtil.findChildrenOfType(targetFile, TerragruntAttribute.class)) {
                            if (!"inputs".equals(attr.getIdentifier().getText())) continue;
                            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
                            if (obj == null) continue;
                            for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                                PsiElement key = elem.getFirstChild();
                                if (key != null) {
                                    result.addElement(LookupElementBuilder.create(key.getText())
                                            .withTypeText("included input").bold());
                                }
                            }
                        }
                    }
                }
            } else if (depth > 2 && getAttrs != null && getAttrs.length >= 2) {
                // Deep chain: include.X.locals.alias.locals. -> resolve through aliases
                // At odd depths (3, 5, ...) after an alias, suggest "locals"/"inputs"
                // At even depths (4, 6, ...) after "locals"/"inputs", suggest attributes
                String includeName = ((TerragruntGetAttr) getAttrs[0]).getIdentifier().getText();
                String firstSection = ((TerragruntGetAttr) getAttrs[1]).getIdentifier().getText();
                if ("locals".equals(firstSection)) {
                    TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                    if (includeBlock != null) {
                        PsiFile targetFile = TerragruntFileResolver.resolveInclude(includeBlock);
                        if (targetFile != null) {
                            // Check if the last completed attr is an alias name (suggest locals/inputs)
                            // or a section name like "locals" (suggest attributes from resolved file)
                            String lastAttr = ((TerragruntGetAttr) getAttrs[depth - 1]).getIdentifier().getText();
                            if ("locals".equals(lastAttr) || "inputs".equals(lastAttr)) {
                                // Last was a section — resolve the chain and suggest attributes
                                PsiFile deepFile = resolveDeepChainForCompletion(targetFile, getAttrs, 2, depth, file);
                                if (deepFile != null) {
                                    addCompletionsFromFile(deepFile, lastAttr, result);
                                }
                            } else {
                                // Last was an alias name — check if it's a read_terragrunt_config alias
                                PsiFile deepFile = resolveDeepChainForCompletion(targetFile, getAttrs, 2, depth - 1, file);
                                if (deepFile != null) {
                                    PsiFile aliasFile = resolveLocalAliasForCompletion(deepFile, lastAttr);
                                    if (aliasFile != null) {
                                        if (isIncludeLocalsAlias(deepFile, lastAttr)) {
                                            addCompletionsFromFile(aliasFile, "locals", result);
                                        } else {
                                            result.addElement(LookupElementBuilder.create("locals").withTypeText("config section").bold());
                                            result.addElement(LookupElementBuilder.create("inputs").withTypeText("config section").bold());
                                        }
                                    }
                                }
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

    private boolean isIncludeLocalsAlias(PsiFile file, String aliasName) {
        return isIncludeAlias(file, aliasName, "locals");
    }

    private boolean isIncludeInputsAlias(PsiFile file, String aliasName) {
        return isIncludeAlias(file, aliasName, "inputs");
    }

    private boolean isIncludeAlias(PsiFile file, String aliasName, String section) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (!aliasName.equals(attr.getIdentifier().getText())) continue;
                TerragruntPostfixExpr postfix = PsiTreeUtil.findChildOfType(attr, TerragruntPostfixExpr.class);
                if (postfix == null) continue;
                TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                if (primary == null) continue;
                TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                if (varExpr != null && "include".equals(varExpr.getIdentifier().getText())) {
                    PsiElement[] gas = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                    if (gas != null && gas.length >= 2) {
                        String s = ((TerragruntGetAttr) gas[1]).getIdentifier().getText();
                        if (section.equals(s)) return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private PsiFile resolveLocalAliasForCompletion(PsiFile file, String aliasName) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (!aliasName.equals(attr.getIdentifier().getText())) continue;
                // Check for read_terragrunt_config(...)
                TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
                if (funcCall != null && "read_terragrunt_config".equals(funcCall.getIdentifier().getText())) {
                    return TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
                }
                // Check for include.X.locals
                TerragruntPostfixExpr postfix = PsiTreeUtil.findChildOfType(attr, TerragruntPostfixExpr.class);
                if (postfix != null) {
                    TerragruntPrimaryExpr primary = PsiTreeUtil.getChildOfType(postfix, TerragruntPrimaryExpr.class);
                    if (primary != null) {
                        TerragruntVariableExpr varExpr = PsiTreeUtil.getChildOfType(primary, TerragruntVariableExpr.class);
                        if (varExpr != null && "include".equals(varExpr.getIdentifier().getText())) {
                            PsiElement[] gas = PsiTreeUtil.getChildrenOfType(postfix, TerragruntGetAttr.class);
                            if (gas != null && gas.length >= 2) {
                                String section = ((TerragruntGetAttr) gas[1]).getIdentifier().getText();
                                if ("locals".equals(section) || "inputs".equals(section)) {
                                    String includeName = ((TerragruntGetAttr) gas[0]).getIdentifier().getText();
                                    TerragruntBlock includeBlock = TerragruntFileResolver.findIncludeBlock(file, includeName);
                                    if (includeBlock != null) {
                                        return TerragruntFileResolver.resolveInclude(includeBlock);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void addDotCompletionsFromText(@NotNull PsiElement position, @NotNull CompletionResultSet result) {
        int offset = position.getTextOffset();
        String fileText = position.getContainingFile().getText();
        int start = offset - 1;
        while (start >= 0 && (Character.isLetterOrDigit(fileText.charAt(start)) || fileText.charAt(start) == '.' || fileText.charAt(start) == '_' || fileText.charAt(start) == '-')) {
            start--;
        }
        start++;
        String prefix = fileText.substring(start, offset);
        if (prefix.endsWith(".")) prefix = prefix.substring(0, prefix.length() - 1);

        String[] parts = prefix.split("\\.");
        if (parts.length == 0) return;

        PsiFile file = position.getContainingFile().getOriginalFile();
        String rootVar = parts[0];

        if ("local".equals(rootVar) && parts.length == 2) {
            // local.X. -> depends on alias type
            PsiFile resolved = resolveLocalAliasForCompletion(file, parts[1]);
            if (resolved != null) {
                if (isIncludeLocalsAlias(file, parts[1])) {
                    // include.X.locals alias -> suggest attributes directly
                    for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(resolved, TerragruntBlock.class)) {
                        if (!"locals".equals(block.getIdentifier().getText())) continue;
                        TerragruntBody body = block.getBody();
                        if (body == null) continue;
                        for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                            result.addElement(LookupElementBuilder.create(attr.getIdentifier().getText())
                                    .withTypeText("from " + resolved.getName()).bold());
                        }
                    }
                } else if (isIncludeInputsAlias(file, parts[1])) {
                    // include.X.inputs alias -> suggest input keys directly
                    for (TerragruntAttribute attr : PsiTreeUtil.findChildrenOfType(resolved, TerragruntAttribute.class)) {
                        if (!"inputs".equals(attr.getIdentifier().getText())) continue;
                        TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
                        if (obj == null) continue;
                        for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                            PsiElement key = elem.getFirstChild();
                            if (key != null) {
                                result.addElement(LookupElementBuilder.create(key.getText())
                                        .withTypeText("from " + resolved.getName()).bold());
                            }
                        }
                    }
                } else {
                    // read_terragrunt_config alias -> suggest sections
                    result.addElement(LookupElementBuilder.create("locals").withTypeText("config section").bold());
                    result.addElement(LookupElementBuilder.create("inputs").withTypeText("config section").bold());
                }
            }
        } else if ("local".equals(rootVar) && parts.length == 3 && "locals".equals(parts[2])) {
            // local.X.locals. -> suggest attributes from resolved file's locals
            PsiFile resolved = resolveLocalAliasForCompletion(file, parts[1]);
            if (resolved != null) {
                for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(resolved, TerragruntBlock.class)) {
                    if (!"locals".equals(block.getIdentifier().getText())) continue;
                    TerragruntBody body = block.getBody();
                    if (body == null) continue;
                    for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                        result.addElement(LookupElementBuilder.create(attr.getIdentifier().getText())
                                .withTypeText("from " + resolved.getName()).bold());
                    }
                }
            }
        } else if ("dependency".equals(rootVar) && parts.length == 1) {
            // dependency. -> suggest dependency names
            for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                if (!"dependency".equals(block.getIdentifier().getText())) continue;
                for (TerragruntLabel label : block.getLabelList()) {
                    result.addElement(LookupElementBuilder.create(label.getText().replace("\"", "")).withTypeText("dependency").bold());
                }
            }
        } else if ("dependency".equals(rootVar) && parts.length == 2) {
            // dependency.X. -> suggest "outputs"
            result.addElement(LookupElementBuilder.create("outputs").withTypeText("dependency outputs").bold());
        }
    }

    /**
     * Resolves a deep chain of aliases for completion, returning the final file to suggest from.
     */
    @Nullable
    private PsiFile resolveDeepChainForCompletion(PsiFile currentFile, PsiElement[] getAttrs, int startIndex, int depth, PsiFile originFile) {
        PsiFile file = currentFile;
        int i = startIndex;

        while (i < depth && file != null) {
            String name = ((TerragruntGetAttr) getAttrs[i]).getIdentifier().getText();
            // If next attr is "locals" or "inputs", this is an alias to resolve
            if (i + 1 < getAttrs.length) {
                String next = ((TerragruntGetAttr) getAttrs[i + 1]).getIdentifier().getText();
                if ("locals".equals(next) || "inputs".equals(next)) {
                    file = resolveLocalAliasForCompletion(file, name);
                    i += 2;
                    continue;
                }
            }
            i++;
        }
        return file;
    }

    private void addCompletionsFromFile(PsiFile file, String section, com.intellij.codeInsight.completion.CompletionResultSet result) {
        if ("locals".equals(section)) {
            for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
                if (!"locals".equals(block.getIdentifier().getText())) continue;
                TerragruntBody body = block.getBody();
                if (body == null) continue;
                for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                    result.addElement(LookupElementBuilder.create(attr.getIdentifier().getText())
                            .withTypeText("from " + file.getName()).bold());
                }
            }
        } else if ("inputs".equals(section)) {
            for (TerragruntAttribute attr : PsiTreeUtil.findChildrenOfType(file, TerragruntAttribute.class)) {
                if (!"inputs".equals(attr.getIdentifier().getText())) continue;
                TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
                if (obj == null) continue;
                for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                    PsiElement key = elem.getFirstChild();
                    if (key != null) {
                        result.addElement(LookupElementBuilder.create(key.getText())
                                .withTypeText("from " + file.getName()).bold());
                    }
                }
            }
        }
    }
}
