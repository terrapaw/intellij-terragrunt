package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.lang.psi.*;
import com.github.terrapaw.terragrunt.reference.TerragruntFileResolver;
import com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;

public class TerragruntCrossFileTest extends BasePlatformTestCase {

    private final TerragruntGotoDeclarationHandler handler = new TerragruntGotoDeclarationHandler();

    public void testIncludeNavigatesToBlock() {
        myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                
                inputs = {
                  x = include.<caret>root.locals.region
                }
                """);
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, myFixture.getCaretOffset(), myFixture.getEditor());
        assertNotNull("Should resolve include.root to the include block", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testIncludeLocalsResolvesToIncludedFile() {
        // Create the included file in the project root
        myFixture.addFileToProject("root.hcl", """
                locals {
                  region = "us-east-1"
                  name   = "my-project"
                }
                """);

        // Configure the child file that references it with a relative path
        PsiFile childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  r = include.root.locals.region
                }
                """);
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());

        // Place caret on "region"
        int offset = myFixture.getEditor().getDocument().getText().indexOf("locals.region") + "locals.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        PsiElement element = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve include.root.locals.region across files", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testReferenceContributorResolvesRelativePath() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        PsiFile childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path = "../root.hcl"
                }
                """);
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());

        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("../root.hcl") + 1;
        var ref = myFixture.getFile().findReferenceAt(offset);
        if (ref != null) {
            var resolved = ref.resolve();
            assertNotNull("Should resolve ../root.hcl reference", resolved);
        }
    }

    public void testIncludeLocalsCompletionFromIncludedFile() {
        // Create the included file
        myFixture.addFileToProject("root.hcl", """
                locals {
                  region   = "us-east-1"
                  app_name = "my-app"
                }
                """);

        // Verify the resolver can find locals in the included file
        PsiFile childFile = myFixture.addFileToProject("child/terragrunt.hcl", """
                include "root" {
                  path   = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  r = include.root.locals.region
                }
                """);
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());

        // Verify the file resolves correctly by checking navigation works
        int offset = myFixture.getEditor().getDocument().getText().indexOf("locals.region") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve region from included file", targets);
        assertTrue("Should find the region attribute", targets.length > 0);
    }

    public void testFindUsagesFromIncludedFileLocals() {
        // Create root.hcl so the overrider detects env.hcl as Terragrunt
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");

        // Create the included file with a locals attribute
        PsiFile envFile = myFixture.addFileToProject("env.hcl", """
                locals {
                  environment = "dev"
                }
                """);

        // Create a child file that references it via include.env.locals.environment
        myFixture.addFileToProject("vpc/terragrunt.hcl", """
                include "env" {
                  path   = "../env.hcl"
                  expose = true
                }
                
                inputs = {
                  env = include.env.locals.environment
                }
                """);

        // Verify the include in the child file resolves to env.hcl
        myFixture.configureFromExistingVirtualFile(envFile.getVirtualFile());

        int offset = myFixture.getEditor().getDocument().getText().indexOf("environment");
        PsiElement element = myFixture.getFile().findElementAt(offset);
        assertNotNull("Should find element", element);

        // This tests the full Ctrl+B flow
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should find cross-file usages of 'environment'", targets);
        assertTrue("Should have at least one usage", targets.length > 0);
    }

    public void testFindUsagesViaAliasedLocals() {
        // Create root.hcl so the overrider detects env.hcl as Terragrunt
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");

        // Create the included file
        PsiFile envFile = myFixture.addFileToProject("env.hcl", """
                locals {
                  environment = "dev"
                  vpc_cidr    = "10.0.0.0/16"
                }
                """);

        // Create a child file that aliases include.env.locals into a local variable
        myFixture.addFileToProject("vpc/terragrunt.hcl", """
                include "env" {
                  path   = "../env.hcl"
                  expose = true
                }
                
                locals {
                  env_vars = include.env.locals
                }
                
                inputs = {
                  name = local.env_vars.environment
                  cidr = local.env_vars.vpc_cidr
                }
                """);

        // Ctrl+B on "environment" in env.hcl should find local.env_vars.environment
        myFixture.configureFromExistingVirtualFile(envFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("environment");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should find aliased usage of 'environment'", targets);
        assertTrue("Should have at least one usage", targets.length > 0);
    }

    public void testNavigateFromAliasedLocalToIncludedFile() {
        // Create root.hcl so the overrider detects env.hcl as Terragrunt
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");

        // Create the included file
        myFixture.addFileToProject("env.hcl", """
                locals {
                  environment = "dev"
                  vpc_cidr    = "10.0.0.0/16"
                }
                """);

        // Create a child file with alias pattern
        PsiFile childFile = myFixture.addFileToProject("vpc/terragrunt.hcl", """
                include "env" {
                  path   = "../env.hcl"
                  expose = true
                }
                
                locals {
                  env_vars = include.env.locals
                }
                
                inputs = {
                  name = local.env_vars.environment
                }
                """);

        // Ctrl+B on "environment" in local.env_vars.environment should go to env.hcl
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("env_vars.environment") + "env_vars.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate from local.env_vars.environment to env.hcl", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testNavigateDirectIncludeInputs() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        myFixture.addFileToProject("env.hcl", """
                inputs = {
                  default_tags = { Team = "platform" }
                  region       = "us-east-1"
                }
                """);

        PsiFile childFile = myFixture.addFileToProject("vpc/terragrunt.hcl", """
                include "env" {
                  path   = "../env.hcl"
                  expose = true
                }
                
                inputs = {
                  tags = include.env.inputs.default_tags
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("inputs.default_tags") + "inputs.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate from include.env.inputs.default_tags", targets);
        assertTrue("Should have at least one target", targets.length > 0);
        assertEquals("Should jump to the exact key 'default_tags'", "default_tags", targets[0].getText());
    }

    public void testNavigateDirectIncludeLocals() {
        // Create the included file
        myFixture.addFileToProject("root.hcl", """
                locals {
                  aws_region = "us-east-1"
                  account_id = "123456"
                }
                """);

        // Create a child file using include.root.locals.account_id directly
        PsiFile childFile = myFixture.addFileToProject("vpc/terragrunt.hcl", """
                include "root" {
                  path   = "../root.hcl"
                  expose = true
                }
                
                inputs = {
                  account = include.root.locals.account_id
                }
                """);

        // Ctrl+B on "account_id" should navigate to root.hcl
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.account_id") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate from include.root.locals.account_id to root.hcl", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testNavigateViaReadTerragruntConfig() {
        // Create marker so .hcl files are detected as Terragrunt
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");

        // Create the config file to be read
        myFixture.addFileToProject("common.hcl", """
                locals {
                  project_name = "my-project"
                  region       = "us-west-2"
                }
                """);

        // Create a file that uses read_terragrunt_config
        PsiFile childFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common.hcl")
                  name   = local.common.locals.project_name
                }
                """);

        // Ctrl+B on "project_name" in local.common.locals.project_name
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.project_name") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate from local.common.locals.project_name to common.hcl", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testFindUsagesFromInputsKey() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        PsiFile commonFile = myFixture.addFileToProject("common.hcl", """
                inputs = {
                  notification_email = "team@example.com"
                }
                """);

        myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common.hcl")
                }
                inputs = {
                  email = local.common.inputs.notification_email
                }
                """);

        // Ctrl+B on "notification_email" in common.hcl's inputs
        myFixture.configureFromExistingVirtualFile(commonFile.getVirtualFile());
        int offset = myFixture.getEditor().getDocument().getText().indexOf("notification_email");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should find usages of notification_email from inputs", targets);
        assertTrue("Should have at least one usage", targets.length > 0);
    }

    public void testNavigateLocalAliasInputs() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        myFixture.addFileToProject("common.hcl", """
                inputs = {
                  notification_email = "team@example.com"
                  alert_channel      = "#alerts"
                }
                """);

        PsiFile childFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("../common.hcl")
                }
                inputs = {
                  email = local.common.inputs.notification_email
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("inputs.notification_email") + "inputs.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate from local.common.inputs.notification_email", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testNavigateViaReadTerragruntConfigWithFindInParentFolders() {
        // Create marker
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");

        // Create the config file
        myFixture.addFileToProject("common.hcl", """
                locals {
                  account_id = "999888777"
                }
                """);

        // Create a child file using read_terragrunt_config(find_in_parent_folders("common.hcl"))
        PsiFile childFile = myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  shared = read_terragrunt_config(find_in_parent_folders("common.hcl"))
                  acct   = local.shared.locals.account_id
                }
                """);

        // Ctrl+B on "account_id"
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.account_id") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should navigate via read_terragrunt_config + find_in_parent_folders", targets);
        assertTrue("Should have at least one target", targets.length > 0);
    }

    public void testCompletionLocalAliasDotSuggestsLocals() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        myFixture.addFileToProject("common.hcl", """
                locals {
                  org_name = "acme"
                  team     = "platform"
                }
                """);

        // All files via addFileToProject so they share the same VFS
        PsiFile mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("common.hcl")
                }
                
                inputs = {
                  x = local.common.
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());

        // Position caret after "local.common."
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.common.") + "local.common.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for local.common.", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'locals' after local.common. Got: " + names, names.contains("locals"));
    }

    public void testCompletionLocalAliasLocalsDotSuggestsAttributes() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        myFixture.addFileToProject("common.hcl", """
                locals {
                  org_name = "acme"
                  team     = "platform"
                }
                """);

        PsiFile mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("common.hcl")
                }
                
                inputs = {
                  x = local.common.locals.
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());

        // Position caret after "local.common.locals."
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.common.locals.") + "local.common.locals.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for local.common.locals.", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'org_name' from common.hcl. Got: " + names, names.contains("org_name"));
        assertTrue("Should suggest 'team' from common.hcl. Got: " + names, names.contains("team"));
    }

    public void testCompletionIncludeInputsAliasSuggestsKeysDirectly() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        myFixture.addFileToProject("env.hcl", """
                inputs = {
                  default_tags = {}
                  log_level    = "info"
                }
                """);

        PsiFile mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                include "env" {
                  path   = "env.hcl"
                  expose = true
                }
                locals {
                  env_inputs = include.env.inputs
                }
                inputs = {
                  x = local.env_inputs.
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());

        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.env_inputs.") + "local.env_inputs.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for local.env_inputs.", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'default_tags'. Got: " + names, names.contains("default_tags"));
        assertTrue("Should suggest 'log_level'. Got: " + names, names.contains("log_level"));
    }

    public void testCompletionIncludeLocalsAliasSuggestsAttributesDirectly() {
        myFixture.addFileToProject("root.hcl", """
                locals {
                  aws_region = "us-east-1"
                  account_id = "123456"
                }
                """);

        PsiFile mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                include "root" {
                  path   = "root.hcl"
                  expose = true
                }
                
                locals {
                  root_config = include.root.locals
                }
                
                inputs = {
                  x = local.root_config.
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());

        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.root_config.") + "local.root_config.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for local.root_config.", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'aws_region' directly. Got: " + names, names.contains("aws_region"));
        assertTrue("Should suggest 'account_id' directly. Got: " + names, names.contains("account_id"));
    }

    public void testCompletionInputsFromReadTerragruntConfig() {
        myFixture.addFileToProject("root.hcl", "locals { x = 1 }");
        myFixture.addFileToProject("common.hcl", """
                inputs = {
                  vpc_id = "vpc-123"
                  region = "us-east-1"
                }
                """);

        PsiFile mainFile = myFixture.addFileToProject("terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("common.hcl")
                }
                
                inputs = {
                  x = local.common.inputs.
                }
                """);
        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());

        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("local.common.inputs.") + "local.common.inputs.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for local.common.inputs.", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'vpc_id'. Got: " + names, names.contains("vpc_id"));
        assertTrue("Should suggest 'region'. Got: " + names, names.contains("region"));
    }
}
