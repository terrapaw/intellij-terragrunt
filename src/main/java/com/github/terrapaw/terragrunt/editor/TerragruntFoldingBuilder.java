package com.github.terrapaw.terragrunt.editor;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectExpr;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TerragruntFoldingBuilder extends FoldingBuilderEx {
    @NotNull
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(root, TerragruntBlock.class);
        for (TerragruntBlock block : blocks) {
            TextRange range = block.getTextRange();
            if (range.getLength() > 1) {
                descriptors.add(new FoldingDescriptor(block.getNode(), range));
            }
        }
        // Fold top-level attributes with object values (inputs = { ... })
        Collection<TerragruntAttribute> attrs = PsiTreeUtil.findChildrenOfType(root, TerragruntAttribute.class);
        for (TerragruntAttribute attr : attrs) {
            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
            if (obj != null && obj.getTextRange().getLength() > 1) {
                descriptors.add(new FoldingDescriptor(attr.getNode(), attr.getTextRange()));
            }
        }
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        PsiElement psi = node.getPsi();
        if (psi instanceof TerragruntBlock block) {
            String type = TerragruntPsiUtil.getBlockType(block);
            if (type == null) return "...";
            StringBuilder sb = new StringBuilder(type);
            for (var label : block.getLabelList()) {
                sb.append(" ").append(label.getText());
            }
            sb.append(" {...}");
            return sb.toString();
        }
        if (psi instanceof TerragruntAttribute attr) {
            return attr.getIdentifier().getText() + " = {...}";
        }
        return "{...}";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
