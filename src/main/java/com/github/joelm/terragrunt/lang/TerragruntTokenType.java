package com.github.joelm.terragrunt.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class TerragruntTokenType extends IElementType {
    public TerragruntTokenType(@NotNull String debugName) {
        super(debugName, TerragruntLanguage.INSTANCE);
    }
}
