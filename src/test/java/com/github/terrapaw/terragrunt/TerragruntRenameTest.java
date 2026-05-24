package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.refactor.TerragruntNamesValidator;
import com.github.terrapaw.terragrunt.refactor.TerragruntRenameHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
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
}
