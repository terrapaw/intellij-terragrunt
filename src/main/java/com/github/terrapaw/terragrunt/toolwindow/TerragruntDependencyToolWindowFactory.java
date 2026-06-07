package com.github.terrapaw.terragrunt.toolwindow;

import com.github.terrapaw.terragrunt.run.TerragruntRunConfiguration;
import com.github.terrapaw.terragrunt.run.TerragruntRunConfigurationType;
import com.intellij.execution.RunManager;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.FilterComponent;
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
import java.util.*;
import java.util.List;

public class TerragruntDependencyToolWindowFactory implements ToolWindowFactory {

    private List<TerragruntDependencyScanner.DependencyNode> allNodes = List.of();
    private Set<String> entryPoints = Set.of();
    private boolean dirty = false;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel(new BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Dependencies");
        Tree tree = new Tree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DependencyTreeCellRenderer());

        // Search filter (declared early so toolbar can reference it)
        final FilterComponent[] filterRef = new FilterComponent[1];

        // Toolbar
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new AnAction("Refresh", "Rescan dependencies", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                scheduleRefresh(project, root, tree, filterRef[0]);
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

        // Search filter
        FilterComponent filter = new FilterComponent("TerragruntDepFilter", 10) {
            @Override
            public void filter() {
                renderTree(root, tree, getFilter());
            }
        };
        filterRef[0] = filter;

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar.getComponent(), BorderLayout.NORTH);
        topPanel.add(filter, BorderLayout.SOUTH);
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new com.intellij.ui.components.JBScrollPane(tree), BorderLayout.CENTER);

        // Double-click to navigate, right-click context menu
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected(tree, project);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    tree.setSelectionPath(path);
                    showContextMenu(tree, project, e);
                }
            }
        });

        // Auto-refresh on file changes (only when visible)
        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                boolean relevant = events.stream().anyMatch(ev ->
                        ev.getFile() != null && ev.getFile().getName().endsWith(".hcl"));
                if (relevant) {
                    if (toolWindow.isVisible()) {
                        scheduleRefresh(project, root, tree, filterRef[0]);
                    } else {
                        dirty = true;
                    }
                }
            }
        });

        // Refresh when tool window becomes visible
        toolWindow.addContentManagerListener(new com.intellij.ui.content.ContentManagerListener() {
            @Override
            public void selectionChanged(@NotNull com.intellij.ui.content.ContentManagerEvent event) {
                if (dirty && toolWindow.isVisible()) {
                    dirty = false;
                    scheduleRefresh(project, root, tree, filterRef[0]);
                }
            }
        });

        scheduleRefresh(project, root, tree, filterRef[0]);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private void scheduleRefresh(Project project, DefaultMutableTreeNode root, Tree tree, FilterComponent filter) {
        com.intellij.openapi.application.ReadAction.nonBlocking(() -> {
            refreshData(project);
            return null;
        }).finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), result -> {
            renderTree(root, tree, filter != null ? filter.getFilter() : "");
        }).submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService());
    }

    private void refreshData(Project project) {
        allNodes = TerragruntDependencyScanner.scanProject(project);
        // Entry points = units that no other unit depends on
        Set<String> allDepPaths = new HashSet<>();
        for (var node : allNodes) allDepPaths.addAll(node.dependencyPaths());
        entryPoints = new HashSet<>();
        for (var node : allNodes) {
            if (!allDepPaths.contains(node.file().getPath())) {
                entryPoints.add(node.file().getPath());
            }
        }
    }

    private void renderTree(DefaultMutableTreeNode root, Tree tree, String filterText) {
        root.removeAllChildren();
        String filter = filterText != null ? filterText.toLowerCase() : "";

        Map<String, TerragruntDependencyScanner.DependencyNode> lookup = new HashMap<>();
        for (var node : allNodes) lookup.put(node.file().getPath(), node);

        for (var node : allNodes) {
            if (!filter.isEmpty() && !node.displayName().toLowerCase().contains(filter)) continue;

            int depCount = node.dependencyPaths().size();
            boolean isEntry = entryPoints.contains(node.file().getPath());
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(
                    new DependencyNodeInfo(node.displayName(), depCount, node.file(), false, isEntry));

            for (String depPath : node.dependencyPaths()) {
                var depNode = lookup.get(depPath);
                String depName = depNode != null ? depNode.displayName() : depPath;
                VirtualFile depFile = depNode != null ? depNode.file() : null;
                treeNode.add(new DefaultMutableTreeNode(new DependencyNodeInfo(depName, 0, depFile, true, false)));
            }

            root.add(treeNode);
        }

        ((DefaultTreeModel) tree.getModel()).reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void navigateToSelected(Tree tree, Project project) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (obj instanceof DependencyNodeInfo info && info.file() != null) {
            FileEditorManager.getInstance(project).openFile(info.file(), true);
        }
    }

    private void showContextMenu(Tree tree, Project project, MouseEvent e) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;
        Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(obj instanceof DependencyNodeInfo info) || info.file() == null) return;

        JPopupMenu menu = new JPopupMenu();
        menu.add(new AbstractAction("Open File") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent ev) {
                FileEditorManager.getInstance(project).openFile(info.file(), true);
            }
        });
        menu.addSeparator();

        // Detect if this unit is inside a stack
        String filePath = info.file().getPath();
        boolean isStackUnit = filePath.contains("/.terragrunt-stack/");
        if (isStackUnit) {
            // Find the stack directory (parent of .terragrunt-stack)
            String stackDir = filePath.substring(0, filePath.indexOf("/.terragrunt-stack/"));
            for (String cmd : new String[]{"stack generate", "stack run init", "stack run plan", "stack run apply"}) {
                menu.add(new AbstractAction("Run terragrunt " + cmd) {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent ev) {
                        runCommand(project, cmd, stackDir);
                    }
                });
            }
        } else {
            for (String cmd : new String[]{"init", "plan", "apply"}) {
                menu.add(new AbstractAction("Run terragrunt " + cmd) {
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent ev) {
                        runCommand(project, cmd, info.file().getParent().getPath());
                    }
                });
            }
        }
        menu.show(tree, e.getX(), e.getY());
    }

    private void runCommand(Project project, String command, String dir) {
        var configType = com.intellij.execution.configurations.ConfigurationTypeUtil
                .findConfigurationType(TerragruntRunConfigurationType.class);
        var settings = RunManager.getInstance(project).createConfiguration(
                "terragrunt " + command, configType.getConfigurationFactories()[0]);
        var config = (TerragruntRunConfiguration) settings.getConfiguration();
        config.setCommand(command);
        config.setWorkingDirectory(dir);
        RunManager.getInstance(project).setTemporaryConfiguration(settings);
        ExecutionUtil.runConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }

    record DependencyNodeInfo(String displayName, int depCount, VirtualFile file, boolean isDependency, boolean isEntryPoint) {
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
                setIcon(info.isEntryPoint() ? AllIcons.Nodes.Deploy : AllIcons.Nodes.Module);
                append(info.displayName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                if (info.isEntryPoint()) {
                    append("  entry point", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                } else if (info.depCount() > 0) {
                    append("  " + info.depCount() + " dep" + (info.depCount() > 1 ? "s" : ""),
                            SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        }
    }

    private void exportDot(Project project) {
        var descriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("Export Dependency Graph");
        descriptor.setDescription("Choose directory to save dependency-graph.dot");
        var chosen = com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, project.getBaseDir());
        if (chosen == null) return;

        Map<String, String> pathToName = new HashMap<>();
        for (var node : allNodes) pathToName.put(node.file().getPath(), node.displayName());

        StringBuilder dot = new StringBuilder("digraph dependencies {\n  rankdir=LR;\n  node [shape=box];\n\n");
        for (var node : allNodes) {
            String id = sanitizeId(node.displayName());
            String style = entryPoints.contains(node.file().getPath()) ? " style=bold color=blue" : "";
            dot.append("  ").append(id).append(" [label=\"").append(node.displayName()).append("\"").append(style).append("];\n");
        }
        dot.append("\n");
        for (var node : allNodes) {
            String from = sanitizeId(node.displayName());
            for (String depPath : node.dependencyPaths()) {
                String depName = pathToName.getOrDefault(depPath, depPath);
                dot.append("  ").append(from).append(" -> ").append(sanitizeId(depName)).append(";\n");
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
