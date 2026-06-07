package com.github.terrapaw.terragrunt.inspection;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class ReplaceIdentifierQuickFix implements LocalQuickFix {

    private final String replacement;

    public ReplaceIdentifierQuickFix(@NotNull String replacement) {
        this.replacement = replacement;
    }

    @Override
    public @NotNull String getName() {
        return "Change to '" + replacement + "'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Change to suggested name";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null || !element.isValid()) return;

        // Create a dummy file to get a new identifier PSI element
        var factory = PsiFileFactory.getInstance(project);
        var dummyFile = factory.createFileFromText(
                "dummy.hcl", element.getContainingFile().getFileType(),
                replacement + " {}");
        TerragruntBlock block = PsiTreeUtil.findChildOfType(dummyFile, TerragruntBlock.class);
        if (block != null) {
            element.replace(block.getIdentifier());
        }
    }

    public static String findClosest(String input, Iterable<String> candidates) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int dist = levenshtein(input, candidate);
            if (dist < bestDist && dist <= Math.max(input.length(), candidate.length()) / 2) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}
