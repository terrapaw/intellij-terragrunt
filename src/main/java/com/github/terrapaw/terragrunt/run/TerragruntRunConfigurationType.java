package com.github.terrapaw.terragrunt.run;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TerragruntRunConfigurationType implements ConfigurationType {

    public static final String ID = "TerragruntRunConfiguration";

    @Override
    public @NotNull String getDisplayName() {
        return "Terragrunt";
    }

    @Override
    public String getConfigurationTypeDescription() {
        return "Run Terragrunt commands";
    }

    @Override
    public Icon getIcon() {
        return TerragruntFileType.INSTANCE.getIcon();
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{new TerragruntConfigurationFactory(this)};
    }

    private static class TerragruntConfigurationFactory extends ConfigurationFactory {
        protected TerragruntConfigurationFactory(@NotNull ConfigurationType type) {
            super(type);
        }

        @Override
        public @NotNull String getId() {
            return ID;
        }

        @Override
        public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
            return new TerragruntRunConfiguration(project, this, "Terragrunt");
        }

        @Override
        public Class<? extends BaseState> getOptionsClass() {
            return TerragruntRunConfigurationOptions.class;
        }
    }
}
