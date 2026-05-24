package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.inspection.TerragruntDeprecatedAttributeInspection;
import com.github.terrapaw.terragrunt.inspection.TerragruntMissingAttributeInspection;
import com.github.terrapaw.terragrunt.inspection.TerragruntUnknownBlockInspection;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntInspectionTest extends BasePlatformTestCase {

    public void testValidFileHasNoUnknownBlockWarnings() {
        myFixture.enableInspections(new TerragruntUnknownBlockInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path   = find_in_parent_folders("root.hcl")
                  expose = true
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                locals {
                  region = "us-east-1"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown Terragrunt block"))
                .count();
        assertEquals("Valid file should have no unknown block warnings", 0, warnings);
    }

    public void testValidFileHasNoMissingAttributeWarnings() {
        myFixture.enableInspections(new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                generate "provider" {
                  path      = "provider.tf"
                  if_exists = "overwrite"
                  contents  = "provider {}"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Missing required attribute"))
                .count();
        assertEquals("Valid file should have no missing attribute warnings", 0, warnings);
    }

    public void testUnknownBlockDetected() {
        myFixture.enableInspections(new TerragruntUnknownBlockInspection());
        myFixture.configureByText("terragrunt.hcl", """
                foobar {
                  something = "value"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown Terragrunt block 'foobar'"))
                .count();
        assertEquals("Should detect unknown block 'foobar'", 1, warnings);
    }

    public void testMissingRequiredAttributeDetected() {
        myFixture.enableInspections(new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "broken" {
                  skip_outputs = true
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Missing required attribute 'config_path'"))
                .count();
        assertEquals("Should detect missing config_path in dependency", 1, warnings);
    }

    public void testDeprecatedAttributeDetected() {
        myFixture.enableInspections(new TerragruntDeprecatedAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "old" {
                  config_path = "../vpc"
                  mock_outputs_merge_with_state = true
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Deprecated attribute"))
                .count();
        assertEquals("Should detect deprecated attribute", 1, warnings);
    }

    public void testIncludeWithPathHasNoMissingWarning() {
        myFixture.enableInspections(new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Missing required attribute 'path'"))
                .count();
        assertEquals("Include with path should have no missing path warning", 0, warnings);
    }

    public void testDependencyWithConfigPathHasNoMissingWarning() {
        myFixture.enableInspections(new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                  mock_outputs = {
                    vpc_id = "vpc-123"
                  }
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Missing required attribute"))
                .count();
        assertEquals("Dependency with config_path should have no missing attribute warnings", 0, warnings);
    }

    public void testMultipleBlocksWithLabels() {
        myFixture.enableInspections(new TerragruntUnknownBlockInspection(), new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                include "env" {
                  path           = find_in_parent_folders("env.hcl")
                  expose         = true
                  merge_strategy = "no_merge"
                }
                
                feature "flag" {
                  default = false
                }
                """);
        var highlights = myFixture.doHighlighting();
        long unknownWarnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown Terragrunt block"))
                .count();
        long missingWarnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Missing required attribute"))
                .count();
        assertEquals("Should have no unknown block warnings", 0, unknownWarnings);
        assertEquals("Should have no missing attribute warnings", 0, missingWarnings);
    }

    public void testUnresolvedPathWarnsOnMissingFile() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedPathInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = "../nonexistent-file.hcl"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Cannot resolve"))
                .count();
        assertEquals("Should warn about nonexistent path", 1, warnings);
    }

    public void testUnresolvedPathSkipsFunctionCalls() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedPathInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Cannot resolve"))
                .count();
        assertEquals("Should not warn when path uses function call", 0, warnings);
    }

    public void testUnresolvedPathSkipsInterpolation() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedPathInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "${get_terragrunt_dir()}/../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Cannot resolve"))
                .count();
        assertEquals("Should not warn for interpolated path", 0, warnings);
    }

    public void testUnknownAttributeDetected() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnknownAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                  nonexistent_attr = "bad"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown attribute 'nonexistent_attr'"))
                .count();
        assertEquals("Should detect unknown attribute", 1, warnings);
    }

    public void testUnknownAttributeNotTriggeredForValidAttrs() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnknownAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                  skip_outputs = true
                  mock_outputs = {}
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown attribute"))
                .count();
        assertEquals("Should not warn for valid attributes", 0, warnings);
    }

    public void testUnknownAttributeNotTriggeredInLocals() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnknownAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  anything = "valid"
                  custom_name = "also valid"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown attribute"))
                .count();
        assertEquals("locals block should allow any attribute", 0, warnings);
    }
}
