package com.github.joelm.terragrunt.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TerragruntFileType extends LanguageFileType {
    public static final TerragruntFileType INSTANCE = new TerragruntFileType();

    private TerragruntFileType() {
        super(TerragruntLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Terragrunt HCL";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Terragrunt HCL configuration file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "hcl";
    }

    @Override
    public Icon getIcon() {
        return TerragruntIcons.FILE;
    }
}
