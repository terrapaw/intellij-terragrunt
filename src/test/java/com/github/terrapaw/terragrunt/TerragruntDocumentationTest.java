package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.editor.TerragruntDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntDocumentationTest extends BasePlatformTestCase {

    private final TerragruntDocumentationProvider provider = new TerragruntDocumentationProvider();

    public void testDocForTerragruntFunction() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = find_in_parent_<caret>folders("root.hcl")
                }
                """);
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        String doc = provider.generateDoc(element, element);
        assertNotNull("Should return documentation for find_in_parent_folders", doc);
        assertTrue("Should contain function name", doc.contains("find_in_parent_folders"));
        assertTrue("Should contain signature", doc.contains("name?"));
    }

    public void testDocForGetEnv() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = get_<caret>env("VAR", "default")
                }
                """);
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        String doc = provider.generateDoc(element, element);
        assertNotNull("Should return documentation for get_env", doc);
        assertTrue("Should contain get_env", doc.contains("get_env"));
    }

    public void testDocForTerraformBuiltin() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = json<caret>encode({})
                }
                """);
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        String doc = provider.generateDoc(element, element);
        assertNotNull("Should return documentation for jsonencode", doc);
        assertTrue("Should contain jsonencode", doc.contains("jsonencode"));
    }

    public void testNoDocForRegularIdentifier() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  my_<caret>var = "test"
                }
                """);
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        String doc = provider.generateDoc(element, element);
        assertNull("Should NOT return documentation for regular identifiers", doc);
    }

    public void testQuickNavigateInfo() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = get_repo_<caret>root()
                }
                """);
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        String info = provider.getQuickNavigateInfo(element, element);
        assertNotNull("Should return quick navigate info", info);
        assertTrue("Should contain signature", info.contains("get_repo_root"));
    }
}
