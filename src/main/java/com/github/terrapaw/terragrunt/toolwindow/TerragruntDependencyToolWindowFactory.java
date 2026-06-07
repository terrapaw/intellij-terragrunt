package com.github.terrapaw.terragrunt.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TerragruntDependencyToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Dependencies");
        Tree tree = new Tree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DependencyTreeCellRenderer());

        // Toolbar with refresh, expand-all, collapse-all
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new AnAction("Refresh", "Rescan dependencies", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refreshTree(project, root, tree);
            }
        });
        actionGroup.add(new AnAction("Expand All", "Expand all nodes", AllIcons.Actions.Expandall) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
            }
        });
        actionGroup.add(new AnAction("Collapse All", "Collapse all nodes", AllIcons.Actions.Collapseall) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                for (int i = tree.getRowCount() - 1; i >= 0; i--) tree.collapseRow(i);
            }
        });
        actionGroup.addSeparator();
        actionGroup.add(new AnAction("Export DOT", "Export dependency graph as .dot file", AllIcons.ToolbarDecorator.Export) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                exportDot(project);
            }
        });

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TerragruntDependencies", actionGroup, true);
        toolbar.setTargetComponent(panel);
        panel.add(toolbar.getComponent(), BorderLayout.NORTH);
        panel.add(new com.intellij.ui.components.JBScrollPane(tree), BorderLayout.CENTER);

        // Double-click to navigate
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                    if (obj instanceof DependencyNodeInfo info && info.file() != null) {
                        FileEditorManager.getInstance(project).openFile(info.file(), true);
                    }
                }
            }
        });

        refreshTree(project, root, tree);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private void refreshTree(Project project, DefaultMutableTreeNode root, Tree tree) {
        root.removeAllChildren();

        List<TerragruntDependencyScanner.DependencyNode> nodes = TerragruntDependencyScanner.scanProject(project);

        Map<String, TerragruntDependencyScanner.DependencyNode> lookup = new HashMap<>();
        for (var node : nodes) {
            lookup.put(node.file().getPath(), node);
        }

        for (var node : nodes) {
            int depCount = node.dependencyPaths().size();
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                    new DependencyNodeInfo(node.displayName(), depCount, node.file(), false));

            for (String depPath : node.dependencyPaths()) {
                var depNode = lookup.get(depPath);
                String depName = depNode != null ? depNode.displayName() : depPath;
                VirtualFile depFile = depNode != null ? depNode.file() : null;
                treeNode.add(new DefaultMutableTreeNode(new DependencyNodeInfo(depName, 0, depFile, true)));
            }

            root.add(treeNode);
        }

        ((DefaultTreeModel) tree.getModel()).reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    record DependencyNodeInfo(String displayName, int depCount, VirtualFile file, boolean isDependency) {
        @Override
        public String toString() { return displayName; }
    }

    private static class DependencyTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) return;
            Object obj = node.getUserObject();
            if (!(obj instanceof DependencyNodeInfo info)) {
                append(String.valueOf(obj));
                return;
            }

            if (info.isDependency()) {
                setIcon(AllIcons.Nodes.Related);
                append(info.displayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            } else {
                setIcon(AllIcons.Nodes.Module);
                append(info.displayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                if (info.depCount() > 0) {
                    append("  " + info.depCount() + " dep" + (info.depCount() > 1 ? "s" : ""),
                            SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        }
    }

    private void exportDot(Project project) {
        // Let user choose save location
        var descriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("Export Dependency Graph");
        descriptor.setDescription("Choose directory to save dependency-graph.dot");
        var chosen = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, project.getBaseDir());
        if (chosen == null) return;

        List<TerragruntDependencyScanner.DependencyNode> nodes = TerragruntDependencyScanner.scanProject(project);
        Map<String, String> pathToName = new HashMap<>();
        for (var node : nodes) pathToName.put(node.file().getPath(), node.displayName());

        StringBuilder dot = new StringBuilder("digraph dependencies {\n  rankdir=LR;\n  node [shape=box];\n\n");
        for (var node : nodes) {
            String from = sanitizeId(node.displayName());
            dot.append("  ").append(from).append(" [label=\"").append(node.displayName()).append("\"];\n");
        }
        dot.append("\n");
        for (var node : nodes) {
            String from = sanitizeId(node.displayName());
            for (String depPath : node.dependencyPaths()) {
                String depName = pathToName.getOrDefault(depPath, depPath);
                String to = sanitizeId(depName);
                dot.append("  ").append(from).append(" -> ").append(to).append(";\n");
            }
        }
        dot.append("}\n");

        com.intellij.openapi.application.WriteAction.run(() -> {
            try {
                VirtualFile file = chosen.findOrCreateChildData(this, "dependency-graph.dot");
                file.setBinaryContent(dot.toString().getBytes());
                FileEditorManager.getInstance(project).openFile(file, true);
            } catch (java.io.IOException ignored) {}
        });
    }

    private static String sanitizeId(String name) {
        return "\"" + name.replace("\"", "\\\"") + "\"";
    }
}
