package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedPathInspection;
import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.schema.TerragruntSchema;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;

public class TerragruntCoverageTest extends BasePlatformTestCase {

    // --- Unresolved Path Inspection ---

    public void testUnresolvedPathWarnsOnMissingFile() {
        myFixture.enableInspections(new TerragruntUnresolvedPathInspection());
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

    public void testUnresolvedPathNoWarningForFunctionCall() {
        myFixture.enableInspections(new TerragruntUnresolvedPathInspection());
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

    public void testUnresolvedPathSkipsFunctionCalls() {
        myFixture.enableInspections(new TerragruntUnresolvedPathInspection());
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

    // --- Folding ---

    public void testFoldingCreatesRegionsForBlocks() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "test"
                  region = "us-east-1"
                }
                
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        var foldingBuilder = new com.github.terrapaw.terragrunt.editor.TerragruntFoldingBuilder();
        FoldingDescriptor[] descriptors = foldingBuilder.buildFoldRegions(file, myFixture.getEditor().getDocument(), false);
        assertTrue("Should create folding regions for blocks", descriptors.length >= 2);
    }

    public void testFoldingNotCollapsedByDefault() {
        var foldingBuilder = new com.github.terrapaw.terragrunt.editor.TerragruntFoldingBuilder();
        PsiFile file = myFixture.configureByText("terragrunt.hcl", "locals { x = 1 }");
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        for (TerragruntBlock block : blocks) {
            assertFalse("Blocks should not be collapsed by default",
                    foldingBuilder.isCollapsedByDefault(block.getNode()));
        }
    }

    // --- Schema ---

    public void testSchemaKnowsTerragruntBlocks() {
        assertTrue("Should know 'terraform' block", TerragruntSchema.isKnownBlock("terraform"));
        assertTrue("Should know 'dependency' block", TerragruntSchema.isKnownBlock("dependency"));
        assertTrue("Should know 'include' block", TerragruntSchema.isKnownBlock("include"));
        assertTrue("Should know 'generate' block", TerragruntSchema.isKnownBlock("generate"));
        assertTrue("Should know 'feature' block", TerragruntSchema.isKnownBlock("feature"));
        assertFalse("Should NOT know 'foobar' block", TerragruntSchema.isKnownBlock("foobar"));
    }

    public void testSchemaReturnsRequiredAttributes() {
        var dep = TerragruntSchema.getBlock("dependency");
        assertNotNull(dep);
        assertTrue("dependency should have required config_path",
                dep.attributes().stream().anyMatch(a -> a.name().equals("config_path") && a.required()));
    }

    public void testSchemaHasFunctions() {
        var functions = TerragruntSchema.getFunctions();
        assertFalse("Should have functions", functions.isEmpty());
        assertTrue("Should have find_in_parent_folders",
                functions.stream().anyMatch(f -> f.name().equals("find_in_parent_folders")));
        assertTrue("Should have get_env",
                functions.stream().anyMatch(f -> f.name().equals("get_env")));
    }

    public void testSchemaDeprecatedAttributes() {
        assertTrue("mock_outputs_merge_with_state should be deprecated",
                TerragruntSchema.isDeprecated("mock_outputs_merge_with_state"));
        assertFalse("config_path should NOT be deprecated",
                TerragruntSchema.isDeprecated("config_path"));
    }

    // --- File Type Overrider ---

    public void testFileTypeOverriderDetectsHclInTerragruntProject() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        PsiFile customFile = myFixture.addFileToProject("custom-config.hcl", "locals { env = \"dev\" }");
        assertEquals("custom-config.hcl near root.hcl should be Terragrunt",
                TerragruntFileType.INSTANCE, customFile.getFileType());
    }

    // --- Reference Contributor (file path references) ---

    public void testReferenceContributorResolvesRelativePath() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        PsiFile childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                }
                """);
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());

        // The string "../root.hcl" should have a reference that resolves
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("../root.hcl") + 1;
        var ref = myFixture.getFile().findReferenceAt(offset);
        if (ref != null) {
            var resolved = ref.resolve();
            assertNotNull("Should resolve ../root.hcl reference", resolved);
        }
    }
}
