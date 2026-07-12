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
        String[] columns = {"Input", "Raw Value", "Computed Value", "Source"};
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

        // Detail panel: shows full value when a row is selected
        javax.swing.JTextArea detailArea = new javax.swing.JTextArea(4, 40);
        detailArea.setEditable(false);
        detailArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Value Detail"));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row >= 0 && row < model.getRowCount()) {
                String computed = String.valueOf(model.getValueAt(row, 2));
                detailArea.setText(computed);
                detailArea.setCaretPosition(0);
            } else {
                detailArea.setText("");
            }
        });

        // Copy selected cell value (not entire row) on Ctrl+C
        table.setCellSelectionEnabled(true);
        javax.swing.KeyStroke copy = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        table.getInputMap().put(copy, "copyCell");
        table.getActionMap().put("copyCell", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int[] selectedRows = table.getSelectedRows();
                int[] selectedCols = table.getSelectedColumns();
                if (selectedRows.length == 0 || selectedCols.length == 0) return;

                String value;
                if (selectedCols.length == model.getColumnCount() && selectedRows.length == 1) {
                    // Full row selected — copy all columns tab-separated
                    int row = selectedRows[0];
                    StringBuilder sb = new StringBuilder();
                    for (int col = 0; col < model.getColumnCount(); col++) {
                        if (col > 0) sb.append("\t");
                        sb.append(String.valueOf(model.getValueAt(row, col)));
                    }
                    value = sb.toString();
                } else if (selectedRows.length == model.getRowCount() && selectedCols.length == 1) {
                    // Full column selected — copy all rows newline-separated
                    int col = selectedCols[0];
                    StringBuilder sb = new StringBuilder();
                    for (int row : selectedRows) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(String.valueOf(model.getValueAt(row, col)));
                    }
                    value = sb.toString();
                } else if (selectedRows.length == 1 && selectedCols.length == 1) {
                    // Single cell
                    value = String.valueOf(model.getValueAt(selectedRows[0], selectedCols[0]));
                } else {
                    // Multi-selection — copy as grid
                    StringBuilder sb = new StringBuilder();
                    for (int row : selectedRows) {
                        if (sb.length() > 0) sb.append("\n");
                        for (int i = 0; i < selectedCols.length; i++) {
                            if (i > 0) sb.append("\t");
                            sb.append(String.valueOf(model.getValueAt(row, selectedCols[i])));
                        }
                    }
                    value = sb.toString();
                }
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(value);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            }
        });

        // Double-click selects entire row and copies all columns
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        table.setRowSelectionInterval(row, row);
                        table.setColumnSelectionInterval(0, model.getColumnCount() - 1);
                        StringBuilder sb = new StringBuilder();
                        for (int col = 0; col < model.getColumnCount(); col++) {
                            if (col > 0) sb.append("\t");
                            sb.append(String.valueOf(model.getValueAt(row, col)));
                        }
                        java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(sb.toString());
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                    }
                }
            }
        });

        // Click column header to select entire column
        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col >= 0) {
                    table.setRowSelectionInterval(0, model.getRowCount() - 1);
                    table.setColumnSelectionInterval(col, col);
                }
            }
        });

        // Right-click context menu with "Jump to Input"
        javax.swing.JPopupMenu popupMenu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem jumpItem = new javax.swing.JMenuItem("Jump to Input");
        jumpItem.addActionListener(ev -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String inputName = String.valueOf(model.getValueAt(row, 0));
            String source = String.valueOf(model.getValueAt(row, 3));
            VirtualFile targetFile;
            if ("current file".equals(source)) {
                VirtualFile[] sel = FileEditorManager.getInstance(project).getSelectedFiles();
                targetFile = pinnedFile != null ? pinnedFile : (sel.length > 0 ? sel[0] : null);
            } else {
                // Source is like 'include "root"' — resolve include
                targetFile = pinnedFile != null ? pinnedFile :
                        (FileEditorManager.getInstance(project).getSelectedFiles().length > 0 ?
                                FileEditorManager.getInstance(project).getSelectedFiles()[0] : null);
            }
            if (targetFile == null) return;
            PsiFile psiFile = PsiManager.getInstance(project).findFile(targetFile);
            if (psiFile == null) return;
            // Find the input key in the file
            navigateToInput(project, psiFile, inputName, source);
        });
        popupMenu.add(jumpItem);
        table.setComponentPopupMenu(popupMenu);

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

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), detailScroll);
        splitPane.setResizeWeight(0.7);
        panel.add(splitPane, BorderLayout.CENTER);

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

    private void navigateToInput(Project project, PsiFile file, String inputName, String source) {
        PsiFile targetFile = file;
        // If source is an include, resolve to the included file
        if (source.startsWith("include")) {
            String includeName = source.replaceAll("include\\s*\"([^\"]+)\"", "$1");
            for (com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock block :
                    com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, com.github.terrapaw.terragrunt.lang.psi.TerragruntBlock.class)) {
                if (!"include".equals(com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil.getBlockType(block))) continue;
                var labels = block.getLabelList();
                if (!labels.isEmpty() && includeName.equals(com.github.terrapaw.terragrunt.lang.TerragruntPsiUtil.getLabelText(labels.get(0)))) {
                    PsiFile resolved = com.github.terrapaw.terragrunt.reference.TerragruntFileResolver.resolveInclude(block);
                    if (resolved != null) targetFile = resolved;
                    break;
                }
            }
        }

        // Find the input key in the inputs block
        for (com.github.terrapaw.terragrunt.lang.psi.TerragruntAttribute attr :
                TerragruntInputResolver.getTopLevelAttributes(targetFile)) {
            if (!"inputs".equals(attr.getIdentifier().getText())) continue;
            var obj = com.intellij.psi.util.PsiTreeUtil.findChildOfType(attr, com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectExpr.class);
            if (obj == null) continue;
            for (var elem : com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList(obj, com.github.terrapaw.terragrunt.lang.psi.TerragruntObjectElem.class)) {
                var id = elem.getIdentifier();
                if (id != null && inputName.equals(id.getText())) {
                    // Navigate
                    com.intellij.openapi.fileEditor.OpenFileDescriptor descriptor = new com.intellij.openapi.fileEditor.OpenFileDescriptor(
                            project, targetFile.getVirtualFile(), id.getTextOffset());
                    descriptor.navigate(true);
                    return;
                }
            }
        }
    }
}
