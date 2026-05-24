package com.github.joelm.terragrunt;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class TerragruntCompletionTest extends BasePlatformTestCase {

    // --- Top-level body completions ---

    public void testTopLevelSuggestsBlocks() {
        myFixture.configureByText("terragrunt.hcl", "<caret>");
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'terraform' block", names.contains("terraform"));
        assertTrue("Should suggest 'include' block", names.contains("include"));
        assertTrue("Should suggest 'dependency' block", names.contains("dependency"));
        assertTrue("Should suggest 'locals' block", names.contains("locals"));
        assertTrue("Should suggest 'remote_state' block", names.contains("remote_state"));
        assertTrue("Should suggest 'inputs' attribute", names.contains("inputs"));
    }

    public void testTopLevelDoesNotSuggestFunctions() {
        myFixture.configureByText("terragrunt.hcl", "<caret>");
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertFalse("Should NOT suggest functions at top level", names.contains("find_in_parent_folders"));
        assertFalse("Should NOT suggest 'concat' at top level", names.contains("concat"));
    }

    // --- Inside block body completions ---

    public void testDependencyBlockSuggestsAttributes() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'config_path'", names.contains("config_path"));
        assertTrue("Should suggest 'mock_outputs'", names.contains("mock_outputs"));
        assertFalse("Should NOT suggest 'terraform' block inside dependency", names.contains("terraform"));
    }

    public void testIncludeBlockSuggestsAttributes() {
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'path'", names.contains("path"));
        assertTrue("Should suggest 'expose'", names.contains("expose"));
        assertTrue("Should suggest 'merge_strategy'", names.contains("merge_strategy"));
    }

    // --- Expression context completions ---

    public void testExpressionSuggestsFunctions() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'find_in_parent_folders'", names.contains("find_in_parent_folders"));
        assertTrue("Should suggest 'get_env'", names.contains("get_env"));
        assertTrue("Should suggest 'local' prefix", names.contains("local"));
    }

    public void testExpressionSuggestsForExpressions() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest '[for' tuple expression", names.contains("[for"));
        assertTrue("Should suggest '{for' object expression", names.contains("{for"));
    }

    public void testForVariableCompletion() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  names = [for item in local.list : <caret>]
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'item' for variable", names.contains("item"));
    }

    public void testForKeyValueVariableCompletion() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  result = {for key, value in local.map : key => <caret>}
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'key' for variable", names.contains("key"));
        assertTrue("Should suggest 'value' for variable", names.contains("value"));
    }

    public void testExpressionDoesNotSuggestBlocks() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertFalse("Should NOT suggest 'terraform' block in expression", names.contains("terraform"));
        assertFalse("Should NOT suggest 'remote_state' block in expression", names.contains("remote_state"));
    }

    // --- Dot-completion ---

    public void testDependencyDotSuggestsNames() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                dependency "rds" {
                  config_path = "../rds"
                }
                inputs = {
                  x = dependency.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'vpc'", names.contains("vpc"));
        assertTrue("Should suggest 'rds'", names.contains("rds"));
        assertFalse("Should NOT suggest functions after dependency.", names.contains("concat"));
        assertFalse("Should NOT suggest 'outputs' at this depth", names.contains("outputs"));
    }

    public void testDependencyNameDotSuggestsOutputs() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                inputs = {
                  x = dependency.vpc.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'outputs'", names.contains("outputs"));
        assertFalse("Should NOT suggest 'vpc' again", names.contains("vpc"));
    }

    public void testLocalDotSuggestsAttributes() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  region   = "us-east-1"
                  app_name = "my-app"
                }
                inputs = {
                  x = local.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'region'", names.contains("region"));
        assertTrue("Should suggest 'app_name'", names.contains("app_name"));
        assertFalse("Should NOT suggest functions", names.contains("concat"));
    }

    public void testFeatureDotSuggestsNames() {
        myFixture.configureByText("terragrunt.hcl", """
                feature "multi_az" {
                  default = false
                }
                inputs = {
                  x = feature.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'multi_az'", names.contains("multi_az"));
    }

    public void testFeatureNameDotSuggestsValue() {
        myFixture.configureByText("terragrunt.hcl", """
                feature "multi_az" {
                  default = false
                }
                inputs = {
                  x = feature.multi_az.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'value'", names.contains("value"));
    }
}
