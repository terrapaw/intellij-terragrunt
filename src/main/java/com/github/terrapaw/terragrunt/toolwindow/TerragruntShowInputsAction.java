package com.github.terrapaw.terragrunt.toolwindow;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class TerragruntShowInputsAction extends AnAction {

    public TerragruntShowInputsAction() {
        super("Terragrunt Inputs", "Open the Terragrunt Inputs tool window", null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        boolean visible = file != null && file.getFileType() == TerragruntFileType.INSTANCE;
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        var toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terragrunt Inputs");
        if (toolWindow != null) {
            toolWindow.show();
        }
    }
}
