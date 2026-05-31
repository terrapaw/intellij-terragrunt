package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.settings.TerragruntSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class TerragruntSettingsTest extends BasePlatformTestCase {

    public void testDefaultEntryPointFilenames() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        assertTrue("Default should contain terragrunt.hcl",
                settings.getEntryPointFilenames().contains("terragrunt.hcl"));
    }

    public void testDefaultMarkerFilenames() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        List<String> markers = settings.getMarkerFilenames();
        assertTrue("Default should contain terragrunt.hcl", markers.contains("terragrunt.hcl"));
        assertTrue("Default should contain root.hcl", markers.contains("root.hcl"));
        assertTrue("Default should contain terragrunt.stack.hcl", markers.contains("terragrunt.stack.hcl"));
    }

    public void testIsEntryPointDefault() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        assertTrue("terragrunt.hcl should be entry point", settings.isEntryPoint("terragrunt.hcl"));
        assertFalse("myconfig.hcl should not be entry point", settings.isEntryPoint("myconfig.hcl"));
    }

    public void testCustomEntryPoint() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        List<String> original = List.copyOf(settings.getEntryPointFilenames());
        try {
            settings.setEntryPointFilenames(List.of("terragrunt.hcl", "my-unit.hcl"));
            assertTrue("my-unit.hcl should now be entry point", settings.isEntryPoint("my-unit.hcl"));
        } finally {
            settings.setEntryPointFilenames(List.copyOf(original));
        }
    }
}
