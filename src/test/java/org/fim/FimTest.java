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
package org.fim;

import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.RepositoryException;
import org.fim.model.Context;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;

public class FimTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    private Fim cut;
    private RepositoryTool tool;
    private Context context;
    private Path rootDir;

    @Before
    public void setUp() throws IOException {
        tool = new RepositoryTool(this.getClass());
        rootDir = tool.getRootDir();
        context = tool.getContext();

        cut = new Fim();
    }

    @Test(expected = BadFimUsageException.class)
    public void fimRepositoryAlreadyExist() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"init", "-y"}, context);
    }

    @Test(expected = RepositoryException.class)
    public void fimRepositoryNotWritable() throws Exception {
        if (IS_OS_WINDOWS) {
            // Ignore this test for Windows
            throw new RepositoryException();
        }

        tool.setReadOnly(rootDir);
        try {
            cut.run(new String[]{"init", "-y"}, context);
        } finally {
            tool.setReadWrite(rootDir);
        }
    }

    @Test(expected = BadFimUsageException.class)
    public void fimDoesNotExist() throws Exception {
        cut.run(new String[]{"status"}, context);
    }

    @Test
    public void canPrintUsage() throws Exception {
        exit.expectSystemExitWithStatus(0);
        Fim.main(new String[]{"help"});
    }

    @Test
    public void invalidOptionIsDetected() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        Fim.main(new String[]{"-9"});
    }

    @Test
    public void canCommitUsingMultipleThreads() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"ci", "-y", "-t", "4"}, context);
        assertThat(context.getThreadCount()).isEqualTo(4);
    }

    @Test(expected = BadFimUsageException.class)
    public void InvalidThreadCountIsDetected() throws Exception {
        cut.run(new String[]{"ci", "-y", "-t", "dummy"}, context);
    }

    @Test
    public void canCommitUsingFim() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"ci", "-y"}, context);
    }

    @Test
    public void canCommitFromASubDirectory() throws Exception {
        initRepoAndCreateOneFile();
        String subdir = "sub-dir";
        Path subdirPath = rootDir.resolve(subdir);
        Files.createDirectories(subdirPath);
        cut.run(new String[]{"ci", "-y", "-d", subdirPath.toString()}, context);
        assertThat(context.getCurrentDirectory()).isEqualTo(subdirPath);
    }

    @Test(expected = BadFimUsageException.class)
    public void masterFimRepositoryDirectoryMustExist() throws Exception {
        cut.run(new String[]{"rdup", "-m", "dummy_directory"}, context);
    }

    @Test
    public void negativeOutputTruncatingIsSetToZero() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"ci", "-y", "-o", "-1"}, context);
        assertThat(context.getTruncateOutput()).isEqualTo(0);
    }

    @Test
    public void doNotHashOption() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-n"}, context);
    }

    @Test
    public void fastModeOption() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-f"}, context);
    }

    @Test
    public void superFastModeOption() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-s"}, context);
    }

    @Test
    public void diffAliasIsAllowed() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"diff", "-s"}, context);
    }

    @Test
    public void attributesIgnoredIsAllowed() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-s", "-i", "attrs"}, context);
    }

    @Test
    public void datesIgnoredIsAllowed() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-s", "-i", "dates"}, context);
    }

    @Test
    public void renamedIgnoredIsAllowed() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-s", "-i", "renamed"}, context);
    }

    @Test
    public void allIgnoredIsAllowed() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-s", "-i", "all"}, context);
    }

    @Test(expected = BadFimUsageException.class)
    public void badIgnoredIsNotAllowed() throws Exception {
        initRepoAndCreateOneFile();
        cut.run(new String[]{"status", "-s", "-i", "bad"}, context);
    }

    @Test
    public void canRunVersionCommand() throws Exception {
        cut.run(new String[]{"-v"}, context);
    }

    @Test
    public void canRunHelpCommand() throws Exception {
        cut.run(new String[]{"-h"}, context);
    }

    @Test(expected = BadFimUsageException.class)
    public void noArgumentSpecified() throws Exception {
        cut.run(new String[]{""}, context);
    }

    @Test(expected = BadFimUsageException.class)
    public void noCommandSpecified() throws Exception {
        cut.run(new String[]{"-s"}, context);
    }

    private void initRepoAndCreateOneFile() throws Exception {
        cut.run(new String[]{"init", "-y"}, context);
        tool.createOneFile();
    }
}
