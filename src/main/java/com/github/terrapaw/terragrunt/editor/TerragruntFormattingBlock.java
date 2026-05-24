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

    protected TerragruntFormattingBlock(@NotNull ASTNode node, @Nullable Wrap wrap,
                                        @Nullable Alignment alignment, SpacingBuilder spacingBuilder) {
        super(node, wrap, alignment);
        this.spacingBuilder = spacingBuilder;
    }

    @Override
    protected List<Block> buildChildren() {
        List<Block> blocks = new ArrayList<>();
        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() != TokenType.WHITE_SPACE) {
                blocks.add(new TerragruntFormattingBlock(child, null, null, spacingBuilder));
            }
            child = child.getTreeNext();
        }
        return blocks;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return null; // Don't enforce any spacing - preserve user formatting
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
        if (type == TerragruntTypes.BLOCK || type == TerragruntTypes.BODY
                || type == TerragruntTypes.OBJECT_EXPR || type == TerragruntTypes.TUPLE_EXPR) {
            return new ChildAttributes(Indent.getNormalIndent(), null);
        }
        return new ChildAttributes(Indent.getNoneIndent(), null);
    }
}
