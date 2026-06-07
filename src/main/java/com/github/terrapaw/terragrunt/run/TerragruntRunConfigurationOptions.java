package com.github.terrapaw.terragrunt.run;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;

public class TerragruntRunConfigurationOptions extends RunConfigurationOptions {
    private final StoredProperty<String> command = string("plan").provideDelegate(this, "command");
    private final StoredProperty<String> workingDirectory = string("").provideDelegate(this, "workingDirectory");
    private final StoredProperty<String> additionalArgs = string("").provideDelegate(this, "additionalArgs");

    public String getCommand() { return command.getValue(this); }
    public void setCommand(String value) { command.setValue(this, value); }

    public String getWorkingDirectory() { return workingDirectory.getValue(this); }
    public void setWorkingDirectory(String value) { workingDirectory.setValue(this, value); }

    public String getAdditionalArgs() { return additionalArgs.getValue(this); }
    public void setAdditionalArgs(String value) { additionalArgs.setValue(this, value); }
}
