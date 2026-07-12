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

    public void testFoldingCreatesRegionForInputsAttribute() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  name = "test"
                  region = "us-east-1"
                }
                """);
        var foldingBuilder = new TerragruntFoldingBuilder();
        FoldingDescriptor[] descriptors = foldingBuilder.buildFoldRegions(file, myFixture.getEditor().getDocument(), false);
        assertTrue("Should create folding region for inputs attribute", descriptors.length >= 1);
        String placeholder = foldingBuilder.getPlaceholderText(descriptors[0].getElement());
        assertEquals("inputs = {...}", placeholder);
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

    public void testStructureViewNestedObjects() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  network = {
                    vpc_cidr = "10.0.0.0/16"
                    subnets = {
                      public = "10.0.1.0/24"
                    }
                  }
                }
                """);
        var factory = new com.github.terrapaw.terragrunt.editor.TerragruntStructureViewFactory();
        var viewBuilder = factory.getStructureViewBuilder(file);
        var model = ((com.intellij.ide.structureView.TreeBasedStructureViewBuilder) viewBuilder)
                .createStructureViewModel(myFixture.getEditor());
        var root = model.getRoot();
        // root > locals > network > subnets > public
        var locals = root.getChildren()[0]; // locals block
        var network = ((com.intellij.ide.structureView.StructureViewTreeElement) locals).getChildren()[0]; // network attr
        assertEquals("network", network.getPresentation().getPresentableText());
        var networkChildren = ((com.intellij.ide.structureView.StructureViewTreeElement) network).getChildren();
        assertTrue("network should have children", networkChildren.length >= 2);
        // Find subnets
        for (var child : networkChildren) {
            if ("subnets".equals(child.getPresentation().getPresentableText())) {
                var subnetsChildren = ((com.intellij.ide.structureView.StructureViewTreeElement) child).getChildren();
                assertTrue("subnets should have children", subnetsChildren.length > 0);
                assertEquals("public", subnetsChildren[0].getPresentation().getPresentableText());
            }
        }
    }

    public void testBreadcrumbsForBlock() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                """);
        var provider = new com.github.terrapaw.terragrunt.editor.TerragruntBreadcrumbsProvider();
        // Find the path attribute
        var attr = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file,
                com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute.class);
        assertNotNull(attr);
        assertTrue(provider.acceptElement(attr));
        assertEquals("path", provider.getElementInfo(attr));

        // Find the include block
        var block = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file,
                com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock.class);
        assertNotNull(block);
        assertTrue(provider.acceptElement(block));
        assertEquals("include \"root\"", provider.getElementInfo(block));
    }

    public void testBreadcrumbsForNestedObjectKey() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                remote_state {
                  config = {
                    bucket = "my-bucket"
                  }
                }
                """);
        var provider = new com.github.terrapaw.terragrunt.editor.TerragruntBreadcrumbsProvider();
        var elems = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file,
                com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectElem.class);
        // Should find "bucket" object elem
        var bucket = elems.stream()
                .filter(e -> e.getIdentifier() != null && "bucket".equals(e.getIdentifier().getText()))
                .findFirst().orElse(null);
        assertNotNull("Should find bucket object elem", bucket);
        assertTrue(provider.acceptElement(bucket));
        assertEquals("bucket", provider.getElementInfo(bucket));
    }

    public void testBreadcrumbsShowsQuotedKeys() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  data = merge({"key1" = "val1"})
                }
                """);
        var provider = new com.github.terrapaw.terragrunt.editor.TerragruntBreadcrumbsProvider();
        var elems = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file,
                com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectElem.class);
        for (var elem : elems) {
            assertTrue("Should accept all object elems", provider.acceptElement(elem));
            String info = provider.getElementInfo(elem);
            assertFalse("Should not show '?'", info.equals("?"));
        }
    }

    public void testBlockTypeHighlightedAsKeyword() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int depOffset = text.indexOf("dependency");
        var blockHighlight = highlights.stream()
                .filter(h -> h.getStartOffset() == depOffset && h.getEndOffset() == depOffset + "dependency".length())
                .findFirst().orElse(null);
        assertNotNull("Block type 'dependency' should be highlighted as keyword", blockHighlight);
    }

    public void testAttributeHighlightedAsProperty() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int attrOffset = text.indexOf("config_path");
        var attrHighlight = highlights.stream()
                .filter(h -> h.getStartOffset() == attrOffset && h.getEndOffset() == attrOffset + "config_path".length())
                .findFirst().orElse(null);
        assertNotNull("Attribute 'config_path' should be highlighted as property", attrHighlight);
    }

    public void testObjectKeyHighlightedAsProperty() {
        myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  environment = "prod"
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int keyOffset = text.indexOf("environment");
        var keyHighlight = highlights.stream()
                .filter(h -> h.getStartOffset() == keyOffset && h.getEndOffset() == keyOffset + "environment".length())
                .findFirst().orElse(null);
        assertNotNull("Object key 'environment' should be highlighted as property", keyHighlight);
    }

    public void testNestedObjectKeyHighlighted() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    region = "us-east-1"
                  }
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int regionOffset = text.indexOf("region");
        var regionHighlight = highlights.stream()
                .filter(h -> h.getStartOffset() == regionOffset && h.getEndOffset() == regionOffset + "region".length())
                .findFirst().orElse(null);
        assertNotNull("Nested object key 'region' should be highlighted", regionHighlight);
    }

    public void testAllBlockTypesHighlighted() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = 1
                }
                include "root" {
                  path = "root.hcl"
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();

        int localsOffset = text.indexOf("locals");
        int includeOffset = text.indexOf("include");
        assertTrue("'locals' should be highlighted",
                highlights.stream().anyMatch(h -> h.getStartOffset() == localsOffset && h.getEndOffset() == localsOffset + "locals".length()));
        assertTrue("'include' should be highlighted",
                highlights.stream().anyMatch(h -> h.getStartOffset() == includeOffset && h.getEndOffset() == includeOffset + "include".length()));
    }

    public void testStringValuesNotHighlightedByAnnotator() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int stringOffset = text.indexOf("../vpc");
        var stringHighlight = highlights.stream()
                .filter(h -> h.getStartOffset() == stringOffset && h.getEndOffset() == stringOffset + "../vpc".length())
                .findFirst().orElse(null);
        assertNull("String value '../vpc' should NOT have semantic annotation", stringHighlight);
    }

    public void testBlockLabelNotHighlightedByAnnotator() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        // "vpc" label — the quotes are STRING tokens, content is STRING_LITERAL
        // The annotator should not add an extra highlight on the label
        int labelOffset = text.indexOf("\"vpc\"");
        var labelHighlight = highlights.stream()
                .filter(h -> h.getStartOffset() == labelOffset + 1 && h.getEndOffset() == labelOffset + 4)
                .findFirst().orElse(null);
        assertNull("Block label content 'vpc' should NOT have semantic annotation (already colored by lexer)", labelHighlight);
    }

    public void testVariableReferenceNotHighlighted() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  env = "prod"
                }
                inputs = {
                  val = local.env
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        // "local" in "local.env" is a variable reference, not a block type
        int localRefOffset = text.indexOf("local.env");
        var refHighlight = highlights.stream()
                .filter(h -> h.getStartOffset() == localRefOffset && h.getEndOffset() == localRefOffset + "local".length())
                .findFirst().orElse(null);
        assertNull("Variable reference 'local' should NOT be highlighted as keyword", refHighlight);
    }

    public void testEmptyBlockTypeStillHighlighted() {
        myFixture.configureByText("terragrunt.hcl", "locals {}\n");
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals");
        assertTrue("Empty block 'locals' should still be highlighted",
                highlights.stream().anyMatch(h -> h.getStartOffset() == offset && h.getEndOffset() == offset + "locals".length()));
    }

    public void testTopLevelAttributeHighlighted() {
        myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  name = "app"
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("inputs");
        assertTrue("Top-level attribute 'inputs' should be highlighted",
                highlights.stream().anyMatch(h -> h.getStartOffset() == offset && h.getEndOffset() == offset + "inputs".length()));
    }

    public void testQuotedObjectKeyDoesNotCrash() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  data = merge({"quoted_key" = "value"})
                }
                """);
        // Should not crash — quoted keys have null getIdentifier()
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        assertNotNull("Should complete highlighting without crash", highlights);
    }

    public void testNestedMergeObjectKeyHighlighted() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  data = merge({unquoted_key = "value"})
                }
                """);
        var highlights = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.INFORMATION);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("unquoted_key");
        assertTrue("Unquoted key inside merge() should be highlighted",
                highlights.stream().anyMatch(h -> h.getStartOffset() == offset && h.getEndOffset() == offset + "unquoted_key".length()));
    }

    public void testLiveTemplateContextBetweenBlocks() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                feature "x" {
                  default = false
                }

                terraform {
                  source = "test"
                }
                """);
        String text = file.getText();
        var ctx = new com.github.terrapaw.terragrunt.editor.TerragruntLiveTemplateContext();

        // Blank line between blocks
        int offset = text.indexOf("\n\nterraform") + 1;
        var tac = com.intellij.codeInsight.template.TemplateActionContext.expanding(file, offset);
        assertTrue("Should allow templates between blocks", ctx.isInContext(tac));
    }

    public void testLiveTemplateContextRejectedInsideInputs() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  name = "test"
                }
                """);
        String text = file.getText();
        var ctx = new com.github.terrapaw.terragrunt.editor.TerragruntLiveTemplateContext();

        // Inside inputs = { ... }
        int offset = text.indexOf("name") - 1;
        var tac = com.intellij.codeInsight.template.TemplateActionContext.expanding(file, offset);
        assertFalse("Should reject templates inside attribute value", ctx.isInContext(tac));
    }

    public void testLiveTemplateContextRejectedInsideBlock() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);
        String text = file.getText();
        var ctx = new com.github.terrapaw.terragrunt.editor.TerragruntLiveTemplateContext();

        // Inside the dependency block
        int offset = text.indexOf("config_path") - 1;
        var tac = com.intellij.codeInsight.template.TemplateActionContext.expanding(file, offset);
        assertFalse("Should reject templates inside block body", ctx.isInContext(tac));
    }

    public void testLiveTemplateContextAllowedAfterBlockWithTypedPrefix() {
        // Simulates typing "dep" on blank line after a block — parser creates malformed block
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                terraform {
                  source = "test"
                }
                dep
                locals {
                  name = "test"
                }
                """);
        String text = file.getText();
        var ctx = new com.github.terrapaw.terragrunt.editor.TerragruntLiveTemplateContext();

        // Offset at end of "dep" — parser sees it as a block identifier
        int offset = text.indexOf("dep") + 3;
        var tac = com.intellij.codeInsight.template.TemplateActionContext.expanding(file, offset);
        assertTrue("Should allow templates when offset is at block identifier (typing new block name)", ctx.isInContext(tac));
    }

    // --- Input Calculator ---

    public void testInputResolverCollectsInputs() {
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  region = "us-east-1"
                  env    = "prod"
                }
                
                inputs = {
                  aws_region  = local.region
                  environment = local.env
                  app_name    = "my-api"
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var inputs = com.github.terrapaw.terragrunt.toolwindow.TerragruntInputResolver.resolveInputs(myFixture.getFile());
        assertEquals("Should find 3 inputs", 3, inputs.size());
        assertEquals("aws_region", inputs.get(0).key());
        assertEquals("local.region", inputs.get(0).value());
        assertEquals("us-east-1", inputs.get(0).resolved());
        assertEquals("environment", inputs.get(1).key());
        assertEquals("local.env", inputs.get(1).value());
        assertEquals("prod", inputs.get(1).resolved());
        assertEquals("app_name", inputs.get(2).key());
        assertEquals("my-api", inputs.get(2).resolved());
    }

    public void testInputResolverMergesIncluded() {
        myFixture.addFileToProject("ir-merge/root.hcl", """
                locals {
                  region = "us-west-2"
                }
                
                inputs = {
                  region = local.region
                  team   = "platform"
                }
                """);
        var file = myFixture.addFileToProject("ir-merge/app/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                }
                
                inputs = {
                  region   = "us-east-1"
                  app_name = "api"
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var inputs = com.github.terrapaw.terragrunt.toolwindow.TerragruntInputResolver.resolveInputs(myFixture.getFile());
        // region overridden by current file, team from include, app_name from current
        var map = new java.util.LinkedHashMap<String, String>();
        for (var entry : inputs) map.put(entry.key(), entry.resolved());
        assertEquals("region should be overridden", "us-east-1", map.get("region"));
        assertEquals("team from include", "platform", map.get("team"));
        assertEquals("app_name from current", "api", map.get("app_name"));
    }

    public void testInputResolverDeepChainResolution() {
        // local.region → local.common.locals.region → resolves to "ap-southeast-2"
        var file = myFixture.addFileToProject("ir-chain/terragrunt.hcl", """
                locals {
                  region = "ap-southeast-2"
                  deeper = local.region
                }
                
                inputs = {
                  aws_region = local.deeper
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var inputs = com.github.terrapaw.terragrunt.toolwindow.TerragruntInputResolver.resolveInputs(myFixture.getFile());
        assertEquals(1, inputs.size());
        assertEquals("aws_region", inputs.get(0).key());
        assertEquals("ap-southeast-2", inputs.get(0).resolved());
    }

    public void testInputResolverInterpolatedString() {
        myFixture.addFileToProject("root.hcl", """
                locals {
                  deploy_env = "staging"
                }
                """);
        var file = myFixture.addFileToProject("svc/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                }
                
                inputs = {
                  bucket = "${include.root.locals.deploy_env}-my-bucket"
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var inputs = com.github.terrapaw.terragrunt.toolwindow.TerragruntInputResolver.resolveInputs(myFixture.getFile());
        assertEquals(1, inputs.size());
        assertEquals("staging-my-bucket", inputs.get(0).resolved());
    }

    public void testInputResolverDeepIncludeLocalsChain() {
        // Test that local.X chains resolve through multiple hops within same file
        var file = myFixture.addFileToProject("ir-deep2/terragrunt.hcl", """
                locals {
                  base_env = "production"
                  env      = local.base_env
                  prefix   = "${local.env}-app"
                }
                
                inputs = {
                  environment = local.env
                  bucket_name = local.prefix
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var inputs = com.github.terrapaw.terragrunt.toolwindow.TerragruntInputResolver.resolveInputs(myFixture.getFile());
        var map = new java.util.LinkedHashMap<String, String>();
        for (var entry : inputs) map.put(entry.key(), entry.resolved());
        assertEquals("production", map.get("environment"));
        assertEquals("production-app", map.get("bucket_name"));
    }

    public void testInputResolverDeepInterpolationMultipleRefs() {
        // local.abc resolves to an interpolated string that itself references other locals
        var file = myFixture.addFileToProject("ir-multi-interp/terragrunt.hcl", """
                locals {
                  env    = "prod"
                  region = "us-east-1"
                  prefix = "${local.env}-${local.region}"
                  name   = "${local.prefix}-bucket"
                }
                
                inputs = {
                  bucket = local.name
                  tag    = "${local.env}/${local.region}"
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var inputs = com.github.terrapaw.terragrunt.toolwindow.TerragruntInputResolver.resolveInputs(myFixture.getFile());
        var map = new java.util.LinkedHashMap<String, String>();
        for (var entry : inputs) map.put(entry.key(), entry.resolved());
        assertEquals("prod-us-east-1-bucket", map.get("bucket"));
        assertEquals("prod/us-east-1", map.get("tag"));
    }

    public void testInputResolverCrossFileDeepInterpolation() {
        // Locals in included file resolve to interpolated strings
        myFixture.addFileToProject("ir-xfile-interp/root.hcl", """
                locals {
                  env    = "prod"
                  region = "us-east-1"
                  prefix = "${local.env}-${local.region}"
                }
                """);
        var file = myFixture.addFileToProject("ir-xfile-interp/app/terragrunt.hcl", """
                include "common" {
                  path = "../root.hcl"
                }
                
                locals {
                  common = read_terragrunt_config("../root.hcl")
                  bucket = "${local.common.locals.prefix}-bucket"
                }
                
                inputs = {
                  bucket_name = local.bucket
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var inputs = com.github.terrapaw.terragrunt.toolwindow.TerragruntInputResolver.resolveInputs(myFixture.getFile());
        var map = new java.util.LinkedHashMap<String, String>();
        for (var entry : inputs) map.put(entry.key(), entry.resolved());
        assertEquals("prod-us-east-1-bucket", map.get("bucket_name"));
    }
}
