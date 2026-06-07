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

    public void testFeatureValueNavigation() {
        myFixture.configureByText("terragrunt.hcl", """
                feature "multi_az" {
                  default = false
                }
                inputs = {
                  az = feature.multi_az.<caret>value
                }
                """);
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should navigate feature.multi_az.value to default", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("Should land on 'default'", "default", targets[0].getText());
    }

    public void testFeatureFindUsagesFromLabel() {
        myFixture.configureByText("terragrunt.hcl", """
                feature "multi_<caret>az" {
                  default = false
                }
                inputs = {
                  az = feature.multi_az.value
                  is_multi = feature.multi_az.value
                }
                """);
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should find usages of feature.multi_az", targets);
        assertEquals("Should find 2 usages", 2, targets.length);
    }

    public void testDependencyOutputsNavigation() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                  mock_outputs = {
                    vpc_id          = "vpc-123"
                    private_subnets = ["subnet-1"]
                  }
                }
                inputs = {
                  id = dependency.vpc.outputs.vpc_<caret>id
                }
                """);
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should navigate dependency.vpc.outputs.vpc_id to mock_outputs", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("Should land on the exact key", "vpc_id", targets[0].getText());
    }

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

    public void testNavigateIntoObjectKeys() {
        // local.config.a.b should navigate to key "b" inside the nested object
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    a = {
                      b = "value"
                    }
                  }
                }

                inputs = {
                  x = local.config.a.b
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.lastIndexOf(".b") + 1; // on "b"
        PsiElement element = myFixture.getFile().findElementAt(offset);

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate to 'b' key in nested object", targets);
        assertTrue("Should find target", targets.length > 0);
        assertEquals("b", targets[0].getText());
    }

    public void testNavigateIntoObjectKeysFirstLevel() {
        // local.config.a should navigate to key "a"
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    a = {
                      b = "value"
                    }
                  }
                }

                inputs = {
                  x = local.config.a.b
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.lastIndexOf(".a.") + 1; // on "a"
        PsiElement element = myFixture.getFile().findElementAt(offset);

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate to 'a' key in object", targets);
        assertTrue("Should find target", targets.length > 0);
        assertEquals("a", targets[0].getText());
    }

    public void testNoNavigationIntoNonObjectLocal() {
        // local.name.something should not navigate when name is a string, not an object
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  name = "hello"
                }

                inputs = {
                  x = local.name.something
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf(".something") + 1;
        PsiElement element = myFixture.getFile().findElementAt(offset);

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNull("Should NOT navigate into a non-object local", targets);
    }

    public void testNavigateIntoQuotedObjectKey() {
        // local.config.vpc_cidr should navigate to quoted key "vpc_cidr"
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    "vpc_cidr" = "10.0.0.0/16"
                  }
                }

                inputs = {
                  x = local.config.vpc_cidr
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.lastIndexOf("vpc_cidr");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate to quoted key 'vpc_cidr'", targets);
        assertTrue("Should find target", targets.length > 0);
    }

    public void testNavigateIntoObjectKeysArbitraryDepth() {
        // local.config.a.b.c should navigate to key "c" three levels deep
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    a = {
                      b = {
                        c = "deep"
                      }
                    }
                  }
                }

                inputs = {
                  x = local.config.a.b.c
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.lastIndexOf(".c") + 1;
        PsiElement element = myFixture.getFile().findElementAt(offset);

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate to 'c' key three levels deep", targets);
        assertTrue("Should find target", targets.length > 0);
        assertEquals("c", targets[0].getText());
    }

    public void testFindUsagesFromObjectKey() {
        // Ctrl+B on "vpc_cidr" in the object definition should find local.config.vpc_cidr usages
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    vpc_cidr = "10.0.0.0/16"
                  }
                }

                inputs = {
                  cidr = local.config.vpc_cidr
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("vpc_cidr"); // the definition key
        PsiElement element = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at offset", element);
        assertEquals("vpc_cidr", element.getText());

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should find usages of object key", targets);
        assertTrue("Should find at least one usage", targets.length > 0);
    }

    public void testFindUsagesFromQuotedObjectKey() {
        // Ctrl+B on the content of "vpc_cidr" (quoted key) should find usages
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  config = {
                    "vpc_cidr" = "10.0.0.0/16"
                  }
                }

                inputs = {
                  cidr = local.config.vpc_cidr
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        // Find the first vpc_cidr which is inside the quoted key definition
        int offset = text.indexOf("vpc_cidr");
        PsiElement element = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element at offset", element);

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should find usages from quoted object key", targets);
        assertTrue("Should find at least one usage", targets.length > 0);
    }

    public void testFeatureValueNavigatesToCorrectBlock() {
        // Bug: findFeatureDefault matched the FIRST feature block regardless of label
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                feature "wrong" {
                  default = false
                }

                feature "correct" {
                  default = true
                }

                locals {
                  val = feature.correct.value
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        // Cursor on "value" in feature.correct.value
        int offset = text.indexOf("feature.correct.value") + "feature.correct.".length();
        PsiElement element = file.findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate to default", targets);
        assertTrue(targets.length > 0);
        // The target should be in the "correct" block (default = true), not "wrong" block (default = false)
        String targetContext = targets[0].getParent().getText();
        assertTrue("Should navigate to correct feature block, got: " + targetContext,
                targetContext.contains("true"));
    }

    public void testMockOutputNavigatesToCorrectDependency() {
        // Bug: findMockOutputKey matched the FIRST dependency block regardless of label
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                dependency "wrong" {
                  config_path = "../wrong"
                  mock_outputs = {
                    id = "wrong-id"
                  }
                }

                dependency "correct" {
                  config_path = "../correct"
                  mock_outputs = {
                    id = "correct-id"
                  }
                }

                inputs = {
                  val = dependency.correct.outputs.id
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("dependency.correct.outputs.id") + "dependency.correct.outputs.".length();
        PsiElement element = file.findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate to mock output key", targets);
        assertTrue(targets.length > 0);
        // Should be in the "correct" dependency, not "wrong"
        String context = targets[0].getParent().getText();
        assertTrue("Should navigate to correct dependency's mock_outputs, got: " + context,
                context.contains("correct-id"));
    }
}
