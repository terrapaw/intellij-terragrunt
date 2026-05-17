package com.github.joelm.terragrunt.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class TerragruntElementType extends IElementType {
    public TerragruntElementType(@NotNull String debugName) {
        super(debugName, TerragruntLanguage.INSTANCE);
    }
}
