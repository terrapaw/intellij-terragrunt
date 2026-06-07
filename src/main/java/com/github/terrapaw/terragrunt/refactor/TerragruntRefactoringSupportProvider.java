package com.github.terrapaw.terragrunt.refactor;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TerragruntRefactoringSupportProvider extends RefactoringSupportProvider {
    @Override
    public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, @Nullable PsiElement context) {
        // Allow inline rename for attribute identifiers inside locals block
        if (element.getParent() instanceof TerragruntAttribute attr) {
            TerragruntBlock block = PsiTreeUtil.getParentOfType(attr, TerragruntBlock.class);
            return block != null && "locals".equals(TerragruntPsiUtil.getBlockType(block));
        }
        return false;
    }
}
