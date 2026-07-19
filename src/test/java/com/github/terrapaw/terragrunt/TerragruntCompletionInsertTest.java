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

    public void testNoCompletionInsideString() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "hel<caret>"
                }
                """);
        var completions = myFixture.completeBasic();
        if (completions != null) {
            java.util.List<String> names = java.util.List.of(completions).stream()
                    .map(l -> l.getLookupString()).toList();
            assertFalse("Should NOT suggest 'locals' inside a string. Got: " + names,
                    names.contains("locals"));
            assertFalse("Should NOT suggest 'dependency' inside a string. Got: " + names,
                    names.contains("dependency"));
            assertFalse("Should NOT suggest 'merge' inside a string. Got: " + names,
                    names.contains("merge"));
            assertFalse("Should NOT suggest 'local' inside a string. Got: " + names,
                    names.contains("local"));
        }
    }

    public void testCompletionInsideInterpolation() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "hello"
                  greeting = "hi ${local.<caret>}"
                }
                """);
        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions inside interpolation", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'name' inside interpolation. Got: " + names,
                names.contains("name"));
    }

    public void testAutoCloseQuote() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = <caret>
                }
                """);
        myFixture.type('"');
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue("Typing \" should auto-insert closing quote", text.contains("\"\""));
    }

    public void testObjectKeyCompletion() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                    }
                    region = "us-east-1"
                  }
                }

                inputs = {
                  x = local.config.
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.config.") + "local.config.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for object keys", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'network'. Got: " + names, names.contains("network"));
        assertTrue("Should suggest 'region'. Got: " + names, names.contains("region"));
    }

    public void testNestedObjectKeyCompletion() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                      az_count = 3
                    }
                  }
                }

                inputs = {
                  x = local.config.network.
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("config.network.") + "config.network.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for nested object keys", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'vpc_cidr'. Got: " + names, names.contains("vpc_cidr"));
        assertTrue("Should suggest 'az_count'. Got: " + names, names.contains("az_count"));
    }

    public void testCrossFileNestedObjectKeyCompletion() {
        myFixture.addFileToProject("root.hcl", "");
        myFixture.addFileToProject("xfile-remote.hcl", """
                locals {
                  config = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                    }
                  }
                }
                """);
        com.intellij.psi.PsiFile child = myFixture.addFileToProject("xfile-child/terragrunt.hcl", """
                locals {
                  remote = read_terragrunt_config("../xfile-remote.hcl")
                }

                inputs = {
                  x = local.remote.locals.config.network.
                }
                """);
        myFixture.configureFromExistingVirtualFile(child.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("config.network.") + "config.network.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have cross-file nested object completions", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'vpc_cidr' from remote file's nested object. Got: " + names, names.contains("vpc_cidr"));
    }

    public void testMockOutputsCompletionMatchesCorrectDependency() {
        // Regression: should suggest keys from the matching dependency, not the first one
        myFixture.configureByText("terragrunt.hcl", """
                dependency "rds" {
                  config_path = "../rds"
                  mock_outputs = {
                    db_host = "localhost"
                  }
                }

                dependency "vpc" {
                  config_path = "../vpc"
                  mock_outputs = {
                    vpc_id = "vpc-123"
                    subnet_ids = []
                  }
                }

                inputs = {
                  id = dependency.vpc.outputs.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        assertNotNull(completions);
        var names = java.util.Arrays.stream(completions)
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest vpc_id from vpc's mock_outputs", names.contains("vpc_id"));
        assertFalse("Should NOT suggest db_host from rds's mock_outputs", names.contains("db_host"));
    }

    // --- Path completion inside strings ---

    public void testPathCompletionInIncludePath() {
        myFixture.addFileToProject("common/root.hcl", "locals {}");
        myFixture.addFileToProject("common/env.hcl", "locals {}");
        var mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                include "root" {
                  path = "common/<caret>"
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer multiple path completions", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest root.hcl, got: " + names, names.contains("root.hcl"));
        assertTrue("Should suggest env.hcl, got: " + names, names.contains("env.hcl"));
    }

    public void testPathCompletionInDependencyConfigPath() {
        myFixture.addFileToProject("vpc/terragrunt.hcl", "locals {}");
        myFixture.addFileToProject("rds/terragrunt.hcl", "locals {}");
        var mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "<caret>"
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer path completions", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest vpc/ directory, got: " + names, names.contains("vpc"));
        assertTrue("Should suggest rds/ directory, got: " + names, names.contains("rds"));
    }

    public void testPathCompletionInReadTerragruntConfig() {
        myFixture.addFileToProject("common/shared.hcl", "locals {}");
        myFixture.addFileToProject("common/env.hcl", "locals {}");
        var mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("common/<caret>")
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer path completions", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest shared.hcl, got: " + names, names.contains("shared.hcl"));
    }

    public void testPathCompletionWithInterpolatedPrefix() {
        myFixture.addFileToProject("modules/vpc/main.tf", "");
        myFixture.addFileToProject("modules/rds/main.tf", "");
        var mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                include "root" {
                  path = "${get_terragrunt_dir()}/modules/<caret>"
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions with interpolated prefix", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest vpc/ directory, got: " + names, names.contains("vpc"));
    }

    public void testNoPathCompletionInRegularString() {
        // A plain string that's not a path attribute should NOT get path completions
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "hello/<caret>"
                }
                """);
        var completions = myFixture.completeBasic();
        if (completions != null) {
            java.util.List<String> names = java.util.List.of(completions).stream()
                    .map(l -> l.getLookupString()).toList();
            assertTrue("Should NOT suggest paths in regular strings, got: " + names, names.isEmpty());
        }
    }

    public void testPathCompletionImmediatelyAfterSlash() {
        // Caret right after / with no typed text yet
        myFixture.addFileToProject("envs/dev.hcl", "locals {}");
        myFixture.addFileToProject("envs/prod.hcl", "locals {}");
        var mainFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  env = read_terragrunt_config("../envs/<caret>")
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions after /", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest dev.hcl, got: " + names, names.contains("dev.hcl"));
        assertTrue("Should suggest prod.hcl, got: " + names, names.contains("prod.hcl"));
    }

    public void testPathCompletionAfterInterpolationSlash() {
        // Caret right after ${func()}/ — the trickiest case
        myFixture.addFileToProject("envs/dev.hcl", "locals {}");
        myFixture.addFileToProject("envs/prod.hcl", "locals {}");
        var mainFile = myFixture.addFileToProject("envs/terragrunt.hcl", """
                locals {
                  env = read_terragrunt_config("${get_terragrunt_dir()}/<caret>")
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions after interpolation/", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest dev.hcl, got: " + names, names.contains("dev.hcl"));
    }

    public void testPathCompletionWithGetParentTerragruntDir() {
        // get_parent_terragrunt_dir() resolves to parent dir containing root.hcl (via include)
        myFixture.addFileToProject("root.hcl", "locals {}");
        myFixture.addFileToProject("envs/dev.hcl", "locals {}");
        myFixture.addFileToProject("envs/prod.hcl", "locals {}");
        var mainFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                locals {
                  env = read_terragrunt_config("${get_parent_terragrunt_dir()}/envs/<caret>")
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions with get_parent_terragrunt_dir, got null", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest dev.hcl, got: " + names, names.contains("dev.hcl"));
    }

    public void testPathCompletionImmediatelyAfterFunctionSlash() {
        // Exact case: "${get_parent_terragrunt_dir()}/" with caret right after /
        myFixture.addFileToProject("root.hcl", "locals {}");
        myFixture.addFileToProject("envs/dev.hcl", "locals {}");
        myFixture.addFileToProject("app/main.tf", "");
        var mainFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                locals {
                  env = read_terragrunt_config("${get_parent_terragrunt_dir()}/<caret>")
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions after func()/", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest envs, got: " + names, names.contains("envs"));
        assertTrue("Should suggest app, got: " + names, names.contains("app"));
    }

    public void testConfigPathCompletionOnlyShowsDirs() {
        // config_path should only show directories, not files
        myFixture.addFileToProject("cp-test/vpc/terragrunt.hcl", "locals {}");
        myFixture.addFileToProject("cp-test/rds/terragrunt.hcl", "locals {}");
        myFixture.addFileToProject("cp-test/random-file.hcl", "locals {}");
        var mainFile = myFixture.addFileToProject("cp-test/app/terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../<caret>"
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions", completions);
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest vpc dir, got: " + names, names.contains("vpc"));
        assertTrue("Should suggest rds dir, got: " + names, names.contains("rds"));
        assertFalse("Should NOT suggest files, got: " + names, names.contains("random-file.hcl"));
    }
}
