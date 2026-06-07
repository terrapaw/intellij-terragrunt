package com.github.terrapaw.terragrunt.run;

import com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock;
import com.github.terrapaw.terragrunt.lang.psi.TerragruntBody;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TerragruntRunLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        String fileName = file.getName();

        // Only trigger on the first block's identifier
        TerragruntBody body = PsiTreeUtil.getChildOfType(file, TerragruntBody.class);
        if (body == null) return null;
        TerragruntBlock firstBlock = PsiTreeUtil.getChildOfType(body, TerragruntBlock.class);
        if (firstBlock == null || firstBlock.getIdentifier() != element) return null;

        if ("terragrunt.hcl".equals(fileName)) {
            return createMarker(element, "Run Terragrunt...", new String[]{"init", "plan", "apply"});
        } else if ("terragrunt.stack.hcl".equals(fileName)) {
            return createMarker(element, "Run Terragrunt Stack...", new String[]{"stack generate", "stack run init", "stack run plan", "stack run apply"});
        }
        return null;
    }

    private LineMarkerInfo<?> createMarker(PsiElement element, String tooltip, String[] commands) {
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.RunConfigurations.TestState.Run,
                e -> tooltip,
                (e, elt) -> {
                    PsiFile file = elt.getContainingFile();
                    if (file == null || file.getVirtualFile() == null || file.getVirtualFile().getParent() == null) return;
                    String dir = file.getVirtualFile().getParent().getPath();
                    var project = elt.getProject();

                    // Show popup with command options
                    var popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                            .createPopupChooserBuilder(java.util.List.of(commands))
                            .setTitle("Terragrunt")
                            .setRenderer(new com.intellij.ui.ColoredListCellRenderer<String>() {
                                @Override
                                protected void customizeCellRenderer(@NotNull javax.swing.JList<? extends String> list,
                                        String value, int index, boolean selected, boolean hasFocus) {
                                    setIcon(AllIcons.RunConfigurations.TestState.Run);
                                    append("terragrunt " + value);
                                }
                            })
                            .setItemChosenCallback(command -> runCommand(project, command, dir))
                            .createPopup();
                    popup.show(com.intellij.ui.awt.RelativePoint.fromScreen(e.getLocationOnScreen()));
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> tooltip
        );
    }

    private void runCommand(com.intellij.openapi.project.Project project, String command, String dir) {
        RunManager runManager = RunManager.getInstance(project);
        var configType = com.intellij.execution.configurations.ConfigurationTypeUtil
                .findConfigurationType(TerragruntRunConfigurationType.class);
        RunnerAndConfigurationSettings settings = runManager.createConfiguration(
                "terragrunt " + command, configType.getConfigurationFactories()[0]);
        TerragruntRunConfiguration config = (TerragruntRunConfiguration) settings.getConfiguration();
        config.setCommand(command);
        config.setWorkingDirectory(dir);

        runManager.setTemporaryConfiguration(settings);
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();
        ExecutionUtil.runConfiguration(settings, executor);
    }
}
