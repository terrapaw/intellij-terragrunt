package com.github.joelm.terragrunt.lang;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class TerragruntFile extends PsiFileBase {
    public TerragruntFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, TerragruntLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return TerragruntFileType.INSTANCE;
    }
}
