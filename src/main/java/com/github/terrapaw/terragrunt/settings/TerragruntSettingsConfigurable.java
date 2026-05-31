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
    private DefaultListModel<String> entryPointModel;
    private DefaultListModel<String> markerModel;

    @Nls
    @Override
    public String getDisplayName() {
        return "Terragrunt";
    }

    @Override
    public @Nullable JComponent createComponent() {
        TerragruntSettings settings = TerragruntSettings.getInstance();

        entryPointModel = new DefaultListModel<>();
        for (String name : settings.getEntryPointFilenames()) entryPointModel.addElement(name);

        markerModel = new DefaultListModel<>();
        for (String name : settings.getMarkerFilenames()) markerModel.addElement(name);

        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(createListPanel("Entry point filenames (files treated as Terragrunt children):", entryPointModel));
        mainPanel.add(Box.createVerticalStrut(12));
        mainPanel.add(createListPanel("Marker filenames (presence indicates a Terragrunt project):", markerModel));

        return mainPanel;
    }

    private JPanel createListPanel(String label, DefaultListModel<String> model) {
        JBList<String> list = new JBList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(list)
                .setAddAction(button -> {
                    String input = JOptionPane.showInputDialog(mainPanel, "Filename:");
                    if (input != null && !input.isBlank() && !model.contains(input)) {
                        model.addElement(input.trim());
                    }
                })
                .setRemoveAction(button -> {
                    int index = list.getSelectedIndex();
                    if (index >= 0) model.remove(index);
                });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(decorator.createPanel(), BorderLayout.CENTER);
        return panel;
    }

    @Override
    public boolean isModified() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        return !getListItems(entryPointModel).equals(settings.getEntryPointFilenames())
                || !getListItems(markerModel).equals(settings.getMarkerFilenames());
    }

    @Override
    public void apply() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        settings.setEntryPointFilenames(getListItems(entryPointModel));
        settings.setMarkerFilenames(getListItems(markerModel));
    }

    @Override
    public void reset() {
        TerragruntSettings settings = TerragruntSettings.getInstance();
        entryPointModel.clear();
        for (String name : settings.getEntryPointFilenames()) entryPointModel.addElement(name);
        markerModel.clear();
        for (String name : settings.getMarkerFilenames()) markerModel.addElement(name);
    }

    private List<String> getListItems(DefaultListModel<String> model) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) items.add(model.get(i));
        return items;
    }
}
