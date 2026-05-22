package com.github.joelm.terragrunt;

import com.github.joelm.terragrunt.lang.TerragruntFileType;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntFileTypeTest extends BasePlatformTestCase {

    public void testTerragruntHclDetectedByName() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", "locals { x = 1 }");
        assertEquals("terragrunt.hcl should be Terragrunt file type",
                TerragruntFileType.INSTANCE, file.getFileType());
    }

    public void testRootHclDetectedByName() {
        PsiFile file = myFixture.configureByText("root.hcl", "remote_state { backend = \"s3\" }");
        assertEquals("root.hcl should be Terragrunt file type",
                TerragruntFileType.INSTANCE, file.getFileType());
    }

    public void testTerragruntStackHclDetectedByName() {
        PsiFile file = myFixture.configureByText("terragrunt.stack.hcl", "unit \"vpc\" { source = \"x\" }");
        assertEquals("terragrunt.stack.hcl should be Terragrunt file type",
                TerragruntFileType.INSTANCE, file.getFileType());
    }
}
