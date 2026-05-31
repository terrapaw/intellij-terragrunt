package com.github.terrapaw.terragrunt.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TerragruntSettingsConfigurable implements Configurable {
    private JPanel mainPanel;
    private DefaultListModel<String> listModel;

    @Nls
    @Override
    public String getDisplayName() {
        return "Terragrunt";
    }

    @Override
    public @Nullable JComponent createComponent() {
        listModel = new DefaultListModel<>();
        TerragruntSettings settings = TerragruntSettings.getInstance();
        for (String name : settings.getEntryPointFilenames()) {
            listModel.addElement(name);
        }

        JBList<String> list = new JBList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(list)
                .setAddAction(button -> {
                    String input = JOptionPane.showInputDialog(mainPanel, "Entry point filename:", "terragrunt.hcl");
                    if (input != null && !input.isBlank() && !listModel.contains(input)) {
                        listModel.addElement(input.trim());
                    }
                })
                .setRemoveAction(button -> {
                    int index = list.getSelectedIndex();
                    if (index >= 0) listModel.remove(index);
                });

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(new JLabel("Entry point filenames (files treated as Terragrunt children):"), BorderLayout.NORTH);
        mainPanel.add(decorator.createPanel(), BorderLayout.CENTER);
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        return !getListItems().equals(settings.getEntryPointFilenames());
    }

    @Override
    public void apply() {
        TerragruntSettings.getInstance().setEntryPointFilenames(getListItems());
    }

    @Override
    public void reset() {
        listModel.clear();
        for (String name : TerragruntSettings.getInstance().getEntryPointFilenames()) {
            listModel.addElement(name);
        }
    }

    private List<String> getListItems() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            items.add(listModel.get(i));
        }
        return items;
    }
}
