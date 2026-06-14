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

        // Create shared alignment for = signs within body/object
        Alignment equalsAlign = null;
        if (myType == TerragruntTypes.BODY || myType == TerragruntTypes.OBJECT_EXPR) {
            equalsAlign = Alignment.createAlignment(true);
        }

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() != TokenType.WHITE_SPACE) {
                blocks.add(makeSubBlock(child, equalsAlign));
            } else if (equalsAlign != null && hasTwoNewlines(child.getText())) {
                // Reset alignment after blank lines (same as terragrunt fmt)
                equalsAlign = Alignment.createAlignment(true);
            }
            child = child.getTreeNext();
        }
        return blocks;
    }

    private TerragruntFormattingBlock makeSubBlock(ASTNode child, @Nullable Alignment equalsAlign) {
        IElementType myType = myNode.getElementType();
        IElementType childType = child.getElementType();

        // Inside an attribute: align the = sign
        if (myType == TerragruntTypes.ATTRIBUTE && childType == TerragruntTypes.EQUALS && valueAlignment != null) {
            return new TerragruntFormattingBlock(child, null, valueAlignment, spacingBuilder, null);
        }

        // Pass alignment to attribute/object_elem children
        Alignment childValueAlign = null;
        if ((childType == TerragruntTypes.ATTRIBUTE || childType == TerragruntTypes.OBJECT_ELEM) && equalsAlign != null) {
            childValueAlign = equalsAlign;
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
}
