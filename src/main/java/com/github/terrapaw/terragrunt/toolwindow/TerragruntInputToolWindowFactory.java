package com.github.terrapaw.terragrunt.toolwindow;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.intellij.icons.AllIcons;
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
    private VirtualFile pinnedFile = null;
    private java.util.concurrent.Future<?> pendingUpdate = null;

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

        JButton pinButton = new JButton("Pin", AllIcons.General.Pin_tab);
        pinButton.setToolTipText("Pin current file");
        pinButton.addActionListener(e -> {
            if (pinnedFile == null) {
                VirtualFile[] sel = FileEditorManager.getInstance(project).getSelectedFiles();
                if (sel.length > 0 && sel[0].getName().endsWith(".hcl")) {
                    pinnedFile = sel[0];
                    pinButton.setIcon(AllIcons.Nodes.Padlock);
                    pinButton.setText(pinnedFile.getName());
                    pinButton.setToolTipText("Click to unpin");
                }
            } else {
                pinnedFile = null;
                pinButton.setIcon(AllIcons.General.Pin_tab);
                pinButton.setText("Pin");
                pinButton.setToolTipText("Pin current file");
                VirtualFile[] sel = FileEditorManager.getInstance(project).getSelectedFiles();
                if (sel.length > 0) updateTable(project, model, header, sel[0]);
            }
        });

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(header, BorderLayout.CENTER);
        headerPanel.add(pinButton, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);

        // Update on file selection change
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                if (!toolWindow.isVisible()) return;
                if (pinnedFile == null) {
                    updateTable(project, model, header, event.getNewFile());
                }
            }
        });

        // Update on document changes (typing) — only when window is visible
        com.intellij.openapi.editor.EditorFactory.getInstance().getEventMulticaster()
                .addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
                    @Override
                    public void documentChanged(com.intellij.openapi.editor.event.@NotNull DocumentEvent event) {
                        if (!toolWindow.isVisible()) return;
                        VirtualFile target = pinnedFile != null ? pinnedFile :
                                FileEditorManager.getInstance(project).getSelectedFiles().length > 0 ?
                                        FileEditorManager.getInstance(project).getSelectedFiles()[0] : null;
                        if (target != null && target.getName().endsWith(".hcl")) {
                            var file = target;
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments();
                                updateTable(project, model, header, file);
                            });
                        }
                    }
                }, project);

        // Refresh when window becomes visible
        toolWindow.addContentManagerListener(new com.intellij.ui.content.ContentManagerListener() {
            @Override
            public void selectionChanged(com.intellij.ui.content.@NotNull ContentManagerEvent event) {
                if (toolWindow.isVisible()) {
                    VirtualFile target = pinnedFile != null ? pinnedFile :
                            FileEditorManager.getInstance(project).getSelectedFiles().length > 0 ?
                                    FileEditorManager.getInstance(project).getSelectedFiles()[0] : null;
                    if (target != null) updateTable(project, model, header, target);
                }
            }
        });

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

        header.setText(file.getName() + " — computing...");

        if (pendingUpdate != null && !pendingUpdate.isDone()) {
            pendingUpdate.cancel(true);
        }

        pendingUpdate = com.intellij.openapi.application.ReadAction.nonBlocking(() -> TerragruntInputResolver.resolveInputs(psiFile))
                .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), inputs -> {
                    model.setRowCount(0);
                    String prefix = pinnedFile != null ? "📌 " : "";
                    if (inputs.isEmpty()) {
                        header.setText(prefix + file.getName() + " — no inputs found");
                    } else {
                        header.setText(prefix + file.getName() + " — " + inputs.size() + " input(s)");
                        for (var entry : inputs) {
                            model.addRow(new Object[]{entry.key(), entry.value(), entry.resolved(), entry.source()});
                        }
                    }
                })
                .submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService());
    }
}
