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
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "env", "environment");

        // Child usage should be updated
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(childDoc);
        assertTrue("Child should use environment", childDoc.getText().contains("include.root.inputs.environment"));

        // Root definition should be updated
        var updatedRoot = myFixture.findFileInTempDir("root.hcl");
        var psiRoot = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedRoot);
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiRoot);
        assertNotNull(rootDoc);
        assertTrue("Root should have environment, got: " + rootDoc.getText(),
                rootDoc.getText().contains("environment = \"prod\""));
    }

    public void testDeepKeyRenameWithNestedFolderStructure() {
        // Realistic folder structure: project/common/root.hcl referenced from project/envs/dev/terragrunt.hcl
        var commonFile = myFixture.addFileToProject("project/common/root.hcl", """
                locals {
                  settings = {
                    log_level = "info"
                  }
                }
                """);
        myFixture.addFileToProject("project/envs/dev/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../../common/root.hcl")
                }
                
                inputs = {
                  log = local.common.locals.settings.log_level
                }
                """);

        // Rename from definition side
        myFixture.configureFromExistingVirtualFile(commonFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("log_level");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "log_level", "verbosity");

        var updatedChild = myFixture.findFileInTempDir("project/envs/dev/terragrunt.hcl");
        var psiChild = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedChild);
        assertNotNull(psiChild);
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiChild);
        assertNotNull(childDoc);
        assertTrue("Child should reference verbosity, got: " + childDoc.getText(),
                childDoc.getText().contains("local.common.locals.settings.verbosity"));
    }

    public void testRenameMockOutputKeyFromDefinition() {
        // Rename "vpc_id" in mock_outputs should update dependency.vpc.outputs.vpc_id
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                  mock_outputs = {
                    vpc_id = "mock-vpc-123"
                    subnet_ids = []
                  }
                }
                
                inputs = {
                  vpc = dependency.vpc.outputs.vpc_id
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("vpc_id = \"mock");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on mock_outputs key", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_id", "network_id");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String result = doc.getText();
        assertTrue("Definition should be renamed, got: " + result, result.contains("network_id = \"mock-vpc-123\""));
        assertTrue("Usage should be renamed, got: " + result, result.contains("dependency.vpc.outputs.network_id"));
    }

    public void testRenameMockOutputKeyFromUsage() {
        // Rename from dependency.vpc.outputs.vpc_id (cursor on vpc_id)
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                  mock_outputs = {
                    vpc_id = "mock-vpc-123"
                  }
                }
                
                inputs = {
                  vpc = dependency.vpc.outputs.vpc_id
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("outputs.vpc_id") + "outputs.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        DataContext ctx = dataId -> {
            if (CommonDataKeys.EDITOR.is(dataId)) return myFixture.getEditor();
            if (CommonDataKeys.PSI_FILE.is(dataId)) return myFixture.getFile();
            if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
            return null;
        };
        assertTrue("Rename should be available on dependency outputs usage", handler.isAvailableOnDataContext(ctx));

        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_id", "network_id");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String result = doc.getText();
        assertTrue("Definition should be renamed, got: " + result, result.contains("network_id = \"mock-vpc-123\""));
        assertTrue("Usage should be renamed, got: " + result, result.contains("dependency.vpc.outputs.network_id"));
    }

    public void testDeepKeyRenameQuotedKeyFromUsage() {
        // Rename from usage side where definition uses quoted key
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  network = {
                    "vpc_cidr" = "10.0.0.0/16"
                  }
                }
                
                inputs = {
                  cidr = local.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.network.vpc_cidr") + "local.network.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_cidr", "cidr_block");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String result = doc.getText();
        assertTrue("Definition should be renamed, got: " + result, result.contains("\"cidr_block\" = \"10.0.0.0/16\""));
        assertTrue("Usage should be renamed, got: " + result, result.contains("local.network.cidr_block"));
    }

    public void testDeepKeyRenameNestedFolderFromUsage() {
        // Rename from usage side with deep folder nesting
        myFixture.addFileToProject("project/common/root.hcl", """
                locals {
                  settings = {
                    log_level = "info"
                  }
                }
                """);
        var childFile = myFixture.addFileToProject("project/envs/dev/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../../common/root.hcl")
                }
                
                inputs = {
                  log = local.common.locals.settings.log_level
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("settings.log_level") + "settings.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "log_level", "verbosity");

        // Usage in child should be updated
        var childDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(childDoc);
        assertTrue("Child should use verbosity", childDoc.getText().contains("local.common.locals.settings.verbosity"));

        // Definition in common should be updated
        var updatedCommon = myFixture.findFileInTempDir("project/common/root.hcl");
        var psiCommon = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedCommon);
        var commonDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiCommon);
        assertNotNull(commonDoc);
        assertTrue("Common should have verbosity, got: " + commonDoc.getText(),
                commonDoc.getText().contains("verbosity = \"info\""));
    }

    public void testRenameAliasedAttributeDoesNotCorruptOtherFile() {
        // shared.hcl has root_settings = local.root.locals.settings (an alias)
        // app.hcl uses local.common.locals.root_settings.network.vpc_cidr
        // Renaming root_settings in shared.hcl should correctly update app.hcl
        myFixture.addFileToProject("root.hcl", """
                locals {
                  settings = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                    }
                  }
                }
                """);
        var sharedFile = myFixture.addFileToProject("common/root.hcl", """
                locals {
                  root = read_terragrunt_config("../root.hcl")
                  root_settings = local.root.locals.settings
                }
                """);
        myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common/root.hcl")
                  cidr   = local.common.locals.root_settings.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(sharedFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("root_settings");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "root_settings", "base_settings");

        // Verify shared.hcl was renamed correctly
        var sharedDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(sharedDoc);
        assertTrue("Shared should have base_settings", sharedDoc.getText().contains("base_settings = local.root.locals.settings"));

        // Verify app.hcl was updated correctly (not corrupted)
        var updatedApp = myFixture.findFileInTempDir("app/terragrunt.hcl");
        var psiApp = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedApp);
        assertNotNull(psiApp);
        var appDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiApp);
        assertNotNull(appDoc);
        String appText = appDoc.getText();
        assertTrue("App should have base_settings, got: " + appText,
                appText.contains("local.common.locals.base_settings.network.vpc_cidr"));
        assertFalse("App should NOT be corrupted", appText.contains("base_settingsork"));
    }

    public void testRenameAliasedAttributeFromUsageSide() {
        // EXACT playground replica:
        // root.hcl: locals { settings = { network = { vpc_cidr = "10.0.0.0/16" } } }
        // common/shared.hcl: locals { root = read_terragrunt_config("../root.hcl"); root_settings = local.root.locals.settings }
        // app/terragrunt.hcl: include "root" + locals { common = read_terragrunt_config(...); cidr = local.common.locals.root_settings.network.vpc_cidr }
        myFixture.addFileToProject("root.hcl", """
                locals {
                  aws_region   = "us-east-1"
                  project_name = "my-project"
                  environment  = "prod"
                
                  settings = {
                    log_level = "info"
                    timeout   = 30
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                      az_count = 3
                    }
                  }
                }
                
                inputs = {
                  default_tags = {
                    Project   = "my-project"
                    ManagedBy = "terragrunt"
                  }
                }
                """);
        myFixture.addFileToProject("common/shared.hcl", """
                locals {
                  root          = read_terragrunt_config("../root.hcl")
                  root_settings = local.root.locals.settings
                }
                """);
        var appFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                include "root" {
                  path   = find_in_parent_folders("root.hcl")
                  expose = true
                }
                
                locals {
                  common      = read_terragrunt_config("../common/shared.hcl")
                  root_config = include.root.locals
                
                  region = local.root_config.aws_region
                  log    = local.common.locals.root_settings.log_level
                  cidr   = local.common.locals.root_settings.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(appFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        // Cursor on "root_settings" in local.common.locals.root_settings.network.vpc_cidr (line 12)
        int offset = text.lastIndexOf("root_settings");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "root_settings", "base_settings");

        // App should be updated correctly - both usages
        var appDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(appDoc);
        String appText = appDoc.getText();
        assertTrue("App should have base_settings.log_level, got: " + appText,
                appText.contains("local.common.locals.base_settings.log_level"));
        assertTrue("App should have base_settings.network.vpc_cidr, got: " + appText,
                appText.contains("local.common.locals.base_settings.network.vpc_cidr"));
        assertFalse("App should NOT have root_settings anymore",
                appText.contains("root_settings"));
        assertFalse("App should NOT be corrupted",
                appText.contains("base_settingsork") || appText.contains("base_settingsog"));
    }

    public void testFindUsagesThroughRenamedAlias() {
        // root.hcl defines settings.network.vpc_cidr
        // shared.hcl aliases it as root_settings = local.root.locals.settings
        // app.hcl uses local.common.locals.root_settings.network.vpc_cidr
        // Ctrl+B on vpc_cidr in root.hcl should find usage in app.hcl
        myFixture.addFileToProject("common/root.hcl", """
                locals {
                  root = read_terragrunt_config("../root.hcl")
                  root_settings = local.root.locals.settings
                }
                """);
        myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common/root.hcl")
                  cidr   = local.common.locals.root_settings.network.vpc_cidr
                }
                """);
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  settings = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                    }
                  }
                }
                """);

        myFixture.configureFromExistingVirtualFile(rootFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("vpc_cidr");

        var gotoHandler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement element = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = gotoHandler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should find usages of vpc_cidr through renamed alias chain", targets);
        assertTrue("Should find at least one usage", targets.length > 0);
        assertEquals("terragrunt.hcl", targets[0].getContainingFile().getName());
    }

    public void testRenameFromAliasUsageWithoutLocalsKeyword() {
        // local.root_config.aws_region where root_config = include.root.locals
        // Renaming "aws_region" from here should update root.hcl
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  aws_region = "us-east-1"
                }
                """);
        var appFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                  expose = true
                }
                
                locals {
                  root_config = include.root.locals
                  region = local.root_config.aws_region
                }
                """);

        myFixture.configureFromExistingVirtualFile(appFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.root_config.aws_region") + "local.root_config.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "aws_region", "region");

        // Usage in app should be updated
        var appDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(appDoc);
        assertTrue("App should have local.root_config.region, got: " + appDoc.getText(),
                appDoc.getText().contains("local.root_config.region"));

        // Definition in root.hcl should be updated
        var updatedRoot = myFixture.findFileInTempDir("root.hcl");
        var psiRoot = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedRoot);
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiRoot);
        assertNotNull(rootDoc);
        assertTrue("Root should have region, got: " + rootDoc.getText(),
                rootDoc.getText().contains("region = \"us-east-1\""));
    }

    public void testRenameDeepKeyThroughAliasChain() {
        // local.common.locals.root_settings.network.vpc_cidr
        // root_settings in shared.hcl = local.root.locals.settings (alias)
        // Renaming "network" (idx=3) should resolve through the alias to root.hcl
        myFixture.addFileToProject("root.hcl", """
                locals {
                  settings = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                    }
                  }
                }
                """);
        myFixture.addFileToProject("common/terragrunt.hcl", """
                locals {
                  root = read_terragrunt_config("../root.hcl")
                  root_settings = local.root.locals.settings
                }
                """);
        var appFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common/terragrunt.hcl")
                  cidr   = local.common.locals.root_settings.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(appFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        // Cursor on "network" in local.common.locals.root_settings.network.vpc_cidr
        int offset = text.indexOf(".network.") + 1; // on "network"
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "network", "net");

        // App should be updated
        var appDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(appDoc);
        assertTrue("App should have .net.vpc_cidr, got: " + appDoc.getText(),
                appDoc.getText().contains("root_settings.net.vpc_cidr"));

        // Root should be updated
        var updatedRoot = myFixture.findFileInTempDir("root.hcl");
        var psiRoot = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedRoot);
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiRoot);
        assertNotNull(rootDoc);
        assertTrue("Root should have net key, got: " + rootDoc.getText(),
                rootDoc.getText().contains("net = {"));
    }

    public void testRenameDeepestKeyThroughAliasChain() {
        // Same setup, renaming "vpc_cidr" (idx=4)
        myFixture.addFileToProject("root.hcl", """
                locals {
                  settings = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                    }
                  }
                }
                """);
        myFixture.addFileToProject("common/root.hcl", """
                locals {
                  root = read_terragrunt_config("../root.hcl")
                  root_settings = local.root.locals.settings
                }
                """);
        var appFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common/root.hcl")
                  cidr   = local.common.locals.root_settings.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(appFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        // Cursor on "vpc_cidr" in local.common.locals.root_settings.network.vpc_cidr
        int offset = text.indexOf(".vpc_cidr") + 1;
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_cidr", "cidr_block");

        // App should be updated
        var appDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(appDoc);
        assertTrue("App should have .network.cidr_block, got: " + appDoc.getText(),
                appDoc.getText().contains("root_settings.network.cidr_block"));

        // Root should be updated
        var updatedRoot = myFixture.findFileInTempDir("root.hcl");
        var psiRoot = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedRoot);
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiRoot);
        assertNotNull(rootDoc);
        assertTrue("Root should have cidr_block, got: " + rootDoc.getText(),
                rootDoc.getText().contains("cidr_block = \"10.0.0.0/16\""));
    }

    public void testRenameDeepKeyFromDefinitionThroughRenamedAlias() {
        // Rename "network" from root.hcl (definition side) should update app.hcl
        // even though app.hcl references it through root_settings (a renamed alias)
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  settings = {
                    network = {
                      vpc_cidr = "10.0.0.0/16"
                    }
                  }
                }
                """);
        myFixture.addFileToProject("common/terragrunt.hcl", """
                locals {
                  root = read_terragrunt_config("../root.hcl")
                  root_settings = local.root.locals.settings
                }
                """);
        myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common/terragrunt.hcl")
                  cidr   = local.common.locals.root_settings.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(rootFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("network");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "network", "net");

        // Root definition should be renamed
        var rootDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(rootDoc);
        assertTrue("Root should have net, got: " + rootDoc.getText(),
                rootDoc.getText().contains("net = {"));

        // App usage should be renamed through the alias chain
        var updatedApp = myFixture.findFileInTempDir("app/terragrunt.hcl");
        var psiApp = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedApp);
        assertNotNull(psiApp);
        var appDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiApp);
        assertNotNull(appDoc);
        assertTrue("App should have .net.vpc_cidr, got: " + appDoc.getText(),
                appDoc.getText().contains("root_settings.net.vpc_cidr"));
    }

    public void testRenameAliasFromIntermediateFile() {
        // Rename "root_settings" from shared.hcl (the intermediate file where the alias lives)
        // Should update shared.hcl definition AND app.hcl usage
        myFixture.addFileToProject("root.hcl", """
                locals {
                  settings = {
                    network = { vpc_cidr = "10.0.0.0/16" }
                  }
                }
                """);
        var sharedFile = myFixture.addFileToProject("common/terragrunt.hcl", """
                locals {
                  root = read_terragrunt_config("../root.hcl")
                  root_settings = local.root.locals.settings
                }
                """);
        myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common/terragrunt.hcl")
                  cidr   = local.common.locals.root_settings.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(sharedFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("root_settings");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "root_settings", "base_cfg");

        // Shared definition should be renamed
        var sharedDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(sharedDoc);
        assertTrue("Shared should have base_cfg", sharedDoc.getText().contains("base_cfg = local.root.locals.settings"));

        // App usage should be renamed
        var updatedApp = myFixture.findFileInTempDir("app/terragrunt.hcl");
        var psiApp = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(updatedApp);
        var appDoc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiApp);
        assertNotNull(appDoc);
        assertTrue("App should have base_cfg, got: " + appDoc.getText(),
                appDoc.getText().contains("local.common.locals.base_cfg.network.vpc_cidr"));
    }

    public void testRenameUpdatesMultipleConsumers() {
        // Two app files both use local.common.locals.network.vpc_cidr
        // Renaming vpc_cidr from root.hcl should update BOTH
        var rootFile = myFixture.addFileToProject("root.hcl", """
                locals {
                  network = {
                    vpc_cidr = "10.0.0.0/16"
                  }
                }
                """);
        myFixture.addFileToProject("app1/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../root.hcl")
                  cidr   = local.common.locals.network.vpc_cidr
                }
                """);
        myFixture.addFileToProject("app2/terragrunt.hcl", """
                locals {
                  cfg  = read_terragrunt_config("../root.hcl")
                  cidr = local.cfg.locals.network.vpc_cidr
                }
                """);

        myFixture.configureFromExistingVirtualFile(rootFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("vpc_cidr");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc_cidr", "cidr_block");

        // Both consumers should be updated
        var app1 = myFixture.findFileInTempDir("app1/terragrunt.hcl");
        var app1Doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(
                com.intellij.psi.PsiManager.getInstance(getProject()).findFile(app1));
        assertNotNull(app1Doc);
        assertTrue("App1 should have cidr_block, got: " + app1Doc.getText(),
                app1Doc.getText().contains("network.cidr_block"));

        var app2 = myFixture.findFileInTempDir("app2/terragrunt.hcl");
        var app2Doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(
                com.intellij.psi.PsiManager.getInstance(getProject()).findFile(app2));
        assertNotNull(app2Doc);
        assertTrue("App2 should have cidr_block, got: " + app2Doc.getText(),
                app2Doc.getText().contains("network.cidr_block"));
    }

    public void testRenameSameKeyNameAtDifferentDepths() {
        // settings = { name = "x", nested = { name = "y" } }
        // Renaming "name" at the top level should NOT affect nested.name
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  config = {
                    name = "top"
                    nested = {
                      name = "inner"
                    }
                  }
                }
                
                inputs = {
                  top   = local.config.name
                  inner = local.config.nested.name
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        // Cursor on "name" in local.config.name (the TOP-level key, not nested)
        int offset = text.indexOf("local.config.name") + "local.config.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "name", "label");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String result = doc.getText();
        assertTrue("Top usage should be renamed, got: " + result, result.contains("local.config.label"));
        assertTrue("Nested usage should NOT be renamed, got: " + result, result.contains("local.config.nested.name"));
        assertTrue("Top definition should be renamed", result.contains("label = \"top\""));
        assertTrue("Nested definition should NOT be renamed", result.contains("name = \"inner\""));
    }

    public void testRenameKeyWhenInnerObjectHasMoreChildren() {
        // Outer object has 1 key (opts), inner has 3 keys (a, b, c)
        // Renaming "opts" should work correctly despite inner being larger
        var file = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  config = {
                    opts = {
                      a = 1
                      b = 2
                      c = 3
                    }
                  }
                }
                
                inputs = {
                  o = local.config.opts
                }
                """);

        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        // Rename "opts" from the usage
        int offset = text.indexOf("local.config.opts") + "local.config.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "opts", "options");

        var doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(myFixture.getFile());
        assertNotNull(doc);
        String result = doc.getText();
        assertTrue("Definition should be renamed, got: " + result, result.contains("options = {"));
        assertTrue("Usage should be renamed, got: " + result, result.contains("local.config.options"));
        assertTrue("Inner keys should be unchanged", result.contains("a = 1"));
    }

    public void testRenameDependencyLabel() {
        myFixture.configureByText("terragrunt.hcl", """
                dependency "vpc" {
                  config_path = "../vpc"
                }
                
                inputs = {
                  vpc_id = dependency.vpc.outputs.vpc_id
                  subnet = dependency.vpc.outputs.subnet_id
                }
                """);
        int offset = myFixture.getEditor().getDocument().getText().indexOf("\"vpc\"") + 1;
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "vpc", "network");

        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Label should be renamed, got: " + result, result.contains("dependency \"network\""));
        assertTrue("Usage should be renamed, got: " + result, result.contains("dependency.network.outputs.vpc_id"));
        assertTrue("Second usage too, got: " + result, result.contains("dependency.network.outputs.subnet_id"));
        assertFalse("No old name remaining", result.contains("dependency.vpc"));
    }

    public void testRenameFeatureLabel() {
        myFixture.configureByText("terragrunt.hcl", """
                feature "enable_monitoring" {
                  default = true
                }
                
                inputs = {
                  monitor = feature.enable_monitoring.value
                }
                """);
        int offset = myFixture.getEditor().getDocument().getText().indexOf("\"enable_monitoring\"") + 1;
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "enable_monitoring", "monitoring_enabled");

        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Label should be renamed", result.contains("feature \"monitoring_enabled\""));
        assertTrue("Usage should be renamed", result.contains("feature.monitoring_enabled.value"));
    }

    public void testRenameIncludeLabel() {
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                locals {
                  region = include.root.locals.region
                }
                """);
        int offset = myFixture.getEditor().getDocument().getText().indexOf("\"root\"") + 1;
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var handler = new TerragruntRenameHandler();
        handler.performRenameForTest(getProject(), myFixture.getFile(),
                myFixture.getFile().findElementAt(offset), "root", "base");

        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Label should be renamed", result.contains("include \"base\""));
        assertTrue("Usage should be renamed", result.contains("include.base.locals.region"));
    }
}
