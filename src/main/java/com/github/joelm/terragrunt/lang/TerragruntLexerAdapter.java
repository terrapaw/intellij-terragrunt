package com.github.joelm.terragrunt.lang;

import com.intellij.lexer.FlexAdapter;

public class TerragruntLexerAdapter extends FlexAdapter {
    public TerragruntLexerAdapter() {
        super(new TerragruntLexer(null));
    }
}
