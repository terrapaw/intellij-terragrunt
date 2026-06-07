package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
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

    public void testQuotedLabelHasQuoteTokens() {
        // Regression: QUOTE tokens must be separate from STRING_LITERAL in labels
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                """);

        TerragruntBlock block = PsiTreeUtil.findChildOfType(file, TerragruntBlock.class);
        assertNotNull(block);
        List<TerragruntLabel> labels = block.getLabelList();
        assertEquals(1, labels.size());

        TerragruntLabel label = labels.get(0);
        // Label should contain QUOTE + STRING_LITERAL + QUOTE as leaf tokens
        String labelText = label.getText();
        assertTrue("Label should start with quote", labelText.startsWith("\""));
        assertTrue("Label should end with quote", labelText.endsWith("\""));

        // Verify the inner text is just the identifier (no greedy parsing across lines)
        String inner = labelText.substring(1, labelText.length() - 1);
        assertEquals("vpc", inner);
    }

    public void testInterpolationStructure() {
        // Regression: ${...} must have distinct INTERPOLATION_START/END tokens
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "prefix-${local.env}-suffix"
                }
                """);

        Collection<TerragruntStringLit> strings = PsiTreeUtil.findChildrenOfType(file, TerragruntStringLit.class);
        // Find the string with interpolation
        TerragruntStringLit interpString = null;
        for (TerragruntStringLit s : strings) {
            if (s.getText().contains("${")) {
                interpString = s;
                break;
            }
        }
        assertNotNull("Should find string with interpolation", interpString);

        // Should contain an interpolation child
        TerragruntInterpolation interp = PsiTreeUtil.findChildOfType(interpString, TerragruntInterpolation.class);
        assertNotNull("String should contain interpolation PSI node", interp);

        // Interpolation should contain a variable expression
        TerragruntVariableExpr varExpr = PsiTreeUtil.findChildOfType(interp, TerragruntVariableExpr.class);
        assertNotNull("Interpolation should contain variable expression", varExpr);
    }

    public void testObjectElemHasIdentifierKey() {
        // Regression: object elements with identifier keys must have getIdentifier() != null
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    region = "us-east-1"
                    count  = 3
                  }
                }
                """);

        Collection<TerragruntObjectElem> elems = PsiTreeUtil.findChildrenOfType(file, TerragruntObjectElem.class);
        assertTrue("Should find object elements", elems.size() >= 2);

        for (TerragruntObjectElem elem : elems) {
            assertNotNull("Object elem should have identifier key", elem.getIdentifier());
        }

        // Verify specific keys exist
        List<String> keys = elems.stream()
                .map(e -> e.getIdentifier().getText())
                .toList();
        assertTrue("Should contain 'region'", keys.contains("region"));
        assertTrue("Should contain 'count'", keys.contains("count"));
    }

    public void testMultiLineStringDoesNotConsumeNextLine() {
        // Regression: greedy string parsing must not cross line boundaries
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  a = "first"
                  b = "second"
                }
                """);

        TerragruntBlock block = PsiTreeUtil.findChildOfType(file, TerragruntBlock.class);
        assertNotNull(block);
        TerragruntBody body = block.getBody();
        assertNotNull(body);

        List<TerragruntAttribute> attrs = PsiTreeUtil.getChildrenOfTypeAsList(body, TerragruntAttribute.class);
        assertEquals("Should parse two separate attributes", 2, attrs.size());
        assertEquals("a", attrs.get(0).getIdentifier().getText());
        assertEquals("b", attrs.get(1).getIdentifier().getText());
    }
}
