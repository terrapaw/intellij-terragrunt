package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.inspection.TerragruntMissingAttributeInspection;
import com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedVariableInspection;
import com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntCoreUxTest extends BasePlatformTestCase {

    private final TerragruntGotoDeclarationHandler handler = new TerragruntGotoDeclarationHandler();

    // --- Find usages from definition ---

    public void testCtrlBOnLocalDefinitionFindsUsages() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  app_<caret>name = "my-app"
                }
                
                inputs = {
                  name = local.app_name
                  title = local.app_name
                }
                """);
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should find usages of app_name", targets);
        assertEquals("Should find 2 usages", 2, targets.length);
    }

    public void testCtrlBOnLocalDefinitionNoUsages() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  unused_<caret>var = "test"
                }
                
                inputs = {
                  x = "something"
                }
                """);
        PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(elementAtCaret, myFixture.getCaretOffset(), myFixture.getEditor());
        assertTrue("Should have no targets for unused var", targets == null || targets.length == 0);
    }

    // --- Unresolved variable inspection ---

    public void testUnresolvedLocalVariable() {
        myFixture.enableInspections(new TerragruntUnresolvedVariableInspection());
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "test"
                }
                
                inputs = {
                  x = local.nonexistent
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unresolved local variable 'nonexistent'"))
                .count();
        assertEquals("Should detect unresolved local", 1, warnings);
    }

    public void testResolvedLocalVariableNoWarning() {
        myFixture.enableInspections(new TerragruntUnresolvedVariableInspection());
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "test"
                }
                
                inputs = {
                  x = local.name
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unresolved"))
                .count();
        assertEquals("Should have no unresolved warnings", 0, warnings);
    }

    public void testUnresolvedDependency() {
        myFixture.enableInspections(new TerragruntUnresolvedVariableInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                inputs = {
                  x = dependency.rds.outputs.id
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unresolved dependency 'rds'"))
                .count();
        assertEquals("Should detect unresolved dependency", 1, warnings);
    }

    public void testUnresolvedFeature() {
        myFixture.enableInspections(new TerragruntUnresolvedVariableInspection());
        myFixture.configureByText("terragrunt.hcl", """
                feature "flag_a" {
                  default = true
                }
                
                inputs = {
                  x = feature.flag_b.value
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unresolved feature 'flag_b'"))
                .count();
        assertEquals("Should detect unresolved feature", 1, warnings);
    }

    // --- Quick-fix for missing attributes ---

    public void testQuickFixInsertsMissingAttribute() {
        myFixture.enableInspections(new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  skip_outputs = true
                }
                """);
        myFixture.doHighlighting();
        var intentions = myFixture.getAllQuickFixes();
        assertFalse("Should have quick-fixes available", intentions.isEmpty());
        myFixture.launchAction(intentions.get(0));
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue("Should have inserted config_path, got: " + text, text.contains("config_path"));
    }

    public void testQuickFixInsertsMultipleMissingAttributes() {
        myFixture.enableInspections(new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                generate "provider" {
                  comment_prefix = "#"
                }
                """);
        myFixture.doHighlighting();
        var intentions = myFixture.getAllQuickFixes();
        assertFalse("Should have quick-fixes available", intentions.isEmpty());
        myFixture.launchAction(intentions.get(0));
        String text = myFixture.getEditor().getDocument().getText();
        assertTrue("Should have inserted path, got: " + text, text.contains("path"));
        assertTrue("Should have inserted if_exists", text.contains("if_exists"));
        assertTrue("Should have inserted contents", text.contains("contents"));
    }
}
