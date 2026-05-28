package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;
import java.util.List;

public class TerragruntPsiDebugTest extends BasePlatformTestCase {

    public void testIncludeBlockStructure() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                """);

        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals("Should find 1 block", 1, blocks.size());

        TerragruntBlock block = blocks.iterator().next();
        assertEquals("Block name should be 'include'", "include", block.getIdentifier().getText());

        TerragruntBody body = block.getBody();
        assertNotNull("Block should have a body", body);

        List<TerragruntAttribute> directAttrs = PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class);
        assertTrue("Should have at least one attribute", directAttrs.size() > 0);
    }
}
