package com.github.terrapaw.terragrunt.toolwindow;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
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

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Terragrunt Dependencies");
        Tree tree = new Tree(root);
        tree.setCellRenderer(new DependencyTreeCellRenderer());

        // Refresh button
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshTree(project, root, tree));

        panel.add(refreshButton, BorderLayout.NORTH);
        panel.add(new com.intellij.ui.components.JBScrollPane(tree), BorderLayout.CENTER);

        // Double-click to navigate
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    var path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                    if (node instanceof DependencyNodeInfo info && info.file() != null) {
                        FileEditorManager.getInstance(project).openFile(info.file(), true);
                    }
                }
            }
        });

        // Initial load
        refreshTree(project, root, tree);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private void refreshTree(Project project, DefaultMutableTreeNode root, Tree tree) {
        root.removeAllChildren();

        List<TerragruntDependencyScanner.DependencyNode> nodes = TerragruntDependencyScanner.scanProject(project);

        // Build a lookup by file path
        Map<String, TerragruntDependencyScanner.DependencyNode> lookup = new HashMap<>();
        for (var node : nodes) {
            lookup.put(node.file().getPath(), node);
        }

        for (var node : nodes) {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                    new DependencyNodeInfo(node.displayName(), node.file()));

            for (String depPath : node.dependencyPaths()) {
                var depNode = lookup.get(depPath);
                String depName = depNode != null ? depNode.displayName() : depPath;
                VirtualFile depFile = depNode != null ? depNode.file() : null;
                treeNode.add(new DefaultMutableTreeNode(new DependencyNodeInfo("→ " + depName, depFile)));
            }

            root.add(treeNode);
        }

        ((DefaultTreeModel) tree.getModel()).reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    record DependencyNodeInfo(String displayName, VirtualFile file) {
        @Override
        public String toString() { return displayName; }
    }

    private static class DependencyTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof DependencyNodeInfo info) {
                if (info.displayName().startsWith("→ ")) {
                    setIcon(com.intellij.icons.AllIcons.Nodes.Related);
                } else {
                    setIcon(com.intellij.icons.AllIcons.Nodes.Module);
                }
            }
            return this;
        }
    }
}
