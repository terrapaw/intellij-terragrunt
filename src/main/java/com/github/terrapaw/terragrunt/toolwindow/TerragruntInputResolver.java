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

    public record InputEntry(String key, String value, String resolved, String source) {}

    @NotNull
    public static List<InputEntry> resolveInputs(@NotNull PsiFile file) {
        Map<String, InputEntry> merged = new LinkedHashMap<>();

        // First: collect inputs from included files (they get overridden by the current file)
        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"include".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            PsiFile included = TerragruntFileResolver.resolveInclude(block);
            if (included == null) continue;
            String includeName = getBlockLabel(block);
            collectInputs(included, includeName != null ? "include \"" + includeName + "\"" : "include", merged, file);
        }

        // Then: collect inputs from the current file (overrides included ones)
        collectInputs(file, "current file", merged, file);

        return new ArrayList<>(merged.values());
    }

    private static void collectInputs(PsiFile file, String source, Map<String, InputEntry> merged, PsiFile rootFile) {
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
                String value = getExpressionText(elem);
                String resolved = resolveValue(elem, locals, file, rootFile);
                merged.put(key, new InputEntry(key, value, resolved, source));
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

    private static String getExpressionText(TerragruntObjectElem elem) {
        var exprs = elem.getExpressionList();
        TerragruntExpression valueExpr = exprs.size() >= 2 ? exprs.get(1) : (exprs.size() == 1 ? exprs.get(0) : null);
        return valueExpr != null ? valueExpr.getText().trim() : "?";
    }

    private static String resolveValue(TerragruntObjectElem elem, Map<String, String> locals, PsiFile file, PsiFile rootFile) {
        var exprs = elem.getExpressionList();
        TerragruntExpression valueExpr = exprs.size() >= 2 ? exprs.get(1) : (exprs.size() == 1 ? exprs.get(0) : null);
        if (valueExpr == null) return "?";
        return deepResolve(valueExpr.getText().trim(), file, rootFile, 10);
    }

    /**
     * Recursively resolves an expression until it reaches a terminal value (string, number, object, list)
     * or hits the depth limit.
     */
    private static String deepResolve(String text, PsiFile contextFile, PsiFile rootFile, int maxDepth) {
        if (maxDepth <= 0) return text;

        // Already a terminal value
        if (isTerminal(text)) return simplifyText(text);

        // local.X — resolve from locals in contextFile
        if (text.startsWith("local.")) {
            String[] parts = text.substring("local.".length()).split("\\.");
            String localName = parts[0];

            // First try: direct local value
            TerragruntAttribute localAttr = TerragruntFileResolver.findLocalAttribute(contextFile, localName);
            if (localAttr != null && localAttr.getExpression() != null) {
                if (parts.length == 1) {
                    // local.X → resolve its value
                    return deepResolve(localAttr.getExpression().getText().trim(), contextFile, rootFile, maxDepth - 1);
                }
                // local.alias.something — alias points to a file
                PsiFile resolved = TerragruntChainResolver.resolveLocalAlias(contextFile, localName);
                if (resolved != null) {
                    if (parts.length >= 3 && "locals".equals(parts[1])) {
                        // local.X.locals.Y
                        TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(resolved, parts[2]);
                        if (attr != null && attr.getExpression() != null) {
                            String remaining = parts.length > 3 ? joinParts(parts, 3) : null;
                            String val = deepResolve(attr.getExpression().getText().trim(), resolved, rootFile, maxDepth - 1);
                            if (remaining != null) return val; // Can't drill further into object text
                            return val;
                        }
                    } else if (parts.length == 2) {
                        // local.alias.Y — look for Y in resolved file's locals
                        TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(resolved, parts[1]);
                        if (attr != null && attr.getExpression() != null) {
                            return deepResolve(attr.getExpression().getText().trim(), resolved, rootFile, maxDepth - 1);
                        }
                    }
                }
            }
        }

        // include.X.locals.Y — resolve from rootFile's includes
        if (text.startsWith("include.")) {
            String resolved = resolveIncludeExpression(text, rootFile);
            if (resolved != null && !resolved.equals(text)) {
                return resolved;
            }
        }

        return simplifyText(text);
    }

    private static boolean isTerminal(String text) {
        if (text.startsWith("\"") && text.endsWith("\"")) return true;
        if (text.startsWith("{") || text.startsWith("[")) return true;
        if (text.equals("true") || text.equals("false") || text.equals("null")) return true;
        try { Double.parseDouble(text); return true; } catch (NumberFormatException ignored) {}
        return false;
    }

    private static String simplifyText(String text) {
        if (text.startsWith("\"") && text.endsWith("\"") && !text.contains("${")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static String joinParts(String[] parts, int from) {
        var sb = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(".");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String resolveIncludeExpression(String text, PsiFile file) {
        // include.X.locals.Y or include.X.inputs.Y
        String[] parts = text.split("\\.");
        if (parts.length < 4) return null;
        String includeName = parts[1];
        String section = parts[2]; // locals or inputs
        String attrName = parts[3];

        for (TerragruntBlock block : PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class)) {
            if (!"include".equals(TerragruntPsiUtil.getBlockType(block))) continue;
            var labels = block.getLabelList();
            if (labels.isEmpty() || !includeName.equals(TerragruntPsiUtil.getLabelText(labels.get(0)))) continue;
            PsiFile included = TerragruntFileResolver.resolveInclude(block);
            if (included == null) return null;
            if ("locals".equals(section)) {
                TerragruntAttribute attr = TerragruntFileResolver.findLocalAttribute(included, attrName);
                if (attr != null && attr.getExpression() != null) {
                    return deepResolve(attr.getExpression().getText().trim(), included, file, 8);
                }
            }
        }
        return null;
    }

    private static String simplifyExpression(TerragruntExpression expr) {
        return simplifyText(expr.getText().trim());
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
