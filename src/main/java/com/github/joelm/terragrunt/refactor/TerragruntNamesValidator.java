package com.github.joelm.terragrunt.refactor;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class TerragruntNamesValidator implements NamesValidator {
    private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "for", "in", "if", "else", "endif", "endfor");

    @Override
    public boolean isKeyword(@NotNull String name, Project project) {
        return KEYWORDS.contains(name);
    }

    @Override
    public boolean isIdentifier(@NotNull String name, Project project) {
        if (name.isEmpty()) return false;
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') return false;
        }
        return !isKeyword(name, project);
    }
}
