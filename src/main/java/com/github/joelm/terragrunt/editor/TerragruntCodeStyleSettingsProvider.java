package com.github.joelm.terragrunt.editor;

import com.github.joelm.terragrunt.lang.TerragruntLanguage;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

public class TerragruntCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    @NotNull
    @Override
    public Language getLanguage() {
        return TerragruntLanguage.INSTANCE;
    }

    @Override
    protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings, @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
        indentOptions.INDENT_SIZE = 2;
        indentOptions.TAB_SIZE = 2;
        indentOptions.USE_TAB_CHARACTER = false;
        indentOptions.CONTINUATION_INDENT_SIZE = 2;
    }

    @Override
    public String getCodeSample(@NotNull SettingsType settingsType) {
        return """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                locals {
                  region = "us-east-1"
                  name   = "my-app"
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                  mock_outputs = {
                    vpc_id = "vpc-123"
                  }
                }
                """;
    }
}
