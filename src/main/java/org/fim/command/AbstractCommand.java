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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.fim.internal.SettingsManager;
import org.fim.internal.StateGenerator;
import org.fim.model.Command;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.util.Logger;

public abstract class AbstractCommand implements Command
{
	protected static final Path CURRENT_DIRECTORY = Paths.get(".");

	@Override
	public FimReposConstraint getFimReposConstraint()
	{
		return FimReposConstraint.MUST_EXIST;
	}

	protected void fileContentHashingMandatory(Context context)
	{
		if (context.getHashMode() == HashMode.dontHash)
		{
			Logger.error("File content hashing mandatory for this command.");
			System.exit(-1);
		}
	}

	protected void checkHashMode(Context context, Option... options)
	{
		List<Option> optionList = Arrays.asList(options);
		SettingsManager settingsManager = new SettingsManager(context);
		if (settingsManager.getGlobalHashMode() != HashMode.hashAll)
		{
			if (isCompatible(settingsManager.getGlobalHashMode(), context.getHashMode()))
			{
				if (optionList.contains(Option.ALLOW_COMPATIBLE))
				{
					Logger.info(String.format("Using global hash mode '%s' that is compatible with the current one", StateGenerator.hashModeToString(settingsManager.getGlobalHashMode())));
				}
				else
				{
					Logger.warning(String.format("Using global hash mode '%s' that is compatible with the current one, but is not allowed by this command. Hash mode forced",
							StateGenerator.hashModeToString(settingsManager.getGlobalHashMode())));
					context.setHashMode(settingsManager.getGlobalHashMode());
				}
			}
			else
			{
				Logger.warning(String.format("Using global hash mode '%s' that is not compatible with the current one. Hash mode forced", StateGenerator.hashModeToString(settingsManager.getGlobalHashMode())));
				context.setHashMode(settingsManager.getGlobalHashMode());
			}
		}
		else if (context.getHashMode() != HashMode.hashAll)
		{
			if (optionList.contains(Option.ALL_HASH_MANDATORY))
			{
				Logger.error("Computing all hash is mandatory");
				System.exit(-1);
			}
		}
	}

	private boolean isCompatible(HashMode globalHashMode, HashMode hashMode)
	{
		switch (globalHashMode)
		{
			case hashAll:
				return true;

			case hashMediumBlock:
				if (hashMode == HashMode.hashAll)
				{
					return false;
				}
				return true;

			case hashSmallBlock:
				if (hashMode == HashMode.hashAll || hashMode == HashMode.hashMediumBlock)
				{
					return false;
				}
				return true;

			case dontHash:
				if (hashMode != HashMode.dontHash)
				{
					return false;
				}
				return true;
		}

		return false;
	}

	protected boolean confirmAction(Context context, String action)
	{
		if (context.isAlwaysYes())
		{
			return true;
		}

		Scanner scanner = new Scanner(System.in);
		System.out.printf("Do you really want to %s (y/n/A)? ", action);
		String str = scanner.next();
		if (str.equalsIgnoreCase("y"))
		{
			return true;
		}
		else if (str.equals("A"))
		{
			context.setAlwaysYes(true);
			return true;
		}
		return false;
	}

	protected static enum Option
	{
		ALLOW_COMPATIBLE,
		ALL_HASH_MANDATORY
	}
}
