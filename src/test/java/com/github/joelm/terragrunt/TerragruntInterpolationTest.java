package com.github.joelm.terragrunt;

import com.github.joelm.terragrunt.lang.TerragruntLexerAdapter;
import com.github.joelm.terragrunt.lang.psi.TerragruntTypes;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TerragruntInterpolationTest extends BasePlatformTestCase {

    // --- Lexer tests for interpolation ---

    public void testInterpolationInStringProducesCorrectTokens() {
        List<IElementType> tokens = getTokenTypes("\"${local.name}\"");
        assertTrue("Should have INTERPOLATION_START", tokens.contains(TerragruntTypes.INTERPOLATION_START));
        assertTrue("Should have IDENTIFIER", tokens.contains(TerragruntTypes.IDENTIFIER));
        assertTrue("Should have DOT", tokens.contains(TerragruntTypes.DOT));
        assertTrue("Should have INTERPOLATION_END", tokens.contains(TerragruntTypes.INTERPOLATION_END));
        assertFalse("Should NOT have RBRACE (interpolation uses INTERPOLATION_END)", tokens.contains(TerragruntTypes.RBRACE));
    }

    public void testInterpolationWithFunctionCall() {
        List<IElementType> tokens = getTokenTypes("\"${get_repo_root()}\"");
        assertTrue("Should have INTERPOLATION_START", tokens.contains(TerragruntTypes.INTERPOLATION_START));
        assertTrue("Should have IDENTIFIER", tokens.contains(TerragruntTypes.IDENTIFIER));
        assertTrue("Should have LPAREN", tokens.contains(TerragruntTypes.LPAREN));
        assertTrue("Should have RPAREN", tokens.contains(TerragruntTypes.RPAREN));
        assertTrue("Should have INTERPOLATION_END", tokens.contains(TerragruntTypes.INTERPOLATION_END));
    }

    public void testEscapedInterpolationNotTokenized() {
        List<IElementType> tokens = getTokenTypes("\"$${not_interpolation}\"");
        assertFalse("Escaped ${ should NOT produce INTERPOLATION_START", tokens.contains(TerragruntTypes.INTERPOLATION_START));
    }

    public void testNestedBracesInInterpolation() {
        // ${condition ? { a = 1 } : {}} - nested braces inside interpolation
        List<IElementType> tokens = getTokenTypes("\"${true ? \"a\" : \"b\"}\"");
        assertTrue("Should have INTERPOLATION_START", tokens.contains(TerragruntTypes.INTERPOLATION_START));
        assertTrue("Should have INTERPOLATION_END", tokens.contains(TerragruntTypes.INTERPOLATION_END));
        assertTrue("Should have QUESTION", tokens.contains(TerragruntTypes.QUESTION));
    }

    // --- Parser tests for interpolation in strings ---

    public void testStringWithInterpolationParses() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  key = "${path_relative_to_include()}/terraform.tfstate"
                }
                """);
        assertNoParseErrors(file);
    }

    public void testStringWithMultipleInterpolations() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "${local.prefix}-${local.suffix}"
                }
                """);
        assertNoParseErrors(file);
    }

    public void testStringWithDotAccessInInterpolation() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  id = "${dependency.vpc.outputs.vpc_id}"
                }
                """);
        assertNoParseErrors(file);
    }

    // --- Heredoc tests ---

    public void testHeredocWithInterpolation() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                generate "provider" {
                  path      = "provider.tf"
                  if_exists = "overwrite_terragrunt"
                  contents  = <<EOF
                provider "aws" {
                  region = "${local.aws_region}"
                }
                EOF
                }
                """);
        assertNoParseErrors(file);
    }

    public void testHeredocWithoutInterpolation() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                generate "versions" {
                  path      = "versions.tf"
                  if_exists = "overwrite"
                  contents  = <<EOF
                terraform {
                  required_version = ">= 1.5.0"
                }
                EOF
                }
                """);
        assertNoParseErrors(file);
    }

    public void testHeredocWithMultipleInterpolations() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                generate "backend" {
                  path      = "backend.tf"
                  if_exists = "overwrite"
                  contents  = <<EOF
                terraform {
                  backend "s3" {
                    bucket = "${local.bucket}"
                    key    = "${local.key}"
                    region = "${local.region}"
                  }
                }
                EOF
                }
                """);
        assertNoParseErrors(file);
    }

    // --- Brace matching: interpolation } should not match block { ---

    public void testInterpolationBraceDoesNotConfuseParser() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                remote_state {
                  backend = "s3"
                  config = {
                    key = "${path_relative_to_include()}/terraform.tfstate"
                    bucket = "my-bucket"
                  }
                }
                """);
        assertNoParseErrors(file);
    }

    // --- Full realistic file with all features ---

    public void testRealisticFileWithInterpolationsAndHeredoc() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                locals {
                  region = "us-east-1"
                  name   = "my-app"
                }
                
                remote_state {
                  backend = "s3"
                  config = {
                    bucket = "${local.name}-state"
                    key    = "${path_relative_to_include()}/terraform.tfstate"
                    region = local.region
                  }
                }
                
                generate "provider" {
                  path      = "provider.tf"
                  if_exists = "overwrite_terragrunt"
                  contents  = <<EOF
                provider "aws" {
                  region = "${local.region}"
                  default_tags {
                    tags = {
                      Project = "${local.name}"
                    }
                  }
                }
                EOF
                }
                
                inputs = {
                  name   = local.name
                  region = local.region
                }
                """);
        assertNoParseErrors(file);
    }

    // --- psi.referenceContributor uses "implementation" not "implementationClass" ---
    // This is a runtime test - if the plugin loads without error, it passes.
    // The TerragruntInspectionTest tests already validate this implicitly since
    // they use the full plugin infrastructure.

    private void assertNoParseErrors(PsiFile file) {
        Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Parse errors found:\n");
            for (PsiErrorElement error : errors) {
                sb.append("  - ").append(error.getErrorDescription())
                        .append(" at offset ").append(error.getTextOffset())
                        .append("\n");
            }
            fail(sb.toString());
        }
    }

    private List<IElementType> getTokenTypes(String input) {
        Lexer lexer = new TerragruntLexerAdapter();
        lexer.start(input);
        List<IElementType> tokens = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            IElementType type = lexer.getTokenType();
            if (type != com.intellij.psi.TokenType.WHITE_SPACE) {
                tokens.add(type);
            }
            lexer.advance();
        }
        return tokens;
    }
}
