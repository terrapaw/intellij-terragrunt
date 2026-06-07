package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.editor.TerragruntFoldingBuilder;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;

public class TerragruntEditorTest extends BasePlatformTestCase {

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
        var foldingBuilder = new TerragruntFoldingBuilder();
        FoldingDescriptor[] descriptors = foldingBuilder.buildFoldRegions(file, myFixture.getEditor().getDocument(), false);
        assertTrue("Should create folding regions for blocks", descriptors.length >= 2);
    }

    public void testFoldingNotCollapsedByDefault() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", "locals { x = 1 }");
        var foldingBuilder = new TerragruntFoldingBuilder();
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        for (TerragruntBlock block : blocks) {
            assertFalse("Blocks should not be collapsed by default",
                    foldingBuilder.isCollapsedByDefault(block.getNode()));
        }
    }

    public void testFoldingPlaceholderShowsBlockName() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = "root.hcl"
                }
                """);
        var foldingBuilder = new TerragruntFoldingBuilder();
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        TerragruntBlock block = blocks.iterator().next();
        String placeholder = foldingBuilder.getPlaceholderText(block.getNode());
        assertTrue("Placeholder should contain block name", placeholder.contains("include"));
        assertTrue("Placeholder should contain label", placeholder.contains("root"));
    }

    public void testFileTemplateUnitExists() {
        var template = com.intellij.ide.fileTemplates.FileTemplateManager.getInstance(getProject())
                .getTemplate("Terragrunt Unit");
        assertNotNull("Terragrunt Unit template should exist", template);
        assertTrue(template.getText().contains("include"));
        assertTrue(template.getText().contains("inputs"));
    }

    public void testFileTemplateRootExists() {
        var template = com.intellij.ide.fileTemplates.FileTemplateManager.getInstance(getProject())
                .getTemplate("Terragrunt Root");
        assertNotNull("Terragrunt Root template should exist", template);
        assertTrue(template.getText().contains("remote_state"));
    }

    public void testFileTemplateStackExists() {
        var template = com.intellij.ide.fileTemplates.FileTemplateManager.getInstance(getProject())
                .getTemplate("Terragrunt Stack");
        assertNotNull("Terragrunt Stack template should exist", template);
        assertTrue(template.getText().contains("unit"));
    }

    public void testFileTemplatesRenderWithoutVelocityErrors() throws Exception {
        var manager = com.intellij.ide.fileTemplates.FileTemplateManager.getInstance(getProject());
        var props = manager.getDefaultProperties();
        for (String name : new String[]{"Terragrunt Unit", "Terragrunt Root", "Terragrunt Stack"}) {
            var template = manager.getTemplate(name);
            assertNotNull(name + " template should exist", template);
            // This will throw if Velocity can't parse the template
            String result = template.getText(props);
            assertNotNull(name + " should render without error", result);
            assertFalse(name + " should not be empty", result.isBlank());
        }
    }

    public void testStructureViewShowsBlocksAndAttributes() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  region = "us-east-1"
                }

                dependency "vpc" {
                  config_path = "../vpc"
                }

                inputs = {
                  name = "app"
                }
                """);
        var factory = new com.github.terrapaw.terragrunt.editor.TerragruntStructureViewFactory();
        var viewBuilder = factory.getStructureViewBuilder(file);
        assertNotNull("Structure view builder should be created", viewBuilder);

        var model = ((com.intellij.ide.structureView.TreeBasedStructureViewBuilder) viewBuilder)
                .createStructureViewModel(myFixture.getEditor());
        var root = model.getRoot();
        var children = root.getChildren();

        // Should have: locals, dependency "vpc", inputs
        assertTrue("Should have at least 3 top-level elements", children.length >= 3);

        var names = new java.util.ArrayList<String>();
        for (var child : children) {
            names.add(child.getPresentation().getPresentableText());
        }
        assertTrue("Should contain locals block", names.contains("locals"));
        assertTrue("Should contain dependency with label",
                names.stream().anyMatch(n -> n.contains("dependency") && n.contains("vpc")));
        assertTrue("Should contain inputs attribute", names.contains("inputs"));

        // Verify inputs shows its object keys as children
        for (var child : children) {
            if ("inputs".equals(child.getPresentation().getPresentableText())) {
                var inputChildren = ((com.intellij.ide.structureView.StructureViewTreeElement) child).getChildren();
                assertTrue("inputs should show object keys as children", inputChildren.length > 0);
                var childNames = new java.util.ArrayList<String>();
                for (var ic : inputChildren) {
                    childNames.add(ic.getPresentation().getPresentableText());
                }
                assertTrue("inputs should contain 'name' key", childNames.contains("name"));
            }
        }
    }
}
