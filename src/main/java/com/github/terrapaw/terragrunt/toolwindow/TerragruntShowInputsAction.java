package com.github.terrapaw.terragrunt.toolwindow;

import com.github.terrapaw.terragrunt.lang.TerragruntFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class TerragruntShowInputsAction extends AnAction {

    public TerragruntShowInputsAction() {
        super("Show Computed Inputs", "Show the resolved inputs for this Terragrunt file", null);
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
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (file == null || editor == null) return;

        List<TerragruntInputResolver.InputEntry> inputs = TerragruntInputResolver.resolveInputs(file);

        if (inputs.isEmpty()) {
            JBPopupFactory.getInstance()
                    .createMessage("No inputs found in this file or its includes.")
                    .showInBestPositionFor(editor);
            return;
        }

        // Build table
        String[] columns = {"Key", "Value", "Source"};
        Object[][] data = new Object[inputs.size()][3];
        for (int i = 0; i < inputs.size(); i++) {
            var entry = inputs.get(i);
            data[i][0] = entry.key();
            data[i][1] = entry.value();
            data[i][2] = entry.source();
        }

        DefaultTableModel model = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JBTable table = new JBTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(650, Math.min(400, 30 + inputs.size() * 24)));

        JBPopupFactory.getInstance()
                .createComponentPopupBuilder(scrollPane, table)
                .setTitle("Computed Inputs — " + file.getName())
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .createPopup()
                .showInBestPositionFor(editor);
    }
}
