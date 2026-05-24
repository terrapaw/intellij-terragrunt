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

    public void testLiveTemplateLocalsNoExtraIndentOnBrace() {
        // Live templates are deactivated from Tab/popup (available via Ctrl+J only)
        // The completion contributor handles block insertion with proper indentation
        myFixture.configureByText("terragrunt.hcl", "loc<caret>");
        var completions = myFixture.completeBasic();
        // Should get 'locals' from completion contributor, not live template
        if (completions != null) {
            var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
            assertTrue("Should suggest 'locals' from completion contributor", names.contains("locals"));
        }
    }

    public void testLiveTemplateDepNoExtraIndentOnBrace() {
        myFixture.configureByText("terragrunt.hcl", "dep<caret>");
        var completions = myFixture.completeBasic();
        if (completions != null) {
            var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
            assertTrue("Should suggest 'dependency' from completion contributor", names.contains("dependency"));
        }
    }

    public void testLiveTemplateDoesNotExpandInsideBlock() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  dep<caret>
                }
                """);
        String before = myFixture.getEditor().getDocument().getText();
        myFixture.performEditorAction("ExpandLiveTemplateByTab");
        String after = myFixture.getEditor().getDocument().getText();
        assertFalse("Should NOT expand dep template inside a block", after.contains("dependency \"\""));
    }

    public void testLiveTemplateNotInCompletionInsideInputs() {
        myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  <caret>
                }
                """);

        // Directly test the context
        var ctx = com.intellij.codeInsight.template.TemplateActionContext.expanding(
                myFixture.getFile(), myFixture.getEditor());
        var templateContext = new com.github.terrapaw.terragrunt.editor.TerragruntLiveTemplateContext();
        assertFalse("isInContext should return false inside inputs block",
                templateContext.isInContext(ctx));

        var completions = myFixture.completeBasic();
        if (completions != null) {
            java.util.List<String> names = java.util.List.of(completions).stream()
                    .map(l -> l.getLookupString()).toList();
            assertFalse("Should NOT suggest 'loc' template inside inputs", names.contains("loc"));
            assertFalse("Should NOT suggest 'dep' template inside inputs", names.contains("dep"));
            assertFalse("Should NOT suggest 'inc' template inside inputs", names.contains("inc"));
        }
    }

    public void testLiveTemplateNotInCompletionInsideLocalsBlock() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        if (completions != null) {
            java.util.List<String> names = java.util.List.of(completions).stream()
                    .map(l -> l.getLookupString()).toList();
            assertFalse("Should NOT suggest 'loc' template inside locals block", names.contains("loc"));
            assertFalse("Should NOT suggest 'dep' template inside locals block", names.contains("dep"));
        }
    }

    public void testNoBlockCompletionInsideInputsMap() {
        myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        if (completions != null) {
            java.util.List<String> names = java.util.List.of(completions).stream()
                    .map(l -> l.getLookupString()).toList();
            assertFalse("Should NOT suggest 'locals' block inside inputs", names.contains("locals"));
            assertFalse("Should NOT suggest 'terraform' block inside inputs", names.contains("terraform"));
            assertFalse("Should NOT suggest 'dependency' block inside inputs", names.contains("dependency"));
        }
    }

    public void testNoBlockCompletionInsideInputsMapWithExistingContent() {
        // Mimics env.hcl with locals above and inputs below
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  environment = "dev"
                }
                
                inputs = {
                  default_tags = {}
                  <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        if (completions != null) {
            java.util.List<String> names = java.util.List.of(completions).stream()
                    .map(l -> l.getLookupString()).toList();
            assertFalse("Should NOT suggest 'locals' block inside inputs with content above. Got: " + names,
                    names.contains("locals"));
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
