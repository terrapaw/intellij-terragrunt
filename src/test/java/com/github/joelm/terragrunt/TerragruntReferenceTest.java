package com.github.joelm.terragrunt;

import com.github.joelm.terragrunt.lang.psi.TerragruntBlock;
import com.github.joelm.terragrunt.reference.TerragruntGotoDeclarationHandler;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntReferenceTest extends BasePlatformTestCase {

    private final TerragruntGotoDeclarationHandler handler = new TerragruntGotoDeclarationHandler();

    public void testLocalVariableGotoDeclaration() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  app_name = "my-app"
                }
                
                inputs = {
                  name = local.app_<caret>name
                }
                """);
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        assertNotNull(elementAtCaret);
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should find goto targets for local.app_name", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testDependencyGotoDeclaration() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                inputs = {
                  id = dependency.<caret>vpc.outputs.vpc_id
                }
                """);
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        assertNotNull(elementAtCaret);
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should find goto targets for dependency.vpc", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertTrue("Target should be a block", targets[0] instanceof TerragruntBlock);
    }

    public void testFeatureGotoDeclaration() {
        myFixture.configureByText("terragrunt.hcl", """
                feature "multi_az" {
                  default = false
                }
                
                inputs = {
                  az = feature.<caret>multi_az.value
                }
                """);
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        assertNotNull(elementAtCaret);
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should find goto targets for feature.multi_az", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testUnresolvedLocalNoTargets() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "test"
                }
                
                inputs = {
                  x = local.<caret>nonexistent
                }
                """);
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        assertNotNull(elementAtCaret);
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, myFixture.getCaretOffset(), myFixture.getEditor());
        assertTrue("Should have no targets for nonexistent local",
                targets == null || targets.length == 0);
    }
}
