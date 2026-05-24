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
}
