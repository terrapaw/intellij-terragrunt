package com.github.terrapaw.terragrunt.reference;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves chains of local aliases through include and read_terragrunt_config references.
 * Shared between the goto handler and completion contributor.
 */
public final class TerragruntChainResolver {

    private TerragruntChainResolver() {}

    /**
     * Resolves a local variable alias to the file it references.
     * Handles: include.X.locals, include.X.inputs, read_terragrunt_config(...)
     */
    @Nullable
    public static PsiFile resolveLocalAlias(PsiFile file, String aliasName) {
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(block.getIdentifier().getText())) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (!aliasName.equals(attr.getIdentifier().getText())) continue;

                // Pattern 1: include.X.locals or include.X.inputs
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

                        // Pattern 2: read_terragrunt_config(...) inside primary
                        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(primary, TerragruntFunctionCall.class);
                        if (funcCall != null && "read_terragrunt_config".equals(funcCall.getIdentifier().getText())) {
                            return TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
                        }
                    }
                }

                // Pattern 2 fallback: read_terragrunt_config at expression level
                TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
                if (funcCall != null && "read_terragrunt_config".equals(funcCall.getIdentifier().getText())) {
                    return TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
                }
            }
        }
        return null;
    }

    /**
     * Checks if an alias points to include.X.locals (as opposed to read_terragrunt_config).
     */
    public static boolean isIncludeAlias(PsiFile file, String aliasName, String section) {
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

    /**
     * Walks a chain of get_attrs resolving aliases at each step.
     * Returns the final file that the chain resolves to.
     */
    @Nullable
    public static PsiFile resolveChain(PsiFile startFile, PsiElement[] getAttrs, int startIndex, int endIndex, PsiFile originFile) {
        PsiFile file = startFile;
        int i = startIndex;

        while (i < endIndex && file != null) {
            String name = ((TerragruntGetAttr) getAttrs[i]).getIdentifier().getText();
            if (i + 1 < getAttrs.length) {
                String next = ((TerragruntGetAttr) getAttrs[i + 1]).getIdentifier().getText();
                if ("locals".equals(next) || "inputs".equals(next)) {
                    file = resolveLocalAlias(file, name);
                    i += 2;
                    continue;
                }
            }
            i++;
        }
        return file;
    }
}
