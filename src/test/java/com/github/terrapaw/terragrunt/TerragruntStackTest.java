package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;

public class TerragruntStackTest extends BasePlatformTestCase {

    private final TerragruntGotoDeclarationHandler handler = new TerragruntGotoDeclarationHandler();

    public void testUnitBlockParses() {
        PsiFile file = myFixture.configureByText("terragrunt.stack.hcl", """
                unit "vpc" {
                  source = "git::git@github.com:acme/modules.git//vpc?ref=v1.0"
                  path   = "vpc"
                  values = {
                    vpc_name = "main"
                    cidr     = "10.0.0.0/16"
                  }
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        assertEquals("unit", blocks.iterator().next().getIdentifier().getText());
    }

    public void testStackBlockParses() {
        PsiFile file = myFixture.configureByText("terragrunt.stack.hcl", """
                stack "services" {
                  source = "github.com/acme/stacks//services?ref=v1.0"
                  path   = "services"
                  values = {
                    project = "dev-services"
                  }
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(1, blocks.size());
        assertEquals("stack", blocks.iterator().next().getIdentifier().getText());
    }

    public void testMultipleUnitsAndStacks() {
        PsiFile file = myFixture.configureByText("terragrunt.stack.hcl", """
                unit "vpc" {
                  source = "modules//vpc"
                  path   = "vpc"
                  values = {
                    name = "main"
                  }
                }
                
                unit "app" {
                  source = "modules//app"
                  path   = "app"
                  values = {
                    name = "my-app"
                  }
                }
                
                stack "monitoring" {
                  source = "stacks//monitoring"
                  path   = "monitoring"
                  values = {
                    enabled = true
                  }
                }
                """);
        assertNoParseErrors(file);
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(file, TerragruntBlock.class);
        assertEquals(3, blocks.size());
    }

    public void testUnitWithNoDotTerragruntStack() {
        PsiFile file = myFixture.configureByText("terragrunt.stack.hcl", """
                unit "vpc" {
                  source                 = "modules//vpc"
                  path                   = "vpc"
                  no_dot_terragrunt_stack = true
                  no_validation          = false
                  values = {
                    name = "main"
                  }
                }
                """);
        assertNoParseErrors(file);
    }

    public void testValuesReferenceParses() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  vpc_name = values.vpc_name
                  cidr     = values.cidr
                }
                """);
        assertNoParseErrors(file);
    }

    public void testValuesInExpressionCompletion() {
        myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  x = <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'values' prefix", names.contains("values"));
    }

    public void testUnitBlockCompletion() {
        myFixture.configureByText("terragrunt.stack.hcl", """
                unit "vpc" {
                  <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'source'", names.contains("source"));
        assertTrue("Should suggest 'path'", names.contains("path"));
        assertTrue("Should suggest 'values'", names.contains("values"));
        assertTrue("Should suggest 'no_dot_terragrunt_stack'", names.contains("no_dot_terragrunt_stack"));
    }

    public void testStackBlockCompletion() {
        myFixture.configureByText("terragrunt.stack.hcl", """
                stack "monitoring" {
                  <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        java.util.List<String> names = java.util.List.of(completions).stream()
                .map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'source'", names.contains("source"));
        assertTrue("Should suggest 'path'", names.contains("path"));
        assertTrue("Should suggest 'values'", names.contains("values"));
    }

    public void testNoUnresolvedWarningForValues() {
        myFixture.enableInspections(new com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedVariableInspection());
        myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  name = values.vpc_name
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unresolved"))
                .count();
        assertEquals("Should NOT warn about values.X (cross-file)", 0, warnings);
    }

    private void assertNoParseErrors(PsiFile file) {
        Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Parse errors found:\n");
            for (PsiErrorElement error : errors) {
                sb.append("  - ").append(error.getErrorDescription())
                        .append(" at offset ").append(error.getTextOffset()).append("\n");
            }
            fail(sb.toString());
        }
    }

    public void testValuesNavigatesToStackDefinition() {
        // Stack file defines a unit with values
        myFixture.addFileToProject("terragrunt.stack.hcl", """
                unit "app" {
                  source = "../modules/app"
                  path   = "app"
                  values = {
                    environment = "prod"
                    region      = "us-east-1"
                  }
                }
                """);

        // Unit file in .terragrunt-stack/app/ references values.environment
        PsiFile unitFile = myFixture.addFileToProject(".terragrunt-stack/app/terragrunt.hcl", """
                inputs = {
                  env = values.environment
                }
                """);
        myFixture.configureFromExistingVirtualFile(unitFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("environment");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate to values definition in stack file", targets);
        assertTrue("Should find target", targets.length > 0);
        assertFalse("Target should be in stack file",
                targets[0].getContainingFile().getName().equals("terragrunt.hcl"));
    }

    public void testValuesFindUsagesFromDefinition() {
        // Stack file defines a unit with values
        PsiFile stackFile = myFixture.addFileToProject("vfu-stack/terragrunt.stack.hcl", """
                unit "api" {
                  source = "../modules/api"
                  path   = "api"
                  values = {
                    environment = "prod"
                  }
                }
                """);

        // Unit file in .terragrunt-stack/api/ references values.environment
        myFixture.addFileToProject("vfu-stack/.terragrunt-stack/api/terragrunt.hcl", """
                inputs = {
                  env = values.environment
                }
                """);

        // Ctrl+B on "environment" in the stack file's values definition
        myFixture.configureFromExistingVirtualFile(stackFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("environment");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should find usages in unit file", targets);
        assertTrue("Should find at least one usage", targets.length > 0);
    }
}
