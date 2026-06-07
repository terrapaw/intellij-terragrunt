package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.run.TerragruntRunConfiguration;
import com.github.terrapaw.terragrunt.run.TerragruntRunConfigurationType;
import com.github.terrapaw.terragrunt.run.TerragruntRunLineMarkerProvider;
import com.github.terrapaw.terragrunt.settings.TerragruntSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class TerragruntRunConfigTest extends BasePlatformTestCase {

    public void testRunConfigurationTypeRegistered() {
        var configType = ConfigurationTypeUtil.findConfigurationType(TerragruntRunConfigurationType.class);
        assertNotNull("Terragrunt run configuration type should be registered", configType);
        assertEquals("Terragrunt", configType.getDisplayName());
    }

    public void testRunConfigurationCreation() {
        var configType = ConfigurationTypeUtil.findConfigurationType(TerragruntRunConfigurationType.class);
        var factory = configType.getConfigurationFactories()[0];
        var settings = RunManager.getInstance(getProject()).createConfiguration("test", factory);
        var config = (TerragruntRunConfiguration) settings.getConfiguration();

        config.setCommand("plan");
        config.setWorkingDirectory("/tmp");
        config.setAdditionalArgs("--non-interactive");

        assertEquals("plan", config.getCommand());
        assertEquals("/tmp", config.getWorkingDirectory());
        assertEquals("--non-interactive", config.getAdditionalArgs());
    }

    public void testBinaryPathAutoDetect() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        settings.setBinaryPath("");
        String path = settings.getEffectiveBinaryPath();
        // Should return either a found path or fallback "terragrunt"
        assertNotNull(path);
        assertFalse(path.isBlank());
    }

    public void testBinaryPathManualOverride() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        settings.setBinaryPath("/usr/local/bin/terragrunt");
        assertEquals("/usr/local/bin/terragrunt", settings.getEffectiveBinaryPath());
        settings.setBinaryPath(""); // reset
    }

    public void testGutterMarkerOnTerragruntHcl() {
        PsiFile file = myFixture.configureByText("terragrunt.hcl", """
                include "root" {
                  path = find_in_parent_folders("root.hcl")
                }
                """);
        var provider = new TerragruntRunLineMarkerProvider();
        var block = PsiTreeUtil.findChildOfType(file,
                com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock.class);
        assertNotNull(block);
        LineMarkerInfo<?> marker = provider.getLineMarkerInfo(block.getIdentifier());
        assertNotNull("Should have gutter run marker on terragrunt.hcl", marker);
    }

    public void testGutterMarkerOnStackHcl() {
        PsiFile file = myFixture.configureByText("terragrunt.stack.hcl", """
                unit "vpc" {
                  source = "./units/vpc"
                  path   = "vpc"
                }
                """);
        var provider = new TerragruntRunLineMarkerProvider();
        var block = PsiTreeUtil.findChildOfType(file,
                com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock.class);
        assertNotNull(block);
        LineMarkerInfo<?> marker = provider.getLineMarkerInfo(block.getIdentifier());
        assertNotNull("Should have gutter run marker on terragrunt.stack.hcl", marker);
    }

    public void testNoGutterMarkerOnOtherHcl() {
        PsiFile file = myFixture.configureByText("root.hcl", """
                locals {
                  region = "us-east-1"
                }
                """);
        var provider = new TerragruntRunLineMarkerProvider();
        var block = PsiTreeUtil.findChildOfType(file,
                com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock.class);
        assertNotNull(block);
        LineMarkerInfo<?> marker = provider.getLineMarkerInfo(block.getIdentifier());
        assertNull("Should NOT have gutter marker on root.hcl", marker);
    }

    public void testParametersListUtilParsesQuotedArgs() {
        // Verifies the library we use for arg parsing handles quotes correctly
        var parsed = com.intellij.util.execution.ParametersListUtil.parse(
                "--var=\"name=hello world\" --no-color");
        assertEquals(2, parsed.size());
        assertEquals("--var=name=hello world", parsed.get(0));
        assertEquals("--no-color", parsed.get(1));
    }
}
