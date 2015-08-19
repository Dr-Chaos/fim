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
package org.fim.tooling;

import org.fim.model.HashMode;
import org.fim.model.Parameters;

public class BuildableParameters extends Parameters
{
	public BuildableParameters dontHashFiles()
	{
		setHashMode(HashMode.dontHashFiles);
		return this;
	}

	public BuildableParameters hashSmallBlock()
	{
		setHashMode(HashMode.hashSmallBlock);
		return this;
	}

	public BuildableParameters hashMediumBlock()
	{
		setHashMode(HashMode.hashMediumBlock);
		return this;
	}

	public BuildableParameters computeAllHash()
	{
		setHashMode(HashMode.computeAllHash);
		return this;
	}
}
