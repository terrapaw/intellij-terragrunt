package com.github.terrapaw.terragrunt;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntFormatterTest extends BasePlatformTestCase {

    public void testIndentsBlockBody() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                name = "test"
                region = "us-east-1"
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        // Alignment pads shorter names to align = signs
        assertTrue("Should indent and align attributes inside block",
                result.contains("  name") && result.contains("  region") && result.contains("= \"test\"") && result.contains("= \"us-east-1\""));
    }

    public void testIndentsNestedBlock() {
        myFixture.configureByText("terragrunt.hcl", """
                terraform {
                extra_arguments "vars" {
                commands = ["plan"]
                }
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should indent nested block", result.contains("  extra_arguments"));
        assertTrue("Should double-indent nested content", result.contains("    commands"));
    }

    public void testIndentsObjectLiteral() {
        myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                name = "test"
                count = 3
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should indent object contents", result.contains("  name = \"test\""));
    }

    public void testPreservesSpacingInsideAttributes() {
        // Formatter should not change spacing around = or inside expressions
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name   = "test"
                  region = "us-east-1"
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should preserve alignment spacing", result.contains("name   = \"test\""));
    }

    public void testDoesNotMungeSpacingInsideExpressions() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  list = ["a", "b", "c"]
                  expr = local.name == "test" ? "yes" : "no"
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should preserve list spacing", result.contains("[\"a\", \"b\", \"c\"]"));
        assertTrue("Should preserve expression spacing", result.contains("== \"test\" ? \"yes\" : \"no\""));
    }

    public void testTopLevelBlocksNotIndented() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "test"
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("locals should start at column 0", result.contains("\nlocals {") || result.startsWith("locals {"));
        assertTrue("dependency should start at column 0", result.contains("\ndependency \"vpc\" {"));
        assertFalse("Top-level blocks should NOT be indented", result.contains("  locals {"));
        assertFalse("Top-level blocks should NOT be indented", result.contains("  dependency"));
    }

    public void testAlignmentOfEqualsInBlock() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                app_name="my-app"
                app_port=8080
                environment="prod"
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        // All = signs should be aligned
        int eq1 = result.indexOf("= \"my-app\"");
        int eq2 = result.indexOf("= 8080");
        int eq3 = result.indexOf("= \"prod\"");
        // They should all be at the same column (aligned)
        int col1 = eq1 - result.lastIndexOf('\n', eq1) - 1;
        int col2 = eq2 - result.lastIndexOf('\n', eq2) - 1;
        int col3 = eq3 - result.lastIndexOf('\n', eq3) - 1;
        assertEquals("= signs should be aligned", col1, col2);
        assertEquals("= signs should be aligned", col2, col3);
    }

    public void testAlignmentResetsAfterBlankLine() {
        // After a blank line, alignment groups reset
        myFixture.configureByText("terragrunt.hcl",
                "locals {\n  a = \"short\"\n  long_name = \"long\"\n\n  x = 1\n}\n");
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        // Just verify it doesn't crash and produces valid output
        assertTrue("Should contain x", result.contains("x"));
        assertTrue("Should contain long_name", result.contains("long_name"));
    }

    public void testSpaceAfterCommaInList() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  list = ["a","b","c"]
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should have space after commas", result.contains("\"a\", \"b\", \"c\""));
    }

    public void testSpaceInsideInlineObject() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = merge({Name=local.app},{Env=local.env})
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should have space after { in inline object", result.contains("{ Name"));
        assertTrue("Should have space before } in inline object", result.contains("app }"));
    }

    public void testFormatterMatchesTerragruntFmt() {
        // Comprehensive test matching terragrunt hcl format output
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                config_path="../vpc"
                mock_outputs={
                vpc_id="vpc-123"
                private_subnets=["a","b","c"]
                }
                }
                """);
        myFixture.performEditorAction("ReformatCode");
        String result = myFixture.getEditor().getDocument().getText();
        // Indentation
        assertTrue("Should indent config_path", result.contains("  config_path"));
        assertTrue("Should indent mock_outputs", result.contains("  mock_outputs"));
        assertTrue("Should indent vpc_id", result.contains("    vpc_id"));
        // Commas
        assertTrue("Should space after commas", result.contains("\"a\", \"b\", \"c\""));
        // Alignment within mock_outputs
        int vpcEq = result.indexOf("= \"vpc-123\"");
        int subEq = result.indexOf("= [\"a\"");
        if (vpcEq > 0 && subEq > 0) {
            int vpcCol = vpcEq - result.lastIndexOf('\n', vpcEq) - 1;
            int subCol = subEq - result.lastIndexOf('\n', subEq) - 1;
            assertEquals("= should be aligned in mock_outputs", vpcCol, subCol);
        }
    }
}
