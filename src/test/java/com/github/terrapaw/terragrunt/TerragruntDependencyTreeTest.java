package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.toolwindow.TerragruntDependencyScanner;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class TerragruntDependencyTreeTest extends BasePlatformTestCase {

    public void testScanFindsNoDependencies() {
        myFixture.addFileToProject("root.hcl", "");
        myFixture.addFileToProject("vpc/terragrunt.hcl", """
                locals {
                  name = "vpc"
                }
                """);
        var nodes = TerragruntDependencyScanner.scanProject(getProject());
        assertFalse("Should find at least one node", nodes.isEmpty());
        var vpc = nodes.stream().filter(n -> n.displayName().contains("vpc")).findFirst().orElse(null);
        assertNotNull(vpc);
        assertTrue("vpc should have no dependencies", vpc.dependencyPaths().isEmpty());
    }

    public void testScanFindsDependencyBlock() {
        myFixture.addFileToProject("root.hcl", "");
        myFixture.addFileToProject("vpc/terragrunt.hcl", """
                locals {
                  name = "vpc"
                }
                """);
        myFixture.addFileToProject("app/terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        var nodes = TerragruntDependencyScanner.scanProject(getProject());
        var app = nodes.stream().filter(n -> n.displayName().contains("app")).findFirst().orElse(null);
        assertNotNull(app);
        assertEquals("app should have 1 dependency", 1, app.dependencyPaths().size());
        assertTrue("Should point to vpc", app.dependencyPaths().get(0).contains("vpc"));
    }

    public void testScanFindsDependenciesBlock() {
        myFixture.addFileToProject("root.hcl", "");
        myFixture.addFileToProject("vpc/terragrunt.hcl", "locals {}");
        myFixture.addFileToProject("rds/terragrunt.hcl", "locals {}");
        myFixture.addFileToProject("app/terragrunt.hcl", """
                dependencies {
                  paths = ["../vpc", "../rds"]
                }
                """);
        var nodes = TerragruntDependencyScanner.scanProject(getProject());
        var app = nodes.stream().filter(n -> n.displayName().contains("app")).findFirst().orElse(null);
        assertNotNull(app);
        assertEquals("app should have 2 dependencies", 2, app.dependencyPaths().size());
    }

    public void testEntryPointDetection() {
        myFixture.addFileToProject("root.hcl", "");
        myFixture.addFileToProject("vpc/terragrunt.hcl", "locals {}");
        myFixture.addFileToProject("app/terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        var nodes = TerragruntDependencyScanner.scanProject(getProject());

        // Collect all dependency paths to find entry points
        java.util.Set<String> allDepPaths = new java.util.HashSet<>();
        for (var node : nodes) allDepPaths.addAll(node.dependencyPaths());

        // app depends on vpc, so vpc is NOT an entry point. app IS an entry point.
        var app = nodes.stream().filter(n -> n.displayName().contains("app")).findFirst().orElse(null);
        var vpc = nodes.stream().filter(n -> n.displayName().contains("vpc")).findFirst().orElse(null);
        assertNotNull(app);
        assertNotNull(vpc);
        assertFalse("app should be entry point (nothing depends on it)", allDepPaths.contains(app.file().getPath()));
        assertTrue("vpc should NOT be entry point (app depends on it)", allDepPaths.contains(vpc.file().getPath()));
    }

    public void testUnresolvedDependencyPathDoesNotCrash() {
        myFixture.addFileToProject("root.hcl", "");
        myFixture.addFileToProject("app/terragrunt.hcl", """
                dependency "missing" {
                  config_path = "../nonexistent"
                }
                """);
        var nodes = TerragruntDependencyScanner.scanProject(getProject());
        var app = nodes.stream().filter(n -> n.displayName().contains("app")).findFirst().orElse(null);
        assertNotNull(app);
        assertTrue("Unresolved paths should not appear in dependency list", app.dependencyPaths().isEmpty());
    }

    public void testScannerDoesNotCrashWithFileAtContentRoot() {
        // Smoke test: terragrunt.hcl at the root level
        myFixture.addFileToProject("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "./vpc"
                }
                """);
        myFixture.addFileToProject("vpc/terragrunt.hcl", "locals {}");
        // Should not crash
        var nodes = TerragruntDependencyScanner.scanProject(getProject());
        assertFalse("Should find nodes", nodes.isEmpty());
    }
}
