package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.settings.TerragruntSettings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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

    public void testCustomEntryPointAffectsResolution() {
        // my-unit.hcl is a custom entry point — get_parent_terragrunt_dir() should resolve via include
        TerragruntSettings settings = TerragruntSettings.getInstance();
        List<String> original = List.copyOf(settings.getEntryPointFilenames());
        try {
            settings.setEntryPointFilenames(List.of("terragrunt.hcl", "my-unit.hcl"));

            myFixture.addFileToProject("root.hcl", "");
            myFixture.addFileToProject("parent-cfg.hcl", """
                    locals {
                      team = "platform"
                    }
                    """);

            // my-unit.hcl is an entry point — it includes parent-cfg.hcl
            PsiFile unitFile = myFixture.addFileToProject("app/my-unit.hcl", """
                    include "parent" {
                      path = "../parent-cfg.hcl"
                    }

                    locals {
                      dir = get_parent_terragrunt_dir()
                    }
                    """);

            // Since my-unit.hcl is an entry point, get_parent_terragrunt_dir() should resolve
            // via include (rule 2) — returning parent-cfg.hcl's directory
            myFixture.configureFromExistingVirtualFile(unitFile.getVirtualFile());
            // Verify the include resolves (proves the file is treated as a child)
            var blocks = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
                    myFixture.getFile(), com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock.class);
            com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock includeBlock = null;
            for (var block : blocks) {
                if ("include".equals(block.getIdentifier().getText())) {
                    includeBlock = block;
                    break;
                }
            }
            assertNotNull("Should find include block", includeBlock);
            PsiFile resolved = com.github.terrapaw.terragrunt.reference.TerragruntFileResolver.resolveInclude(includeBlock);
            assertNotNull("Include should resolve", resolved);
        } finally {
            settings.setEntryPointFilenames(List.copyOf(original));
        }
    }

    public void testCustomMarkerAffectsDetection() {
        // Add a custom marker — files near it should be detected as Terragrunt
        TerragruntSettings settings = TerragruntSettings.getInstance();
        List<String> originalMarkers = List.copyOf(settings.getMarkerFilenames());
        try {
            settings.setMarkerFilenames(List.of("terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl", "my-marker.hcl"));

            // Create the custom marker
            myFixture.addFileToProject("custom-proj/my-marker.hcl", "locals {}");
            // Create a generic .hcl file next to it
            PsiFile genericFile = myFixture.addFileToProject("custom-proj/config.hcl", "locals { x = 1 }");

            // config.hcl should be detected as Terragrunt because my-marker.hcl is nearby
            assertEquals("config.hcl near custom marker should be Terragrunt",
                    com.github.terrapaw.terragrunt.lang.TerragruntFileType.INSTANCE,
                    genericFile.getFileType());
        } finally {
            settings.setMarkerFilenames(List.copyOf(originalMarkers));
        }
    }

    public void testNonEntryPointReturnsSelfForGetParentDir() {
        // A file NOT in the entry points list is treated as a parent config
        // get_parent_terragrunt_dir() should return its own directory
        TerragruntSettings settings = TerragruntSettings.getInstance();
        assertFalse("myconfig.hcl should not be entry point", settings.isEntryPoint("myconfig.hcl"));

        myFixture.addFileToProject("root.hcl", "");
        myFixture.addFileToProject("env.hcl", """
                locals {
                  region = "us-east-1"
                }
                """);

        // myconfig.hcl is NOT an entry point — get_parent_terragrunt_dir() returns own dir
        PsiFile parentConfig = myFixture.addFileToProject("myconfig.hcl", """
                locals {
                  env = read_terragrunt_config("${get_parent_terragrunt_dir()}/env.hcl")
                  region = local.env.locals.region
                }
                """);

        myFixture.configureFromExistingVirtualFile(parentConfig.getVirtualFile());
        String text = myFixture.getEditor().getDocument().getText();
        int offset = text.indexOf("locals.region") + "locals.".length();
        PsiElement element = myFixture.getFile().findElementAt(offset);

        var handler = new com.github.terrapaw.terragrunt.reference.TerragruntGotoDeclarationHandler();
        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, myFixture.getEditor());
        assertNotNull("Should resolve via get_parent_terragrunt_dir() returning own dir", targets);
        assertTrue("Should find target", targets.length > 0);
    }
}
