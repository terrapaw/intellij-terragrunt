package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
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

    public void testHclFileInTerragruntProjectDetected() {
        // Create a root.hcl sibling so the directory is recognized as a Terragrunt project
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        PsiFile envFile = myFixture.addFileToProject("env.hcl", "locals { environment = \"dev\" }");
        assertEquals("env.hcl next to root.hcl should be Terragrunt file type",
                TerragruntFileType.INSTANCE, envFile.getFileType());
    }

    public void testHclFileWithTerragruntContentDetected() {
        PsiFile file = myFixture.configureByText("custom.hcl", "include \"root\" { path = \"x\" }");
        assertEquals("HCL file with include block should be Terragrunt",
                TerragruntFileType.INSTANCE, file.getFileType());
    }

    public void testHclFileDetectedByOverriderNearTerragruntFiles() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        PsiFile customFile = myFixture.addFileToProject("custom-config.hcl", "locals { env = \"dev\" }");
        assertEquals("custom-config.hcl near root.hcl should be Terragrunt",
                TerragruntFileType.INSTANCE, customFile.getFileType());
    }

    public void testOverriderDoesNotCrashOnDeletedParentDirectory() throws Exception {
        // Simulate a file whose parent directory gets deleted (stale VFS reference)
        PsiFile file = myFixture.addFileToProject("subdir/test.hcl", "locals { x = 1 }");
        com.intellij.openapi.vfs.VirtualFile vFile = file.getVirtualFile();
        com.intellij.openapi.vfs.VirtualFile dir = vFile.getParent();

        // Delete the parent directory
        com.intellij.openapi.application.WriteAction.runAndWait(() -> dir.delete(this));

        // Should not throw InvalidVirtualFileAccessException
        com.github.terrapaw.terragrunt.lang.TerragruntFileTypeOverrider overrider =
                new com.github.terrapaw.terragrunt.lang.TerragruntFileTypeOverrider();
        overrider.getOverriddenFileType(vFile);
    }
}
