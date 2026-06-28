package com.github.terrapaw.terragrunt.toolwindow;

import com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil;
import com.github.terrapaw.terragrunt.lang.psi.*;
import com.github.terrapaw.terragrunt.reference.TerragruntChainResolver;
import com.github.terrapaw.terragrunt.reference.TerragruntFileResolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Resolves the final computed inputs for a Terragrunt file by merging
 * inputs from includes and the file itself, resolving local.X where possible.
 */
public class TerragruntInputResolver {

    public record InputEntry(String key, String value, String source) {}

    @NotNull
    public static List<InputEntry> resolveInputs(@NotNull PsiFile file) {
        Map<String, InputEntry> merged = new LinkedHashMap<>();

        // First: collect inputs from included files (they get overridden by the current file)
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"include".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            PsiFile included = TerragruntFileResolver.resolveInclude(block);
            if (included == null) continue;
            String includeName = getBlockLabel(block);
            collectInputs(included, includeName != null ? "include \"" + includeName + "\"" : "include", merged);
        }

        // Then: collect inputs from the current file (overrides included ones)
        collectInputs(file, "current file", merged);

        return new ArrayList<>(merged.values());
    }

    private static void collectInputs(PsiFile file, String source, Map<String, InputEntry> merged) {
        // Collect locals for resolution
        Map<String, String> locals = collectLocals(file);

        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (block.getIdentifier() == null || !"inputs".equals(block.getIdentifier().getText())) continue;
            // inputs is actually a top-level attribute, not a block — skip
        }

        // inputs is a top-level attribute: inputs = { ... }
        for (TerragruntAttribute attr : getTopLevelAttributes(file)) {
            if (!"inputs".equals(attr.getIdentifier().getText())) continue;
            TerragruntObjectExpr obj = PsiTreeUtil.findChildOfType(attr, TerragruntObjectExpr.class);
            if (obj == null) continue;
            for (TerragruntObjectElem elem : PsiTreeUtil.getChildrenOfTypeAsList(obj, TerragruntObjectElem.class)) {
                String key = getElemKey(elem);
                if (key == null) continue;
                String value = resolveValue(elem, locals, file);
                merged.put(key, new InputEntry(key, value, source));
            }
        }
    }

    private static Map<String, String> collectLocals(PsiFile file) {
        Map<String, String> locals = new LinkedHashMap<>();
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"locals".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            TerragruntBody body = block.getBody();
            if (body == null) continue;
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                String name = attr.getIdentifier().getText();
                TerragruntExpression expr = attr.getExpression();
                if (expr != null) {
                    locals.put(name, simplifyExpression(expr));
                }
            }
        }
        return locals;
    }

    private static String resolveValue(TerragruntObjectElem elem, Map<String, String> locals, PsiFile file) {
        // Get the value expression (second expression in elem, or after =)
        var exprs = elem.getExpressionList();
        TerragruntExpression valueExpr = exprs.size() >= 2 ? exprs.get(1) : (exprs.size() == 1 ? exprs.get(0) : null);
        if (valueExpr == null) return "?";

        String text = valueExpr.getText().trim();

        // Try to resolve local.X references
        if (text.startsWith("local.")) {
            String localName = text.substring("local.".length()).split("\\.")[0];
            if (locals.containsKey(localName)) {
                if (text.equals("local." + localName)) {
                    return locals.get(localName);
                }
            }
        }

        // Try to resolve simple dependency references (show as-is for now)
        return simplifyExpression(valueExpr);
    }

    private static String simplifyExpression(TerragruntExpression expr) {
        String text = expr.getText().trim();
        // Strip quotes from simple strings
        if (text.startsWith("\"") && text.endsWith("\"") && !text.contains("${")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static List<TerragruntAttribute> getTopLevelAttributes(PsiFile file) {
        List<TerragruntAttribute> attrs = new ArrayList<>();
        for (PsiElement child : file.getChildren()) {
            if (child instanceof TerragruntAttribute attr) {
                attrs.add(attr);
            }
            // Also check inside the file's body if it has one
            if (child instanceof TerragruntBody body) {
                attrs.addAll(PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class));
            }
        }
        // Also try from the file-level body
        TerragruntBody body = PsiTreeUtil.findChildOfType(file, TerragruntBody.class);
        if (body != null) {
            for (TerragruntAttribute attr : PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class)) {
                if (!attrs.contains(attr)) attrs.add(attr);
            }
        }
        return attrs;
    }

    private static String getElemKey(TerragruntObjectElem elem) {
        PsiElement id = elem.getIdentifier();
        if (id != null) return id.getText();
        // Quoted key
        var exprs = elem.getExpressionList();
        if (exprs.size() >= 2) {
            String keyText = exprs.get(0).getText();
            if (keyText.startsWith("\"") && keyText.endsWith("\"")) {
                return keyText.substring(1, keyText.length() - 1);
            }
        }
        return null;
    }

    private static String getBlockLabel(TerragruntBlock block) {
        var labels = block.getLabelList();
        if (labels.isEmpty()) return null;
        return TerragruntPsiUtil.getLabelText(labels.get(0));
    }
}
