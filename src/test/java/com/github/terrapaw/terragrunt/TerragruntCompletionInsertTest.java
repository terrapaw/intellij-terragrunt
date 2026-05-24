package com.github.terrapaw.terragrunt;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;
import java.util.List;

public class TerragruntCompletionInsertTest extends BasePlatformTestCase {

    public void testBlockInsertWithLabel() {
        myFixture.configureByText("terragrunt.hcl", "include<caret>");
        myFixture.completeBasic();
        // If only one match, it auto-inserts
        String text = myFixture.getEditor().getDocument().getText();
        if (text.contains("include \"\" {")) {
            assertTrue("Should insert block with label template", true);
        } else {
            // Multiple matches - select from lookup
            selectAndInsert("include");
            text = myFixture.getEditor().getDocument().getText();
            assertTrue("Should insert block with label template, got: " + text, text.contains("include \"\" {"));
        }
    }

    public void testBlockInsertWithoutLabel() {
        myFixture.configureByText("terragrunt.hcl", "locals<caret>");
        myFixture.completeBasic();
        String text = myFixture.getEditor().getDocument().getText();
        if (text.contains("locals {")) {
            assertFalse("Should NOT have quotes for label", text.contains("locals \""));
        } else {
            selectAndInsert("locals");
            text = myFixture.getEditor().getDocument().getText();
            assertTrue("Should insert block without label, got: " + text, text.contains("locals {"));
        }
    }

    public void testAttributeInsertAddsEquals() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path<caret>
                }
                """);
        myFixture.completeBasic();
        String text = myFixture.getEditor().getDocument().getText();
        if (text.contains("config_path = ")) {
            assertTrue(true);
        } else {
            selectAndInsert("config_path");
            text = myFixture.getEditor().getDocument().getText();
            assertTrue("Should insert 'config_path = ', got: " + text, text.contains("config_path = "));
        }
    }

    public void testFunctionInsertAddsParens() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = find_in_parent_folders<caret>
                }
                """);
        myFixture.completeBasic();
        String text = myFixture.getEditor().getDocument().getText();
        if (text.contains("find_in_parent_folders()")) {
            assertTrue(true);
        } else {
            selectAndInsert("find_in_parent_folders");
            text = myFixture.getEditor().getDocument().getText();
            assertTrue("Should insert function with parens, got: " + text, text.contains("find_in_parent_folders()"));
        }
    }

    public void testTopLevelAttributeInsertAddsEquals() {
        myFixture.configureByText("terragrunt.hcl", "inputs<caret>");
        myFixture.completeBasic();
        String text = myFixture.getEditor().getDocument().getText();
        if (text.contains("inputs = ")) {
            assertTrue(true);
        } else {
            selectAndInsert("inputs");
            text = myFixture.getEditor().getDocument().getText();
            assertTrue("Should insert 'inputs = ', got: " + text, text.contains("inputs = "));
        }
    }

    private void selectAndInsert(String name) {
        var lookup = myFixture.getLookup();
        if (lookup == null) return;
        List<LookupElement> items = lookup.getItems();
        for (LookupElement item : items) {
            if (item.getLookupString().equals(name)) {
                lookup.setCurrentItem(item);
                myFixture.finishLookup('\n');
                return;
            }
        }
    }
}
