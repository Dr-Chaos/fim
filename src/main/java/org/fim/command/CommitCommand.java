/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
 *
 * Fim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Fim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.command;

import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.DontWantToContinueException;
import org.fim.internal.*;
import org.fim.model.*;
import org.fim.util.Console;
import org.fim.util.Logger;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.fim.model.HashMode.dontHash;
import static org.fim.model.Modification.attributesModified;
import static org.fim.model.Modification.dateModified;
import static org.fim.util.FileStateUtil.buildFileNamesMap;

public class CommitCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "commit";
    }

    @Override
    public String getShortCmdName() {
        return "ci";
    }

    @Override
    public String getDescription() {
        return "Commit the current directory State";
    }

    @Override
    public Object execute(Context context) throws Exception {
        SettingsManager settingsManager = new SettingsManager(context);
        HashMode globalHashMode = settingsManager.getGlobalHashMode();
        if (context.getHashMode() == dontHash && globalHashMode != dontHash) {
            Logger.error("Computing hash is mandatory");
            throw new BadFimUsageException();
        }
        adjustThreadCount(context);

        if (context.getComment().length() == 0) {
            System.out.println("No comment provided. You are going to commit your modifications without any comment.");
            if (!confirmAction(context, "continue")) {
                throw new DontWantToContinueException();
            }
        }

        StateManager manager = new StateManager(context);
        State currentState = new StateGenerator(context).generateState(context.getComment(), context.getRepositoryRootDir(), context.getCurrentDirectory());
        State lastState = manager.loadLastState();
        State lastStateToCompare = lastState;

        if (context.isInvokedFromSubDirectory()) {
            if (!lastState.getModelVersion().equals(currentState.getModelVersion())) {
                Logger.error("Not able to incrementally commit into the last State that use a different model version.");
                throw new BadFimUsageException();
            }

            lastStateToCompare = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), true);
        }

        CompareResult result = new StateComparator(context, lastStateToCompare, currentState).compare().displayChanges(System.out);
        if (result.somethingModified()) {
            Console.newLine();
            if (confirmAction(context, "commit")) {
                currentState.setModificationCounts(result.getModificationCounts());

                if (context.getHashMode() != dontHash && context.getHashMode() != globalHashMode) {
                    // Reload the last state with the globalHashMode in order to get a complete state.
                    context.setHashMode(globalHashMode);
                    lastState = manager.loadLastState();
                    retrieveMissingHash(context, currentState, lastState);
                }

                if (context.isInvokedFromSubDirectory()) {
                    currentState = createConsolidatedState(context, lastState, currentState);
                }

                manager.createNewState(currentState);
            } else {
                Logger.info("Nothing committed");
            }
        }

        return result;
    }

    private void retrieveMissingHash(Context context, State currentState, State lastState) throws NoSuchAlgorithmException, IOException {
        Map<String, FileState> lastFileStateMap = buildFileNamesMap(lastState.getFileStates());

        List<FileState> toReHash = new ArrayList<>();
        for (FileState fileState : currentState.getFileStates()) {
            Modification modification = fileState.getModification();
            if (modification == null || modification == attributesModified || modification == dateModified) {
                // Get in the last State the hash of the unmodified files
                FileState lastFileState = lastFileStateMap.get(fileState.getFileName());
                if (lastFileState == null) {
                    throw new IllegalStateException(String.format("Not able to find file '%s' into the previous state", fileState.getFileName()));
                }
                fileState.setFileHash(lastFileState.getFileHash());
            } else {
                // Hash changed, we need to compute all the mandatory hash
                toReHash.add(fileState);
            }
        }

        StateReGenerator stateReGenerator = new StateReGenerator(context);
        stateReGenerator.reHashFiles(toReHash);
    }

    private State createConsolidatedState(Context context, State lastState, State currentState) throws IOException {
        State filteredState = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), false);

        State consolidatedState = currentState.clone();
        consolidatedState.getFileStates().addAll(filteredState.getFileStates());
        consolidatedState.getModificationCounts().add(filteredState.getModificationCounts());
        consolidatedState.getIgnoredFiles().addAll(lastState.getIgnoredFiles());

        return consolidatedState;
    }
}
