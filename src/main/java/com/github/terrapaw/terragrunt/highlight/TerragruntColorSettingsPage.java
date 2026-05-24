package com.github.terrapaw.terragrunt.highlight;

import com.github.terrapaw.terragrunt.lang.TerragruntIcons;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class TerragruntColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Keyword", TerragruntSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("String", TerragruntSyntaxHighlighter.STRING),
            new AttributesDescriptor("Number", TerragruntSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Line comment", TerragruntSyntaxHighlighter.LINE_COMMENT),
            new AttributesDescriptor("Block comment", TerragruntSyntaxHighlighter.BLOCK_COMMENT),
            new AttributesDescriptor("Identifier", TerragruntSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Braces", TerragruntSyntaxHighlighter.BRACES),
            new AttributesDescriptor("Brackets", TerragruntSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Parentheses", TerragruntSyntaxHighlighter.PARENTHESES),
            new AttributesDescriptor("Operator", TerragruntSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Interpolation", TerragruntSyntaxHighlighter.INTERPOLATION),
    };

    @Override
    public @Nullable Icon getIcon() {
        return TerragruntIcons.FILE;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new TerragruntSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return """
                # Terragrunt configuration
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                locals {
                  region = "us-east-1"
                  count  = 3
                  enabled = true
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                inputs = {
                  name   = local.region
                  vpc_id = dependency.vpc.outputs.vpc_id
                  check  = local.count > 0 ? "yes" : "no"
                }
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @NotNull
    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Terragrunt HCL";
    }
}
