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
package org.fim.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

import org.fim.model.HashMode;
import org.junit.Test;

public class HashModeUtilTest
{
	@Test
	public void checkHashModeCompatibility()
	{
		assertCompatible(hashAll, hashAll);
		assertCompatible(hashAll, hashMediumBlock);
		assertCompatible(hashAll, hashSmallBlock);
		assertCompatible(hashAll, dontHash);

		assertNotCompatible(hashMediumBlock, hashAll);
		assertCompatible(hashMediumBlock, hashMediumBlock);
		assertCompatible(hashMediumBlock, hashSmallBlock);
		assertCompatible(hashMediumBlock, dontHash);

		assertNotCompatible(hashSmallBlock, hashAll);
		assertNotCompatible(hashSmallBlock, hashMediumBlock);
		assertCompatible(hashSmallBlock, hashSmallBlock);
		assertCompatible(hashSmallBlock, dontHash);

		assertNotCompatible(dontHash, hashAll);
		assertNotCompatible(dontHash, hashMediumBlock);
		assertNotCompatible(dontHash, hashSmallBlock);
		assertCompatible(dontHash, dontHash);
	}

	private void assertCompatible(HashMode hashMode, HashMode toCheck)
	{
		assertThat(HashModeUtil.isCompatible(hashMode, toCheck)).isTrue();
	}

	private void assertNotCompatible(HashMode hashMode, HashMode toCheck)
	{
		assertThat(HashModeUtil.isCompatible(hashMode, toCheck)).isFalse();
	}
}
