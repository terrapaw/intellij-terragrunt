package com.github.joelm.terragrunt.lang;

import com.intellij.lang.Language;

public class TerragruntLanguage extends Language {
    public static final TerragruntLanguage INSTANCE = new TerragruntLanguage();

    private TerragruntLanguage() {
        super("Terragrunt");
    }
}
