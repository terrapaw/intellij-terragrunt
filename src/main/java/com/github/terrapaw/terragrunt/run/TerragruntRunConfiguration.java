package com.github.terrapaw.terragrunt.run;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class TerragruntRunConfiguration extends RunConfigurationBase<TerragruntRunConfigurationOptions> {

    protected TerragruntRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @NotNull String name) {
        super(project, factory, name);
    }

    @Override
    protected @NotNull TerragruntRunConfigurationOptions getOptions() {
        return (TerragruntRunConfigurationOptions) super.getOptions();
    }

    public String getCommand() { return getOptions().getCommand(); }
    public void setCommand(String command) { getOptions().setCommand(command); }

    public String getWorkingDirectory() { return getOptions().getWorkingDirectory(); }
    public void setWorkingDirectory(String dir) { getOptions().setWorkingDirectory(dir); }

    public String getAdditionalArgs() { return getOptions().getAdditionalArgs(); }
    public void setAdditionalArgs(String args) { getOptions().setAdditionalArgs(args); }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new TerragruntSettingsEditor();
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
        return new TerragruntCommandLineState(this, environment);
    }

    private static class TerragruntSettingsEditor extends SettingsEditor<TerragruntRunConfiguration> {
        private JComboBox<String> commandCombo;
        private JTextField workingDirField;
        private JTextField additionalArgsField;

        @Override
        protected @NotNull JComponent createEditor() {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(4, 4, 4, 4);

            c.gridx = 0; c.gridy = 0; c.weightx = 0;
            panel.add(new JLabel("Command:"), c);
            c.gridx = 1; c.weightx = 1;
            commandCombo = new JComboBox<>(new String[]{"plan", "apply", "init", "validate", "destroy", "output", "stack generate", "stack run plan", "stack run apply"});
            commandCombo.setEditable(true);
            panel.add(commandCombo, c);

            c.gridx = 0; c.gridy = 1; c.weightx = 0;
            panel.add(new JLabel("Working directory:"), c);
            c.gridx = 1; c.weightx = 1;
            workingDirField = new JTextField();
            panel.add(workingDirField, c);

            c.gridx = 0; c.gridy = 2; c.weightx = 0;
            panel.add(new JLabel("Additional arguments:"), c);
            c.gridx = 1; c.weightx = 1;
            additionalArgsField = new JTextField();
            panel.add(additionalArgsField, c);

            return panel;
        }

        @Override
        protected void resetEditorFrom(@NotNull TerragruntRunConfiguration config) {
            commandCombo.setSelectedItem(config.getCommand());
            workingDirField.setText(config.getWorkingDirectory());
            additionalArgsField.setText(config.getAdditionalArgs());
        }

        @Override
        protected void applyEditorTo(@NotNull TerragruntRunConfiguration config) {
            config.setCommand((String) commandCombo.getSelectedItem());
            config.setWorkingDirectory(workingDirField.getText());
            config.setAdditionalArgs(additionalArgsField.getText());
        }
    }
}
