/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2015  Etienne Vrignaud
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.fim.Main;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.Parameters;
import org.fim.model.State;
import org.fim.util.Logger;

public class RemoveMirroredFilesCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "rm-mirrored-files";
	}

	@Override
	public String getShortCmdName()
	{
		return "rmirror";
	}

	@Override
	public String getDescription()
	{
		return "Remove mirrored files from local directory based on a master Fim repository";
	}

	@Override
	public FimReposConstraint getFimReposConstraint()
	{
		return FimReposConstraint.DONT_CARE;
	}

	@Override
	public void execute(Parameters parameters) throws IOException, NoSuchAlgorithmException
	{
		if (parameters.getMasterFimRepositoryDir() == null)
		{
			System.err.println("The master Fim directory must be provided");
			Main.printUsage();
			System.exit(-1);
		}

		fileContentHashingMandatory(parameters);

		if (parameters.getHashMode() == HashMode.HASH_ONLY_FIRST_FOUR_KILO)
		{
			System.out.println("You are going to detect mirrored files and remove them based only on the hash of the first four kilo of the files.");
			if (!confirmAction(parameters, "continue"))
			{
				System.exit(0);
			}
		}

		if (parameters.getHashMode() == HashMode.HASH_ONLY_FIRST_MEGA)
		{
			System.out.println("You are going to detect mirrored files and remove them based only on the hash of the first mega of the files.");
			if (!confirmAction(parameters, "continue"))
			{
				System.exit(0);
			}
		}

		Path masterFimRepository = Paths.get(parameters.getMasterFimRepositoryDir());
		if (!Files.exists(masterFimRepository))
		{
			System.err.printf("Directory %s does not exist%n", parameters.getMasterFimRepositoryDir());
			System.exit(-1);
		}

		if (masterFimRepository.normalize().equals(CURRENT_DIRECTORY.normalize()))
		{
			System.err.printf("Cannot remove mirrored files from the current directory%n");
			System.exit(-1);
		}

		Path masterDotFimDir = masterFimRepository.resolve(Parameters.DOT_FIM_DIR);
		if (!Files.exists(masterDotFimDir))
		{
			System.err.printf("Directory %s is not a Fim repository%n", parameters.getMasterFimRepositoryDir());
			System.exit(-1);
		}

		System.out.println("Searching for mirrored files files using the " + parameters.getMasterFimRepositoryDir() + " directory as master");
		System.out.println("");

		Path masterStateDir = masterDotFimDir.resolve("states");
		State masterState = new StateManager(parameters, masterStateDir).loadLastState();
		Map<FileHash, FileState> masterFilesHash = buildFileHashMap(masterState);

		long totalFilesRemoved = 0;
		State localState = new StateGenerator(parameters).generateState("", CURRENT_DIRECTORY);
		for (FileState localFileState : localState.getFileStates())
		{
			FileState masterFileState = masterFilesHash.get(localFileState.getFileHash());
			if (masterFileState != null)
			{
				System.out.printf("'%s' is a mirror of '%s/%s'%n", localFileState.getFileName(),
						parameters.getMasterFimRepositoryDir(), masterFileState.getFileName());
				if (confirmAction(parameters, "remove it"))
				{
					Path localFile = Paths.get(localFileState.getFileName());
					try
					{
						Files.delete(localFile);
						System.out.printf("  '%s' removed%n", localFileState.getFileName());
						totalFilesRemoved++;
					}
					catch (IOException ex)
					{
						Logger.error(String.format("Error deleting file '%s'", localFile), ex);
					}
				}
			}
		}

		System.out.printf("%nRemoved %d mirrored files%n", totalFilesRemoved);
	}

	private Map<FileHash, FileState> buildFileHashMap(State state)
	{
		Map<FileHash, FileState> filesHashMap = new HashMap<>();
		for (FileState fileState : state.getFileStates())
		{
			filesHashMap.put(fileState.getFileHash(), fileState);
		}
		return filesHashMap;
	}
}