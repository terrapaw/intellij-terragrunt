package com.github.terrapaw.terragrunt.run;

import com.github.terrapaw.terragrunt.settings.TerragruntSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class TerragruntCommandLineState extends CommandLineState {

    private final TerragruntRunConfiguration config;

    public TerragruntCommandLineState(@NotNull TerragruntRunConfiguration config, @NotNull ExecutionEnvironment env) {
        super(env);
        this.config = config;
    }

    @Override
    protected @NotNull ProcessHandler startProcess() throws ExecutionException {
        String binary = TerragruntSettings.getInstance().getEffectiveBinaryPath();
        GeneralCommandLine cmd = new GeneralCommandLine();
        cmd.setExePath(binary);

        // Add command (may be multi-word like "stack generate")
        for (String part : config.getCommand().split("\\s+")) {
            cmd.addParameter(part);
        }

        // Add additional args
        String args = config.getAdditionalArgs();
        if (args != null && !args.isBlank()) {
            for (String arg : args.split("\\s+")) {
                cmd.addParameter(arg);
            }
        }

        // Set working directory
        String workDir = config.getWorkingDirectory();
        if (workDir != null && !workDir.isBlank()) {
            cmd.setWorkDirectory(new File(workDir));
        } else {
            String basePath = config.getProject().getBasePath();
            if (basePath != null) cmd.setWorkDirectory(new File(basePath));
        }

        cmd.setRedirectErrorStream(true);

        ColoredProcessHandler handler = new ColoredProcessHandler(cmd);
        ProcessTerminatedListener.attach(handler);
        return handler;
    }
}
