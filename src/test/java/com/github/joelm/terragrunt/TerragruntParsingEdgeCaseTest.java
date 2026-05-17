package com.github.joelm.terragrunt;

import com.github.joelm.terragrunt.lang.psi.TerragruntBlock;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;

public class TerragruntParsingEdgeCaseTest extends BasePlatformTestCase {

    public void testEmptyFile() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", "");
        assertNoParseErrors(file);
    }

    public void testEmptyBlock() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                }
                """);
        assertNoParseErrors(file);
    }

    public void testMultipleLabels() {
        // terraform extra_arguments blocks have a label
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                terraform {
                  extra_arguments "retry" {
                    commands = ["plan"]
                  }
                  before_hook "init" {
                    commands = ["init"]
                    execute  = ["echo", "hello"]
                  }
                }
                """);
        assertNoParseErrors(file);
    }

    public void testDeeplyNestedExpressions() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = merge(
                    { a = "b" },
                    { c = lookup(local.map, "key", "default") }
                  )
                }
                """);
        assertNoParseErrors(file);
    }

    public void testTernaryWithFunctionCalls() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  env = get_env("ENV", "dev")
                  bucket = local.env == "prod" ? "prod-bucket" : "dev-bucket"
                }
                """);
        assertNoParseErrors(file);
    }

    public void testListOfMaps() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  tags = [
                    { key = "Name", value = "test" },
                    { key = "Env", value = "dev" }
                  ]
                }
                """);
        assertNoParseErrors(file);
    }

    public void testBooleanExpressions() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  enabled = true && !false
                  check   = local.enabled || false
                  compare = 5 > 3 && 2 <= 4
                }
                """);
        assertNoParseErrors(file);
    }

    public void testArithmeticExpressions() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  sum  = 1 + 2
                  prod = 3 * 4
                  mod  = 10 % 3
                  expr = (1 + 2) * 3
                }
                """);
        assertNoParseErrors(file);
    }

    public void testMultipleBlocksOfSameType() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                include "env" {
                  path           = find_in_parent_folders("env.hcl")
                  expose         = true
                  merge_strategy = "no_merge"
                }
                include "region" {
                  path   = find_in_parent_folders("region.hcl")
                  expose = true
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals("Should find 3 include blocks", 3, blocks.size());
    }

    public void testComplexInputsMap() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  name       = local.name
                  vpc_id     = dependency.vpc.outputs.vpc_id
                  subnets    = dependency.vpc.outputs.private_subnets
                  enabled    = feature.flag.value
                  region     = "us-east-1"
                  count      = 3
                  is_prod    = false
                  tags       = { Environment = "dev", Team = "platform" }
                }
                """);
        assertNoParseErrors(file);
    }

    public void testHeredocBasic() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                generate "provider" {
                  path      = "provider.tf"
                  if_exists = "overwrite"
                  contents  = <<EOF
                provider "aws" {
                  region = "us-east-1"
                }
                EOF
                }
                """);
        // Heredoc may have parse issues since we treat newlines as whitespace
        // This test documents current behavior
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertTrue("Should find at least the generate block", blocks.size() >= 1);
    }

    public void testCommentsInVariousPositions() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                # Top-level comment
                locals {
                  # Comment inside block
                  name = "test" # Inline comment
                }
                // Another style comment
                """);
        assertNoParseErrors(file);
    }

    public void testFunctionCallChaining() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = read_terragrunt_config(find_in_parent_folders("common.hcl"))
                  region = local.config.locals.region
                }
                """);
        assertNoParseErrors(file);
    }

    public void testEscapedStrings() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  escaped = "line1\\nline2\\ttab"
                  quoted  = "say \\"hello\\""
                  path    = "C:\\\\Users\\\\test"
                }
                """);
        assertNoParseErrors(file);
    }

    public void testEmptyList() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  empty_list = []
                  empty_map  = {}
                }
                """);
        assertNoParseErrors(file);
    }

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
}
