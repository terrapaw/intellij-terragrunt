package com.github.joelm.terragrunt.editor;

import com.github.joelm.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
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
        return spacingBuilder.getSpacing(this, child1, child2);
    }

    @Override
    public Indent getIndent() {
        if (myNode.getTreeParent() == null) return Indent.getNoneIndent();
        if (myNode.getTreeParent().getElementType() == TerragruntTypes.BODY) {
            // Indent content inside blocks
            if (myNode.getTreeParent().getTreeParent() != null &&
                myNode.getTreeParent().getTreeParent().getElementType() == TerragruntTypes.BLOCK) {
                return Indent.getNormalIndent();
            }
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
        if (myNode.getElementType() == TerragruntTypes.BODY || myNode.getElementType() == TerragruntTypes.BLOCK) {
            return new ChildAttributes(Indent.getNormalIndent(), null);
        }
        return new ChildAttributes(Indent.getNoneIndent(), null);
    }
}
