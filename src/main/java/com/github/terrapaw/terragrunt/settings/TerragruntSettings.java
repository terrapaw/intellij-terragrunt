package com.github.terrapaw.terragrunt.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(name = "TerragruntSettings", storages = @Storage("terragrunt.xml"))
public class TerragruntSettings implements PersistentStateComponent<TerragruntSettings.State> {
    private State myState = new State();

    public static TerragruntSettings getInstance() {
        return ApplicationManager.getApplication().getService(TerragruntSettings.class);
    }

    public List<String> getEntryPointFilenames() {
        return myState.entryPointFilenames;
    }

    public void setEntryPointFilenames(List<String> filenames) {
        myState.entryPointFilenames = new ArrayList<>(filenames);
    }

    public boolean isEntryPoint(String filename) {
        return myState.entryPointFilenames.contains(filename);
    }

    public List<String> getMarkerFilenames() {
        return myState.markerFilenames;
    }

    public void setMarkerFilenames(List<String> filenames) {
        myState.markerFilenames = new ArrayList<>(filenames);
    }

    @Override
    public @NotNull State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public static class State {
        public List<String> entryPointFilenames = new ArrayList<>(List.of("terragrunt.hcl"));
        public List<String> markerFilenames = new ArrayList<>(List.of("terragrunt.hcl", "root.hcl", "terragrunt.stack.hcl"));
    }
}
