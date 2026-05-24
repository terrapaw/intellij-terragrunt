package com.github.terrapaw.terragrunt;

import com.github.terrapaw.terragrunt.schema.TerragruntSchema;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TerragruntSchemaTest extends BasePlatformTestCase {

    public void testKnowsTerragruntBlocks() {
        assertTrue(TerragruntSchema.isKnownBlock("terraform"));
        assertTrue(TerragruntSchema.isKnownBlock("dependency"));
        assertTrue(TerragruntSchema.isKnownBlock("include"));
        assertTrue(TerragruntSchema.isKnownBlock("generate"));
        assertTrue(TerragruntSchema.isKnownBlock("feature"));
        assertFalse(TerragruntSchema.isKnownBlock("foobar"));
    }

    public void testReturnsRequiredAttributes() {
        var dep = TerragruntSchema.getBlock("dependency");
        assertNotNull(dep);
        assertTrue("dependency should have required config_path",
                dep.attributes().stream().anyMatch(a -> a.name().equals("config_path") && a.required()));
    }

    public void testHasFunctions() {
        var functions = TerragruntSchema.getFunctions();
        assertFalse(functions.isEmpty());
        assertTrue(functions.stream().anyMatch(f -> f.name().equals("find_in_parent_folders")));
        assertTrue(functions.stream().anyMatch(f -> f.name().equals("get_env")));
        assertTrue(functions.stream().anyMatch(f -> f.name().equals("read_terragrunt_config")));
    }

    public void testDeprecatedAttributes() {
        assertTrue(TerragruntSchema.isDeprecated("mock_outputs_merge_with_state"));
        assertFalse(TerragruntSchema.isDeprecated("config_path"));
    }
}
