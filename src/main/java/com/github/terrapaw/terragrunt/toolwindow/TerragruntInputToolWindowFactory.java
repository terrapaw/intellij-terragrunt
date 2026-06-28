package com.github.terrapaw.terragrunt.toolwindow;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class TerragruntInputToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        String[] columns = {"Key", "Raw Value", "Computed Value", "Source"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JBTable table = new JBTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);

        JLabel header = new JLabel("Open a Terragrunt file to see computed inputs");
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        // Update on file selection change
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                updateTable(project, model, header, event.getNewFile());
            }
        });

        // Update on document changes (typing)
        com.intellij.openapi.editor.EditorFactory.getInstance().getEventMulticaster()
                .addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
                    @Override
                    public void documentChanged(com.intellij.openapi.editor.event.@NotNull DocumentEvent event) {
                        VirtualFile[] selected = FileEditorManager.getInstance(project).getSelectedFiles();
                        if (selected.length > 0 && selected[0].getName().endsWith(".hcl")) {
                            var file = selected[0];
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments();
                                updateTable(project, model, header, file);
                            });
                        }
                    }
                }, project);

        // Initial update
        VirtualFile[] selected = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selected.length > 0) {
            updateTable(project, model, header, selected[0]);
        }
    }

    private void updateTable(Project project, DefaultTableModel model, JLabel header, VirtualFile file) {
        model.setRowCount(0);
        if (file == null || !file.getName().endsWith(".hcl")) {
            header.setText("Open a Terragrunt file to see computed inputs");
            return;
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null || psiFile.getFileType() != TerragruntFileType.INSTANCE) {
            header.setText("Not a Terragrunt file");
            return;
        }

        List<TerragruntInputResolver.InputEntry> inputs = com.intellij.openapi.application.ReadAction.compute(
                () -> TerragruntInputResolver.resolveInputs(psiFile));

        if (inputs.isEmpty()) {
            header.setText(file.getName() + " — no inputs found");
        } else {
            header.setText(file.getName() + " — " + inputs.size() + " input(s)");
            for (var entry : inputs) {
                model.addRow(new Object[]{entry.key(), entry.value(), entry.resolved(), entry.source()});
            }
        }
    }
}
