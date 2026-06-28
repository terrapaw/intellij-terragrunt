package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.inspection.TerragruntDeprecatedAttributeInspection;
import com.github.terrapaw.terragrunt.inspection.TerragruntDuplicateBlockInspection;
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

    public void testUnresolvedPathSuppressedForStackGenerated() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedPathInspection());
        // Create a terragrunt.stack.hcl that defines a unit with path "api"
        myFixture.addFileToProject("terragrunt.stack.hcl", """
                unit "api" {
                  source = "./units/api"
                  path   = "api"
                }
                """);
        // Reference the .terragrunt-stack/api path which doesn't exist yet
        myFixture.configureByText("terragrunt.hcl", """
                dependency "api" {
                  config_path = "./.terragrunt-stack/api"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Cannot resolve"))
                .count();
        assertEquals("Should not warn for stack-generated path", 0, warnings);
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

    public void testDuplicateBlockDetected() {
        myFixture.enableInspections(new TerragruntDuplicateBlockInspection());
        myFixture.configureByText("terragrunt.hcl", """
              dependency "vpc" {
                config_path = "../vpc"
              }

              dependency "vpc" {
                config_path = "../other-vpc"
              }
              """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Duplicate"))
                .count();
        assertEquals("Should detect duplicate dependency block", 1, warnings);
    }

    public void testNoDuplicateWarningForDifferentLabels() {
        myFixture.enableInspections(new TerragruntDuplicateBlockInspection());
        myFixture.configureByText("terragrunt.hcl", """
              dependency "vpc" {
                config_path = "../vpc"
              }

              dependency "rds" {
                config_path = "../rds"
              }
              """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Duplicate"))
                .count();
        assertEquals("Different labels should not trigger duplicate warning", 0, warnings);
    }

    public void testTooManyLabelsDetected() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntLabelCountInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" "extra" {
                  config_path = "../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("expects 1 label"))
                .count();
        assertEquals("Should warn about extra label", 1, warnings);
    }

    public void testMissingLabelDetected() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntLabelCountInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency {
                  config_path = "../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("requires a label"))
                .count();
        assertEquals("Should warn about missing label", 1, warnings);
    }

    public void testEmptyLabelDetected() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntLabelCountInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "" {
                  config_path = "../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("must not be empty"))
                .count();
        assertEquals("Should warn about empty label", 1, warnings);
    }

    public void testCorrectLabelCountNoWarning() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntLabelCountInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                locals {
                  x = 1
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("label"))
                .count();
        assertEquals("Correct label usage should not warn", 0, warnings);
    }

    public void testNoinspectionCommentSuppressesWarning() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedPathInspection());
        myFixture.configureByText("terragrunt.hcl", """
                # noinspection TerragruntUnresolvedPath
                include "root" {
                  path = "../nonexistent-file.hcl"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Cannot resolve"))
                .count();
        assertEquals("noinspection comment should suppress warning", 0, warnings);
    }

    public void testInspectionDoesNotCrashOnBlockAtFileStart() {
        // Regression test: isSuppressedFor should handle blocks at file start
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnknownBlockInspection());
        myFixture.configureByText("terragrunt.hcl", "badblock {\n}\n");
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getSeverity().getName().equals("WARNING") || h.getSeverity().getName().equals("GENERIC_ERROR_OR_WARNING"))
                .count();
        assertTrue("Should produce a warning, not crash", warnings > 0);
    }

    public void testInspectionDoesNotCrashOnBlockAfterSingleNewline() {
        // Edge case: block on line 2 where prevLineStart would be -1
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnknownBlockInspection());
        myFixture.configureByText("terragrunt.hcl", "\nbadblock {\n}\n");
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getSeverity().getName().equals("WARNING") || h.getSeverity().getName().equals("GENERIC_ERROR_OR_WARNING"))
                .count();
        assertTrue("Should produce a warning, not crash", warnings > 0);
    }

    // --- Unused local variable inspection ---

    public void testUnusedLocalFlagged() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnusedLocalInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                locals {
                  used   = "yes"
                  unused = "no"
                }
                
                inputs = {
                  val = local.used
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused local variable 'unused'"))
                .count();
        assertEquals("Should flag 'unused'", 1, warnings);
        long noFalsePositive = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused local variable 'used'"))
                .count();
        assertEquals("Should NOT flag 'used'", 0, noFalsePositive);
    }

    public void testUnusedLocalNotFlaggedInSharedConfig() {
        // Files without include/dependency are shared configs — locals are exported cross-file
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnusedLocalInspection());
        myFixture.configureByText("root.hcl", """
                locals {
                  region = "us-east-1"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused local variable"))
                .count();
        assertEquals("Should NOT flag in shared config", 0, warnings);
    }

    public void testUnusedLocalSuppressed() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnusedLocalInspection());
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                # noinspection TerragruntUnusedLocal
                locals {
                  unused = "no"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused local variable"))
                .count();
        assertEquals("Should be suppressed", 0, warnings);
    }

    // --- Duplicate attribute inspection ---

    public void testDuplicateAttributeFlagged() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntDuplicateAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "first"
                  name = "second"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Duplicate attribute 'name'"))
                .count();
        assertEquals("Should flag duplicate", 1, warnings);
    }

    public void testNoDuplicateAttributeForDifferentNames() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntDuplicateAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name   = "first"
                  region = "us-east-1"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Duplicate attribute"))
                .count();
        assertEquals("Should not flag unique attrs", 0, warnings);
    }

    // --- Unused dependency inspection ---

    public void testUnusedDependencyFlagged() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnusedDependencyInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                dependency "rds" {
                  config_path = "../rds"
                }
                
                inputs = {
                  vpc_id = dependency.vpc.outputs.vpc_id
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused dependency 'rds'"))
                .count();
        assertEquals("Should flag 'rds'", 1, warnings);
        long noFalsePositive = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused dependency 'vpc'"))
                .count();
        assertEquals("Should NOT flag 'vpc'", 0, noFalsePositive);
    }

    public void testUnusedDependencySuppressed() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnusedDependencyInspection());
        myFixture.configureByText("terragrunt.hcl", """
                # noinspection TerragruntUnusedDependency
                dependency "unused" {
                  config_path = "../unused"
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused dependency"))
                .count();
        assertEquals("Should be suppressed", 0, warnings);
    }

    // --- Cross-file unused local inspection ---

    public void testCrossFileUnusedLocalNotFlaggedWhenReferencedExternally() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnusedLocalCrossFileInspection());
        var shared = myFixture.addFileToProject("root.hcl", """
                locals {
                  region   = "us-east-1"
                  orphaned = "nobody uses me"
                }
                """);
        myFixture.addFileToProject("app/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                }
                
                inputs = {
                  r = include.root.locals.region
                }
                """);
        myFixture.configureFromExistingVirtualFile(shared.getVirtualFile());
        var highlights = myFixture.doHighlighting();
        long regionWarnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("'region'"))
                .count();
        assertEquals("region referenced externally — should NOT be flagged", 0, regionWarnings);
        long orphanedWarnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("'orphaned'"))
                .count();
        assertEquals("orphaned not referenced anywhere — should be flagged", 1, orphanedWarnings);
    }
}
