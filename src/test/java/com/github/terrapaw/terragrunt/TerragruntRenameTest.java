package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.refactor.TerragruntNamesValidator;
import com.github.terrapaw.terragrunt.refactor.TerragruntRenameHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TerragruntRenameTest extends BasePlatformTestCase {

    public void testNamesValidatorAcceptsValidIdentifiers() {
        var validator = new TerragruntNamesValidator();
        assertTrue(validator.isIdentifier("app_name", null));
        assertTrue(validator.isIdentifier("vpc-id", null));
        assertTrue(validator.isIdentifier("_private", null));
        assertTrue(validator.isIdentifier("x", null));
    }

    public void testNamesValidatorRejectsInvalid() {
        var validator = new TerragruntNamesValidator();
        assertFalse(validator.isIdentifier("", null));
        assertFalse(validator.isIdentifier("123abc", null));
        assertFalse(validator.isIdentifier("true", null));
        assertFalse(validator.isIdentifier("null", null));
    }

    public void testRenameHandlerAvailableOnLocalDefinition() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  app_<caret>name = "my-app"
                }
                inputs = {
                  x = local.app_name
                }
                """);
        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on local definition", handler.isAvailableOnDataContext(ctx));
    }

    public void testRenameHandlerAvailableOnLocalUsage() {
        myFixture.configureByText("terragrunt.hcl", """
                locals {
                  app_name = "my-app"
                }
                inputs = {
                  x = local.app_<caret>name
                }
                """);
        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on local usage", handler.isAvailableOnDataContext(ctx));
    }

    public void testRenameHandlerNotAvailableOnRandomIdentifier() {
        myFixture.configureByText("terragrunt.hcl", """
                terraform {
                  sou<caret>rce = "test"
                }
                """);
        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertFalse("Rename should NOT be available on non-local identifier", handler.isAvailableOnDataContext(ctx));
    }

    public void testCrossFileRenameUpdatesIncludeLocalsUsage() {
        // root.hcl defines the local
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  region = "us-east-1"
                }
                """);

        // child references it via include.root.locals.region
        myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  r = include.root.locals.region
                }
                """);

        // Configure on root.hcl definition
        myFixture.configureFromExistingVirtualFile(rootFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("region");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on local definition", handler.isAvailableOnDataContext(ctx));

        // Perform the rename
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "region", "aws_region");

        // Verify the child file was updated
        var updatedChild = myFixture.findFileInTempDir("child/terragrunt.hcl");
        assertNotNull("Child file should exist", updatedChild);
        var psiChild = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedChild);
        assertNotNull(psiChild);
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiChild);
        assertNotNull(childDoc);
        String childText = childDoc.getText();
        assertTrue("Child should reference aws_region, got: " + childText,
                childText.contains("include.root.locals.aws_region"));
        assertFalse("Child should NOT still reference region",
                childText.contains("include.root.locals.region"));
    }

    public void testCrossFileRenameUpdatesAliasUsage() {
        // root.hcl defines the local
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  environment = "prod"
                }
                """);

        // child uses an alias: local.config.environment
        myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                  expose = true
                }
                
                locals {
                  config = include.root.locals
                }
                
                inputs = {
                  env = local.config.environment
                }
                """);

        // Configure on root.hcl definition
        myFixture.configureFromExistingVirtualFile(rootFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("environment");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "environment", "env_name");

        var updatedChild = myFixture.findFileInTempDir("child/terragrunt.hcl");
        var psiChild = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedChild);
        assertNotNull(psiChild);
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiChild);
        assertNotNull(childDoc);
        String childText = childDoc.getText();
        assertTrue("Child should reference env_name, got: " + childText,
                childText.contains("local.config.env_name"));
    }

    public void testCrossFileRenameUpdatesReadTerragruntConfigUsage() {
        // common.hcl defines locals
        var commonFile = myFixture.addFileToProject("common.hcl", """
                locals {
                  project_name = "my-app"
                }
                """);

        // child uses read_terragrunt_config: local.common.locals.project_name
        myFixture.addFileToProject("child/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common.hcl")
                }
                
                inputs = {
                  name = local.common.locals.project_name
                }
                """);

        myFixture.configureFromExistingVirtualFile(commonFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("project_name");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "project_name", "app_name");

        var updatedChild = myFixture.findFileInTempDir("child/terragrunt.hcl");
        var psiChild = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedChild);
        assertNotNull(psiChild);
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiChild);
        assertNotNull(childDoc);
        String childText = childDoc.getText();
        assertTrue("Child should reference app_name, got: " + childText,
                childText.contains("local.common.locals.app_name"));
    }

    public void testCrossFileRenameInputsKey() {
        // root.hcl defines inputs
        var rootFile = myFixture.addFileToProject("root.hcl", """
                inputs = {
                  default_tags = "test"
                }
                """);

        // child references include.root.inputs.default_tags
        myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  tags = include.root.inputs.default_tags
                }
                """);

        myFixture.configureFromExistingVirtualFile(rootFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("default_tags");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on inputs key", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "default_tags", "common_tags");

        // Verify definition was renamed
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(rootDoc);
        assertTrue("Root should have common_tags", rootDoc.getText().contains("common_tags"));

        // Verify cross-file usage was renamed
        var updatedChild = myFixture.findFileInTempDir("child/terragrunt.hcl");
        var psiChild = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedChild);
        assertNotNull(psiChild);
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiChild);
        assertNotNull(childDoc);
        String childText = childDoc.getText();
        assertTrue("Child should reference common_tags, got: " + childText,
                childText.contains("include.root.inputs.common_tags"));
    }

    public void testDeepKeyRenameSameFile() {
        // Rename "vpc_cidr" key inside locals object, updates local.network.vpc_cidr usage
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  network = {
                    vpc_cidr = "10.0.0.0/16"
                    az_count = 3
                  }
                }
                
                inputs = {
                  cidr = local.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("vpc_cidr");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on object key in locals", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_cidr", "cidr_block");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String text = doc.getText();
        assertTrue("Definition should be renamed, got: " + text, text.contains("cidr_block = \"10.0.0.0/16\""));
        assertTrue("Usage should be renamed, got: " + text, text.contains("local.network.cidr_block"));
        assertFalse("Old name should not remain", text.contains("vpc_cidr"));
    }

    public void testDeepKeyRenameCrossFile() {
        // Rename "vpc_cidr" in root.hcl, updates local.common.locals.network.vpc_cidr in child
        var commonFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  network = {
                    vpc_cidr = "10.0.0.0/16"
                  }
                }
                """);

        myFixture.addFileToProject("child/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../root.hcl")
                }
                
                inputs = {
                  cidr = local.common.locals.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(commonFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("vpc_cidr");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_cidr", "cidr_block");

        var updatedChild = myFixture.findFileInTempDir("child/terragrunt.hcl");
        var psiChild = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedChild);
        assertNotNull(psiChild);
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiChild);
        assertNotNull(childDoc);
        String childText = childDoc.getText();
        assertTrue("Child should reference cidr_block, got: " + childText,
                childText.contains("local.common.locals.network.cidr_block"));
    }

    public void testDeepKeyRenameQuotedKey() {
        // Rename quoted key "vpc_cidr" inside locals object
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  network = {
                    "vpc_cidr" = "10.0.0.0/16"
                    az_count = 3
                  }
                }
                
                inputs = {
                  cidr = local.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        // Place caret inside the quoted key content (on the STRING_LITERAL "vpc_cidr")
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("vpc_cidr") + 1; // inside the STRING_LITERAL
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on quoted object key", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_cidr", "cidr_block");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String result = doc.getText();
        assertTrue("Definition should be renamed, got: " + result, result.contains("\"cidr_block\""));
        assertTrue("Usage should be renamed, got: " + result, result.contains("local.network.cidr_block"));
    }

    public void testDeepKeyRenameNestedAlias() {
        // Playground scenario: root.hcl has a = { b = { c = "abc" } }
        // test.hcl: a = local.abc.locals.a (alias chain)
        // top.hcl: d = local.read.locals.a.b.c
        // Renaming "c" in root.hcl should update top.hcl
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  a = {
                    b = {
                      c = "abc"
                    }
                  }
                }
                """);
        myFixture.addFileToProject("mid/root.hcl", """
                locals {
                  abc = read_terragrunt_config("../root.hcl")
                  a = local.abc.locals.a
                }
                """);
        myFixture.addFileToProject("top/terragrunt.hcl", """
                locals {
                  read = read_terragrunt_config("../mid/root.hcl")
                  d = local.read.locals.a.b.c
                }
                """);

        myFixture.configureFromExistingVirtualFile(rootFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("c = \"abc\"");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on nested object key", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "c", "renamed_c");

        // Verify top.hcl was updated
        var updatedTop = myFixture.findFileInTempDir("top/terragrunt.hcl");
        var psiTop = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedTop);
        assertNotNull(psiTop);
        var topDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiTop);
        assertNotNull(topDoc);
        String topText = topDoc.getText();
        assertTrue("Top should reference renamed_c, got: " + topText,
                topText.contains("local.read.locals.a.b.renamed_c"));
    }

    public void testDeepKeyRenameFromUsageSameFile() {
        // Rename from the usage side: cursor on "vpc_cidr" in local.network.vpc_cidr
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  network = {
                    vpc_cidr = "10.0.0.0/16"
                  }
                }
                
                inputs = {
                  cidr = local.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        // Place cursor on vpc_cidr in the usage (local.network.vpc_cidr)
        int offset = text.indexOf("local.network.vpc_cidr") + "local.network.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available from usage", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_cidr", "cidr_block");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String result = doc.getText();
        assertTrue("Definition should be renamed, got: " + result, result.contains("cidr_block = \"10.0.0.0/16\""));
        assertTrue("Usage should be renamed, got: " + result, result.contains("local.network.cidr_block"));
    }

    public void testDeepKeyRenameFromUsageCrossFile() {
        // Rename from top.hcl (usage side) should update root.hcl (definition)
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  a = {
                    b = {
                      c = "abc"
                    }
                  }
                }
                """);
        myFixture.addFileToProject("mid/root.hcl", """
                locals {
                  abc = read_terragrunt_config("../root.hcl")
                  a = local.abc.locals.a
                }
                """);
        var topFile = myFixture.addFileToProject("top/terragrunt.hcl", """
                locals {
                  read = read_terragrunt_config("../mid/root.hcl")
                  d = local.read.locals.a.b.c
                }
                """);

        myFixture.configureFromExistingVirtualFile(topFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        // Place cursor on "c" in local.read.locals.a.b.c
        int offset = text.indexOf("a.b.c") + 4; // on "c"
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available from cross-file usage", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "c", "renamed_c");

        // Verify usage in top.hcl was updated
        var topDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(topDoc);
        assertTrue("Top should use renamed_c", topDoc.getText().contains("local.read.locals.a.b.renamed_c"));

        // Verify definition in root.hcl was updated
        var updatedRoot = myFixture.findFileInTempDir("root.hcl");
        var psiRoot = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedRoot);
        assertNotNull(psiRoot);
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiRoot);
        assertNotNull(rootDoc);
        assertTrue("Root should have renamed_c, got: " + rootDoc.getText(),
                rootDoc.getText().contains("renamed_c = \"abc\""));
    }

    public void testRenameLocalFromUsageAttrLevel() {
        // Rename "region" from local.config.locals.region (cursor on "region" at idx 2)
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  region = "us-east-1"
                }
                """);
        var childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                locals {
                  config = read_terragrunt_config("../root.hcl")
                }
                
                inputs = {
                  r = local.config.locals.region
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.region") + "locals.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "region", "aws_region");

        // Child should be updated
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(childDoc);
        assertTrue("Child should use aws_region", childDoc.getText().contains("local.config.locals.aws_region"));

        // Root should be updated
        var updatedRoot = myFixture.findFileInTempDir("root.hcl");
        var psiRoot = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedRoot);
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiRoot);
        assertNotNull(rootDoc);
        assertTrue("Root should have aws_region, got: " + rootDoc.getText(),
                rootDoc.getText().contains("aws_region = \"us-east-1\""));
    }

    public void testRenameInputsKeyFromUsage() {
        // Rename "env" from include.root.inputs.env (cursor on "env" at idx 2)
        var rootFile = myFixture.addFileToProject("root.hcl", """
                inputs = {
                  env = "prod"
                }
                """);
        var childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  e = include.root.inputs.env
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("inputs.env") + "inputs.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on inputs usage", handler.isAvailableOnDataContext(ctx));
    }
}
