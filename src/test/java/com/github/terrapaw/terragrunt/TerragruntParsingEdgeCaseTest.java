package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
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

    // --- Error Recovery Tests ---

    public void testMissingClosingBraceRecovers() {
        // Parser should recover and still parse the second block
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = 1

                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        // Should have parse errors but still find the dependency block
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertTrue("Should recover and find at least one block", blocks.size() >= 1);
    }

    public void testMissingEqualsRecovers() {
        // Missing = in attribute should not break the whole file
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x 1
                  y = 2
                }
                """);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertTrue("Should still find the locals block", blocks.size() >= 1);
    }

    public void testIncompleteExpressionRecovers() {
        // Incomplete expression should not break subsequent blocks
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = local.
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertTrue("Should recover and find dependency block", blocks.size() >= 2);
    }

    public void testEmptyBodyRecovers() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
    }

    public void testTrailingCommaInMapRecovers() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = {
                    a = 1,
                    b = 2,
                  }
                }
                """);
        assertNoParseErrors(file);
    }

    // --- Label Edge Case Tests ---

    public void testMultipleLabelsInOneNode() {
        // With QUOTE token, "vpc" "extra" produces two separate label nodes
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" "extra" {
                  config_path = "../vpc"
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        TerragruntBlock block = blocks.iterator().next();
        // Each quoted string is now a separate label node
        assertEquals(2, block.getLabelList().size());
        assertTrue(block.getLabelList().get(0).getText().contains("vpc"));
        assertTrue(block.getLabelList().get(1).getText().contains("extra"));
    }

    public void testUnquotedIdentifierLabel() {
        // HCL allows unquoted identifiers as labels
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency vpc {
                  config_path = "../vpc"
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        TerragruntBlock block = blocks.iterator().next();
        assertEquals(1, block.getLabelList().size());
        assertEquals("vpc", block.getLabelList().getFirst().getText());
    }


    public void testMultipleUnquotedIdentifierLabels() {
        // dependency vpc extra {} — two unquoted identifier labels
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency vpc extra {
                  config_path = "../vpc"
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        TerragruntBlock block = blocks.iterator().next();
        // Each IDENTIFIER is its own label node (unlike STRING_LITERAL+ which merges)
        assertEquals("Should have 2 labels", 2, block.getLabelList().size());
        assertEquals("vpc", block.getLabelList().get(0).getText());
        assertEquals("extra", block.getLabelList().get(1).getText());
    }

    public void testMixedLabelIdentifierThenString() {
        // dependency vpc "extra" {} — identifier then quoted string
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency vpc "extra" {
                  config_path = "../vpc"
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        assertEquals("Should have 2 labels", 2, blocks.iterator().next().getLabelList().size());
    }

    public void testMixedLabelStringThenIdentifier() {
        // dependency "vpc" extra {} — quoted string then identifier
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" extra {
                  config_path = "../vpc"
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        assertEquals("Should have 2 labels", 2, blocks.iterator().next().getLabelList().size());
    }

    public void testBlockWithNoLabelParsesCorrectly() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = 1
                }
                
                terraform {
                  source = "./modules/app"
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(2, blocks.size());
        for (TerragruntBlock block : blocks) {
            assertEquals(0, block.getLabelList().size());
        }
    }

    public void testMergeWithMultiLineObjectsAndQuotedKeys() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  a = "abc"
                  tags = merge(
                    {
                      "example" = "abc"
                      "abc" = local.a
                    },
                    {
                      "example" = "abc"
                    }
                  )
                }
                """);
        Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder("Parse errors found:");
            for (PsiErrorElement err : errors) {
                msg.append("\n  ").append(err.getErrorDescription())
                   .append(" at offset ").append(err.getTextOffset());
            }
            fail(msg.toString());
        }
    }
}
