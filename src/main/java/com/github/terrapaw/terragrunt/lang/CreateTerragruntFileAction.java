package com.github.terrapaw.terragrunt.lang;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;

public class CreateTerragruntFileAction extends CreateFileFromTemplateAction {

    public CreateTerragruntFileAction() {
        super("Terragrunt File", "Create a new Terragrunt HCL file", TerragruntFileType.INSTANCE.getIcon());
    }

    @Override
    protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
        builder.setTitle("New Terragrunt File")
                .addKind("Unit (terragrunt.hcl)", TerragruntFileType.INSTANCE.getIcon(), "Terragrunt Unit")
                .addKind("Root (root.hcl)", TerragruntFileType.INSTANCE.getIcon(), "Terragrunt Root")
                .addKind("Stack (terragrunt.stack.hcl)", TerragruntFileType.INSTANCE.getIcon(), "Terragrunt Stack");
    }

    @Override
    protected String getActionName(PsiDirectory directory, String newName, String templateName) {
        return "Create Terragrunt File: " + newName;
    }
}
