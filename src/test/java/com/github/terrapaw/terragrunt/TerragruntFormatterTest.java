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
        assertTrue("Should indent attributes inside block", result.contains("  name = \"test\""));
        assertTrue("Should indent attributes inside block", result.contains("  region = \"us-east-1\""));
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
}
