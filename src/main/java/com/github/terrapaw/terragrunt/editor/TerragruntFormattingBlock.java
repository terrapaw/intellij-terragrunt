package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TerragruntFormattingBlock extends AbstractBlock {
    private final SpacingBuilder spacingBuilder;
    private final Alignment valueAlignment;

    protected TerragruntFormattingBlock(@NotNull ASTNode node, @Nullable Wrap wrap,
                                        @Nullable Alignment alignment, SpacingBuilder spacingBuilder,
                                        @Nullable Alignment valueAlignment) {
        super(node, wrap, alignment);
        this.spacingBuilder = spacingBuilder;
        this.valueAlignment = valueAlignment;
    }

    protected TerragruntFormattingBlock(@NotNull ASTNode node, @Nullable Wrap wrap,
                                        @Nullable Alignment alignment, SpacingBuilder spacingBuilder) {
        this(node, wrap, alignment, spacingBuilder, null);
    }

    @Override
    protected List<Block> buildChildren() {
        List<Block> blocks = new ArrayList<>();
        IElementType myType = myNode.getElementType();

        // Create alignment for = signs within body/object — reset after blank lines or object-valued attrs
        Alignment equalsAlign = null;
        boolean needsAlignment = (myType == TerragruntTypes.BODY || myType == TerragruntTypes.OBJECT_EXPR);
        if (needsAlignment) {
            equalsAlign = Alignment.createAlignment(true);
        }

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == TokenType.WHITE_SPACE) {
                // Reset alignment after blank lines (2+ newlines)
                if (needsAlignment && hasTwoNewlines(child.getText())) {
                    equalsAlign = Alignment.createAlignment(true);
                }
            } else {
                IElementType childType = child.getElementType();

                // Reset alignment AFTER an attribute with object/array value
                boolean isAttrWithObject = (childType == TerragruntTypes.ATTRIBUTE || childType == TerragruntTypes.OBJECT_ELEM)
                        && hasObjectOrArrayValue(child);

                // Reset alignment BEFORE object-valued attr (previous attrs get their own group)
                if (needsAlignment && isAttrWithObject) {
                    equalsAlign = Alignment.createAlignment(true);
                }

                Alignment childValueAlign = null;
                if ((childType == TerragruntTypes.ATTRIBUTE || childType == TerragruntTypes.OBJECT_ELEM)
                        && equalsAlign != null && !isAttrWithObject) {
                    childValueAlign = equalsAlign;
                }

                blocks.add(makeSubBlock(child, equalsAlign != null ? childValueAlign : null));

                // After an object-valued attribute, reset alignment for next group
                if (needsAlignment && isAttrWithObject) {
                    equalsAlign = Alignment.createAlignment(true);
                }
            }
            child = child.getTreeNext();
        }
        return blocks;
    }

    private TerragruntFormattingBlock makeSubBlock(ASTNode child, @Nullable Alignment equalsAlign) {
        IElementType myType = myNode.getElementType();
        IElementType childType = child.getElementType();

        // Inside an attribute or object elem: align the = sign
        if ((myType == TerragruntTypes.ATTRIBUTE || myType == TerragruntTypes.OBJECT_ELEM)
                && childType == TerragruntTypes.EQUALS && valueAlignment != null) {
            return new TerragruntFormattingBlock(child, null, valueAlignment, spacingBuilder, null);
        }

        // Pass alignment to attribute/object_elem children (skip if value is object/array)
        Alignment childValueAlign = null;
        if ((childType == TerragruntTypes.ATTRIBUTE || childType == TerragruntTypes.OBJECT_ELEM) && equalsAlign != null) {
            // Don't align attributes whose value is an object or array (terragrunt fmt behaviour)
            if (!hasObjectOrArrayValue(child)) {
                childValueAlign = equalsAlign;
            }
        }

        return new TerragruntFormattingBlock(child, null, null, spacingBuilder, childValueAlign);
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return spacingBuilder.getSpacing(this, child1, child2);
    }

    @Override
    public Indent getIndent() {
        ASTNode parent = myNode.getTreeParent();
        if (parent == null) return Indent.getNoneIndent();

        IElementType parentType = parent.getElementType();
        IElementType myType = myNode.getElementType();

        if (parentType == TerragruntTypes.BODY && parent.getTreeParent() != null
                && parent.getTreeParent().getElementType() == TerragruntTypes.BLOCK) {
            return Indent.getNormalIndent();
        }

        if (parentType == TerragruntTypes.OBJECT_EXPR
                && myType != TerragruntTypes.LBRACE && myType != TerragruntTypes.RBRACE) {
            return Indent.getNormalIndent();
        }

        if (parentType == TerragruntTypes.TUPLE_EXPR
                && myType != TerragruntTypes.LBRACKET && myType != TerragruntTypes.RBRACKET) {
            return Indent.getNormalIndent();
        }

        return Indent.getNoneIndent();
    }

    @Override
    public boolean isLeaf() {
        return myNode.getFirstChildNode() == null;
    }

    @NotNull
    @Override
    public ChildAttributes getChildAttributes(int newChildIndex) {
        IElementType type = myNode.getElementType();
        if (type == TerragruntTypes.BODY) {
            if (myNode.getTreeParent() != null && myNode.getTreeParent().getElementType() == TerragruntTypes.BLOCK) {
                return new ChildAttributes(Indent.getNormalIndent(), null);
            }
            return new ChildAttributes(Indent.getNoneIndent(), null);
        }
        if (type == TerragruntTypes.BLOCK || type == TerragruntTypes.OBJECT_EXPR || type == TerragruntTypes.TUPLE_EXPR) {
            return new ChildAttributes(Indent.getNormalIndent(), null);
        }
        return new ChildAttributes(Indent.getNoneIndent(), null);
    }

    private static boolean hasTwoNewlines(String text) {
        int first = text.indexOf('\n');
        return first != -1 && text.indexOf('\n', first + 1) != -1;
    }

    private static boolean hasObjectOrArrayValue(ASTNode attrNode) {
        // Exclude from alignment if the value directly starts with { or [ (block object/array)
        // but NOT if it's inside a function call like merge({...}) or an inline list
        ASTNode eq = null;
        for (ASTNode child = attrNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == TerragruntTypes.EQUALS) {
                eq = child;
                break;
            }
        }
        if (eq == null) return false;

        // Find first non-whitespace token after =
        ASTNode afterEq = eq.getTreeNext();
        while (afterEq != null && afterEq.getElementType() == TokenType.WHITE_SPACE) {
            afterEq = afterEq.getTreeNext();
        }
        if (afterEq == null) return false;

        // Check if the value spans multiple lines (the expression after = contains \n)
        String valueText = afterEq.getText();
        if (!valueText.contains("\n")) return false;

        // Check if value directly starts with { or [ (not wrapped in a function)
        String trimmed = valueText.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
