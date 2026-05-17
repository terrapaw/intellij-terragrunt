package com.github.joelm.terragrunt;

import com.github.joelm.terragrunt.lang.psi.TerragruntAttribute;
import com.github.joelm.terragrunt.lang.psi.TerragruntBlock;
import com.github.joelm.terragrunt.lang.psi.TerragruntBody;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;

public class TerragruntParsingTest extends BasePlatformTestCase {

    public void testSimpleBlock() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "test"
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        assertEquals("locals", blocks.iterator().next().getIdentifier().getText());
    }

    public void testBlockWithLabel() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        TerragruntBlock block = blocks.iterator().next();
        assertEquals("include", block.getIdentifier().getText());
        assertEquals(1, block.getLabelList().size());
    }

    public void testMultipleBlocks() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path   = find_in_parent_folders("root.hcl")
                  expose = true
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                  mock_outputs = {
                    vpc_id = "vpc-123"
                  }
                }
                
                locals {
                  name = "test"
                }
                
                inputs = {
                  name = local.name
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertTrue("Should find at least 3 top-level blocks", blocks.size() >= 3);
    }

    public void testFunctionCallInExpression() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  account = get_aws_account_id()
                  config  = read_terragrunt_config(find_in_parent_folders("common.hcl"))
                  env     = get_env("ENV", "dev")
                }
                """);
        assertNoParseErrors(file);
    }

    public void testTernaryExpression() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  value = true ? "yes" : "no"
                }
                """);
        assertNoParseErrors(file);
    }

    public void testListAndMapLiterals() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  list = ["a", "b", "c"]
                  map = {
                    key1 = "val1"
                    key2 = "val2"
                  }
                }
                """);
        assertNoParseErrors(file);
    }

    public void testDotAccess() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  vpc_id = dependency.vpc.outputs.vpc_id
                  name   = local.name
                }
                """);
        assertNoParseErrors(file);
    }

    public void testStringInterpolation() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  key = "${path_relative_to_include()}/terraform.tfstate"
                }
                """);
        assertNoParseErrors(file);
    }

    public void testNestedBlocks() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                terraform {
                  source = "git::git@github.com:example/modules.git//app"
                
                  extra_arguments "vars" {
                    commands = ["plan", "apply"]
                    arguments = ["-var-file=terraform.tfvars"]
                  }
                }
                """);
        assertNoParseErrors(file);
    }

    public void testFeatureBlock() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                feature "multi_az" {
                  default = false
                }
                
                inputs = {
                  multi_az = feature.multi_az.value
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
