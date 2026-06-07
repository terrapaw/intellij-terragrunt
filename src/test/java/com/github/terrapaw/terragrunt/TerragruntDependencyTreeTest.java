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
}
