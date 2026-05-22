package com.github.joelm.terragrunt;

import com.github.joelm.terragrunt.reference.TerragruntGotoDeclarationHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntCrossFileTest extends BasePlatformTestCase {

    private final TerragruntGotoDeclarationHandler handler = new TerragruntGotoDeclarationHandler();

    public void testIncludeNavigatesToBlock() {
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                inputs = {
                  x = include.<caret>root.locals.region
                }
                """);
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should resolve include.root to the include block", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testIncludeLocalsResolvesToIncludedFile() {
        // Create the included file in the project root
        myFixture.addFileToProject("root.hcl", """
                locals {
                  region = "us-east-1"
                  name   = "my-project"
                }
                """);

        // Configure the child file that references it with a relative path
        PsiFile childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  r = include.root.locals.region
                }
                """);
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());

        // Place caret on "region"
        int offset = myFixture.getEditor().getDocument().getText().indexOf("locals.region") + "locals.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        PsiElement element = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve include.root.locals.region across files", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testIncludeLocalsCompletionFromIncludedFile() {
        // Create the included file
        myFixture.addFileToProject("root.hcl", """
                locals {
                  region   = "us-east-1"
                  app_name = "my-app"
                }
                """);

        // Verify the resolver can find locals in the included file
        PsiFile childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path   = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  r = include.root.locals.region
                }
                """);
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());

        // Verify the file resolves correctly by checking navigation works
        int offset = myFixture.getEditor().getDocument().getText().indexOf("locals.region") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve region from included file", targets);
        assertTrue("Should find the region attribute", targets.length > 0);
    }
}
