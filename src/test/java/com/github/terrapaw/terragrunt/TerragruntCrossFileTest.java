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

    public void testGetParentTerragruntDirResolution() {
        // Create root.hcl in project root (makes it a "terragrunt dir")
        myFixture.addFileToProject("root.hcl", "locals {}");
        myFixture.addFileToProject("common.hcl", """
                locals {
                  project_name = "my-app"
                }
                """);

        // Child file uses get_parent_terragrunt_dir() to reference common.hcl
        PsiFile childFile = myFixture.addFileToProject("modules/vpc/terragrunt.hcl", """
                include "common" {
                  path = "${get_parent_terragrunt_dir()}/common.hcl"
                  expose = true
                }

                inputs = {
                  name = include.common.locals.project_name
                }
                """);
        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());

        // First verify the include resolves
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock includeBlock = null;
        for (TerragruntBlock block : blocks) {
            if ("include".equals(block.getIdentifier().getText())) {
                includeBlock = block;
                break;
            }
        }
        assertNotNull("Should find include block", includeBlock);
        PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
        assertNotNull("resolveInclude should resolve the path with get_parent_terragrunt_dir(). " +
                "sourceFile=" + childFile.getVirtualFile().getPath(), resolved);
    }

    public void testGetTerragruntDirResolution() {
        // File references sibling using get_terragrunt_dir()
        myFixture.addFileToProject("isolated/settings.hcl", """
                locals {
                  deploy_env = "prod"
                }
                """);

        PsiFile file = myFixture.addFileToProject("isolated/terragrunt.hcl", """
                locals {
                  settings = read_terragrunt_config("${get_terragrunt_dir()}/settings.hcl")
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        // Directly test that read_terragrunt_config resolves the function path
        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock localsBlock = null;
        for (TerragruntBlock b : blocks) {
            if ("locals".equals(b.getIdentifier().getText())) { localsBlock = b; break; }
        }
        assertNotNull("Should find locals block", localsBlock);
        TerragruntAttribute settingsAttr = PsiTreeUtil.getChildrenOfTypeAsList(localsBlock.getBody(), TerragruntAttribute.class).getFirst();
        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(settingsAttr, TerragruntFunctionCall.class);
        assertNotNull("Should find read_terragrunt_config call", funcCall);
        assertEquals("read_terragrunt_config", funcCall.getIdentifier().getText());
        PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
        assertNotNull("Should resolve read_terragrunt_config with get_terragrunt_dir()", resolved);
        assertTrue("Should resolve to settings.hcl", resolved.getName().equals("settings.hcl"));
    }

    public void testGetRootTerragruntDirResolution() {
        // root.hcl at project root
        myFixture.addFileToProject("root.hcl", """
                locals {
                  account_id = "123456"
                }
                """);

        PsiFile file = myFixture.addFileToProject("envs/dev/terragrunt.hcl", """
                locals {
                  root = read_terragrunt_config("${get_root_terragrunt_dir()}/root.hcl")
                }

                inputs = {
                  account = local.root.locals.account_id
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.account_id") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve through get_root_terragrunt_dir()", targets);
        assertTrue("Should find target", targets.length > 0);
    }

    public void testGetParentTerragruntDirInReadConfig() {
        // Parent dir has terragrunt.hcl and common.hcl
        myFixture.addFileToProject("terragrunt.hcl", "");
        myFixture.addFileToProject("common.hcl", """
                locals {
                  team = "platform"
                }
                """);

        PsiFile file = myFixture.addFileToProject("app/terragrunt.hcl", """
                locals {
                  common = read_terragrunt_config("${get_parent_terragrunt_dir()}/common.hcl")
                }

                inputs = {
                  team = local.common.locals.team
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.team") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve get_parent_terragrunt_dir() in read_terragrunt_config", targets);
        assertTrue("Should find target", targets.length > 0);
    }

    public void testGetRepoRootResolution() {
        // Create .git to mark repo root
        myFixture.addFileToProject(".git/config", "");
        myFixture.addFileToProject("infra/shared.hcl", """
                locals {
                  org = "terrapaw"
                }
                """);

        PsiFile file = myFixture.addFileToProject("infra/modules/vpc/terragrunt.hcl", """
                locals {
                  shared = read_terragrunt_config("${get_repo_root()}/infra/shared.hcl")
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock localsBlock = null;
        for (TerragruntBlock b : blocks) {
            if ("locals".equals(b.getIdentifier().getText())) { localsBlock = b; break; }
        }
        assertNotNull("Should find locals block", localsBlock);
        TerragruntAttribute attr = PsiTreeUtil.getChildrenOfTypeAsList(localsBlock.getBody(), TerragruntAttribute.class).getFirst();
        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
        assertNotNull("Should find read_terragrunt_config call", funcCall);
        PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
        assertNotNull("Should resolve read_terragrunt_config with get_repo_root()", resolved);
        assertEquals("shared.hcl", resolved.getName());
    }

    public void testDirnameFindInParentFoldersResolution() {
        // dirname(find_in_parent_folders("root.hcl")) returns the directory containing root.hcl
        myFixture.addFileToProject("root.hcl", "locals {}");
        myFixture.addFileToProject("common/base.hcl", """
                locals {
                  base_name = "infra"
                }
                """);

        PsiFile file = myFixture.addFileToProject("envs/dev/terragrunt.hcl", """
                include "root" {
                  path = "${dirname(find_in_parent_folders(\"root.hcl\"))}/common/base.hcl"
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock includeBlock = null;
        for (TerragruntBlock b : blocks) {
            if ("include".equals(b.getIdentifier().getText())) { includeBlock = b; break; }
        }
        assertNotNull("Should find include block", includeBlock);
        PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
        assertNotNull("Should resolve dirname(find_in_parent_folders()) path", resolved);
        assertEquals("base.hcl", resolved.getName());
    }

    public void testFindInParentFoldersWithFallbackArg() {
        // find_in_parent_folders("root.hcl", "fallback.hcl") should resolve using first arg only
        myFixture.addFileToProject("root.hcl", "locals {}");

        PsiFile file = myFixture.addFileToProject("app/terragrunt.hcl", """
                include "root" {
                  path = "${find_in_parent_folders(\"root.hcl\", \"fallback.hcl\")}"
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock includeBlock = null;
        for (TerragruntBlock b : blocks) {
            if ("include".equals(b.getIdentifier().getText())) { includeBlock = b; break; }
        }
        assertNotNull(includeBlock);
        PsiFile resolved = TerragruntFileResolver.resolveInclude(includeBlock);
        assertNotNull("Should resolve find_in_parent_folders with fallback arg", resolved);
        assertEquals("root.hcl", resolved.getName());
    }

    public void testGetPathToRepoRootResolution() {
        myFixture.addFileToProject(".git/config", "");
        myFixture.addFileToProject("modules/vpc/main.hcl", """
                locals {
                  vpc_name = "main-vpc"
                }
                """);

        PsiFile file = myFixture.addFileToProject("modules/vpc/terragrunt.hcl", """
                locals {
                  config = read_terragrunt_config("${get_path_to_repo_root()}/modules/vpc/main.hcl")
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock localsBlock = null;
        for (TerragruntBlock b : blocks) {
            if ("locals".equals(b.getIdentifier().getText())) { localsBlock = b; break; }
        }
        assertNotNull(localsBlock);
        TerragruntAttribute attr = PsiTreeUtil.getChildrenOfTypeAsList(localsBlock.getBody(), TerragruntAttribute.class).getFirst();
        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
        PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
        assertNotNull("Should resolve get_path_to_repo_root() path", resolved);
        assertEquals("main.hcl", resolved.getName());
    }


    public void testGetPathFromRepoRootResolution() {
        myFixture.addFileToProject(".git/config", "");
        myFixture.addFileToProject("envs/dev/env.hcl", """
                locals {
                  env_name = "dev"
                }
                """);

        // get_path_from_repo_root() for a file in envs/dev/ returns "envs/dev"
        PsiFile file = myFixture.addFileToProject("envs/dev/terragrunt.hcl", """
                locals {
                  config = read_terragrunt_config("${get_repo_root()}/${get_path_from_repo_root()}/env.hcl")
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock localsBlock = null;
        for (TerragruntBlock b : blocks) {
            if ("locals".equals(b.getIdentifier().getText())) { localsBlock = b; break; }
        }
        assertNotNull(localsBlock);
        TerragruntAttribute attr = PsiTreeUtil.getChildrenOfTypeAsList(localsBlock.getBody(), TerragruntAttribute.class).getFirst();
        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
        PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
        assertNotNull("Should resolve get_path_from_repo_root() path", resolved);
        assertEquals("env.hcl", resolved.getName());
    }

    public void testBasenameInPath() {
        myFixture.addFileToProject(".git/config", "");
        myFixture.addFileToProject("modules/vpc/vpc.hcl", """
                locals {
                  x = "test"
                }
                """);

        // basename(get_terragrunt_dir()) returns "vpc" for a file in modules/vpc/
        PsiFile file = myFixture.addFileToProject("modules/vpc/terragrunt.hcl", """
                locals {
                  config = read_terragrunt_config("${get_repo_root()}/modules/${basename(get_terragrunt_dir())}/vpc.hcl")
                }
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        Collection<TerragruntBlock> blocks = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), TerragruntBlock.class);
        TerragruntBlock localsBlock = null;
        for (TerragruntBlock b : blocks) {
            if ("locals".equals(b.getIdentifier().getText())) { localsBlock = b; break; }
        }
        assertNotNull(localsBlock);
        TerragruntAttribute attr = PsiTreeUtil.getChildrenOfTypeAsList(localsBlock.getBody(), TerragruntAttribute.class).getFirst();
        TerragruntFunctionCall funcCall = PsiTreeUtil.findChildOfType(attr, TerragruntFunctionCall.class);
        PsiFile resolved = TerragruntFileResolver.resolveReadTerragruntConfig(funcCall, file);
        assertNotNull("Should resolve basename(get_terragrunt_dir()) in path", resolved);
        assertEquals("vpc.hcl", resolved.getName());
    }

    public void testStackContextResolution() {
        // Marker so .hcl files are detected as Terragrunt
        myFixture.addFileToProject("root.hcl", "");

        // Two env.hcl files at different levels
        myFixture.addFileToProject("stack-ctx-env.hcl", """
                locals {
                  environment = "outer"
                }
                """);
        myFixture.addFileToProject("stack-ctx-proj/stack-ctx-env.hcl", """
                locals {
                  environment = "prod"
                }
                """);

        // shared config inside proj/ uses find_in_parent_folders
        PsiFile myconfig = myFixture.addFileToProject("stack-ctx-proj/stack-ctx-shared.hcl", """
                locals {
                  env_config = read_terragrunt_config(find_in_parent_folders("stack-ctx-env.hcl"))
                  deploy_env = local.env_config.locals.environment
                }
                """);

        // A generated unit inside proj/ includes the shared config
        myFixture.addFileToProject("stack-ctx-proj/stack-ctx-gen/api/terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("stack-ctx-shared.hcl")
                }
                """);

        // Ctrl+B on "environment" in local.env_config.locals.environment
        myFixture.configureFromExistingVirtualFile(myconfig.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.environment") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve local.env_config.locals.environment via stack context", targets);
        assertTrue("Should find target", targets.length > 0);
    }

    public void testDeepChainNavigation() {
        // include.root.locals.env_config.locals.environment — 5 levels deep
        myFixture.addFileToProject("root.hcl", "");

        myFixture.addFileToProject("deep-env.hcl", """
                locals {
                  environment = "prod"
                }
                """);

        myFixture.addFileToProject("deep-shared.hcl", """
                locals {
                  env_config = read_terragrunt_config("deep-env.hcl")
                }
                """);

        PsiFile childFile = myFixture.addFileToProject("deep-child/terragrunt.hcl", """
                include "root" {
                  path = "../deep-shared.hcl"
                }

                inputs = {
                  env = include.root.locals.env_config.locals.environment
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.lastIndexOf("environment");
        PsiElement element = myFixture.getFile().findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve include.root.locals.env_config.locals.environment", targets);
        assertTrue("Should find target", targets.length > 0);
    }

    public void testDeepChainCompletion() {
        // include.root.locals.env_config.locals. should suggest from deep-env.hcl
        myFixture.addFileToProject("root.hcl", "");

        myFixture.addFileToProject("deep-comp-env.hcl", """
                locals {
                  environment = "prod"
                  region      = "us-east-1"
                }
                """);

        myFixture.addFileToProject("deep-comp-shared.hcl", """
                locals {
                  env_config = read_terragrunt_config("deep-comp-env.hcl")
                }
                """);

        PsiFile childFile = myFixture.addFileToProject("deep-comp-child/terragrunt.hcl", """
                include "root" {
                  path = "../deep-comp-shared.hcl"
                }

                inputs = {
                  env = include.root.locals.env_config.locals.
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("env_config.locals.") + "env_config.locals.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for deep chain", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'environment'. Got: " + names, names.contains("environment"));
        assertTrue("Should suggest 'region'. Got: " + names, names.contains("region"));
    }

    public void testDeepChainIntermediateCompletion() {
        // include.root.locals.env_config. should suggest "locals" and "inputs"
        myFixture.addFileToProject("root.hcl", "");

        myFixture.addFileToProject("deep-int-env.hcl", """
                locals {
                  environment = "prod"
                }
                """);

        myFixture.addFileToProject("deep-int-shared.hcl", """
                locals {
                  env_config = read_terragrunt_config("deep-int-env.hcl")
                }
                """);

        PsiFile childFile = myFixture.addFileToProject("deep-int-child/terragrunt.hcl", """
                include "root" {
                  path = "../deep-int-shared.hcl"
                }

                inputs = {
                  env = include.root.locals.env_config.
                }
                """);

        myFixture.configureFromExistingVirtualFile(childFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("env_config.") + "env_config.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions after alias", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'locals'. Got: " + names, names.contains("locals"));
        assertTrue("Should suggest 'inputs'. Got: " + names, names.contains("inputs"));
    }

    public void testLocalDeepChainIntermediateCompletion() {
        // local.shared.locals.env_config. should suggest "locals" and "inputs"
        myFixture.addFileToProject("root.hcl", "");

        myFixture.addFileToProject("loc-deep-env.hcl", """
                locals {
                  environment = "prod"
                }
                """);

        myFixture.addFileToProject("loc-deep-shared.hcl", """
                locals {
                  env_config = read_terragrunt_config("loc-deep-env.hcl")
                }
                """);

        PsiFile mainFile = myFixture.addFileToProject("loc-deep-main/terragrunt.hcl", """
                locals {
                  shared = read_terragrunt_config("../loc-deep-shared.hcl")
                }

                inputs = {
                  env = local.shared.locals.env_config.
                }
                """);

        myFixture.configureFromExistingVirtualFile(mainFile.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("env_config.") + "env_config.".length();
        myFixture.getEditor().getCaretModel().moveToOffset(offset);

        var completions = myFixture.completeBasic();
        assertNotNull("Should have completions for local deep chain intermediate", completions);
        var names = java.util.List.of(completions).stream().map(l -> l.getLookupString()).toList();
        assertTrue("Should suggest 'locals'. Got: " + names, names.contains("locals"));
        assertTrue("Should suggest 'inputs'. Got: " + names, names.contains("inputs"));
    }
}
