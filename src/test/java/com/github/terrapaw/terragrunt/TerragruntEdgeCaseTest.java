package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.inspection.TerragruntMissingAttributeInspection;
import com.github.terrapaw.terragrunt.inspection.TerragruntUnknownBlockInspection;
import com.github.terrapaw.terragrunt.inspection.TerragruntUnresolvedVariableInspection;
import com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;
import java.util.List;

/**
 * Edge case tests to catch potential regressions.
 */
public class TerragruntEdgeCaseTest extends BasePlatformTestCase {

    private final TerragruntGotoDeclarationHandler handler = new TerragruntGotoDeclarationHandler();

    // --- Empty/minimal files ---

    public void testEmptyFileNoErrors() {
        myFixture.enableInspections(new TerragruntUnknownBlockInspection(), new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", "");
        var highlights = myFixture.doHighlighting();
        long errors = highlights.stream()
                .filter(h -> h.getDescription() != null)
                .count();
        assertEquals("Empty file should have no inspection errors", 0, errors);
    }

    public void testOnlyCommentsNoErrors() {
        myFixture.configureByText("terragrunt.hcl", """
                # This is a comment
                // Another comment
                /* Block comment */
                """);
        assertNoParseErrors(myFixture.getFile());
    }

    // --- Blocks with no body content ---

    public void testEmptyDependencyBlockReportsMissing() {
        myFixture.enableInspections(new TerragruntMissingAttributeInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "empty" {
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Missing required"))
                .count();
        assertTrue("Empty dependency should report missing config_path", warnings > 0);
    }

    // --- Deeply nested expressions ---

    public void testTripleNestedFunctionCalls() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = format("%s/%s", basename(get_terragrunt_dir()), "suffix")
                }
                """);
        assertNoParseErrors(file);
    }

    public void testTernaryInsideList() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  subnets = true ? ["a", "b"] : []
                }
                """);
        assertNoParseErrors(file);
    }

    public void testMapInsideTernary() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                inputs = {
                  config = true ? { key = "val" } : {}
                }
                """);
        assertNoParseErrors(file);
    }

    // --- String edge cases ---

    public void testEmptyInterpolation() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = "${}"
                }
                """);
        // May or may not parse - just shouldn't crash
        assertNotNull(file);
    }

    public void testMultipleInterpolationsInOneString() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = "${local.a}-${local.b}-${local.c}"
                }
                """);
        assertNoParseErrors(file);
    }

    public void testStringWithOnlyInterpolation() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = "${local.name}"
                }
                """);
        assertNoParseErrors(file);
    }

    // --- Completion edge cases ---

    public void testCompletionInEmptyFile() {
        myFixture.configureByText("terragrunt.hcl", "<caret>");
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions in empty file", completions);
        assertTrue("Should have block suggestions", completions.length > 0);
    }

    public void testCompletionAfterEquals() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = <caret>
                }
                """);
        var completions = myFixture.completeBasic();
        assertNotNull("Should offer completions after =", completions);
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest functions", names.contains("get_env"));
        assertTrue("Should suggest local prefix", names.contains("local"));
    }

    public void testDotCompletionOnNonexistentLocal() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = local.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        // Should not crash, may return empty since no locals defined yet
        // (the local being defined is 'x' itself which isn't complete)
        assertNotNull("Should not crash on self-referencing local", completions);
    }

    // --- Navigation edge cases ---

    public void testCtrlBOnNonLocalIdentifier() {
        myFixture.configureByText("terragrunt.hcl", """
                terraform {
                  sou<caret>rce = "test"
                }
                """);
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNull("Should return null for non-navigable identifiers", targets);
    }

    public void testCtrlBOnKeyword() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  x = tr<caret>ue
                }
                """);
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNull("Should return null for keywords", targets);
    }

    // --- Inspection edge cases ---

    public void testNoFalsePositiveOnLocalsBlock() {
        myFixture.enableInspections(new TerragruntUnknownBlockInspection());
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  anything = "valid"
                  whatever = 42
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown"))
                .count();
        assertEquals("locals block should never be flagged as unknown", 0, warnings);
    }

    public void testUnresolvedVariableNotTriggeredForDependencyOutputs() {
        myFixture.enableInspections(new TerragruntUnresolvedVariableInspection());
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                inputs = {
                  id = dependency.vpc.outputs.vpc_id
                }
                """);
        var highlights = myFixture.doHighlighting();
        long warnings = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unresolved"))
                .count();
        assertEquals("dependency.vpc should not trigger unresolved warning", 0, warnings);
    }

    // --- Multiple blocks of same type ---

    public void testMultipleDependenciesCompletion() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                dependency "rds" {
                  config_path = "../rds"
                }
                dependency "redis" {
                  config_path = "../redis"
                }
                inputs = {
                  x = dependency.<caret>
                }
                """);
        var completions = myFixture.completeBasic();
        assertNotNull(completions);
        List<String> names = List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest vpc", names.contains("vpc"));
        assertTrue("Should suggest rds", names.contains("rds"));
        assertTrue("Should suggest redis", names.contains("redis"));
    }

    private void assertNoParseErrors(PsiFile file) {
        Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Parse errors:\n");
            for (PsiErrorElement e : errors) {
                sb.append("  - ").append(e.getErrorDescription()).append(" at ").append(e.getTextOffset()).append("\n");
            }
            fail(sb.toString());
        }
    }
}
