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
package org.fim.internal;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.fim.internal.hash.FileHasher;
import org.fim.internal.hash.HashProgress;
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.model.FimIgnore;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.FileUtil;
import org.fim.util.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.internal.hash.HashProgress.PROGRESS_DISPLAY_FILE_COUNT;
import static org.fim.model.HashMode.dontHash;
import static org.fim.util.HashModeUtil.hashModeToString;

public class StateGenerator {
    private static final int FILES_QUEUE_CAPACITY = 500;

    private static Comparator<FileState> fileNameComparator = new FileState.FileNameComparator();

    protected final Context context;
    final HashProgress hashProgress;
    private final FimIgnoreManager fimIgnoreManager;

    ExecutorService executorService;

    protected Path rootDir;
    private BlockingDeque<Path> filesToHashQueue;
    private AtomicBoolean scanInProgress;
    boolean fileHashersStarted;
    List<FileHasher> fileHashers;

    public StateGenerator(Context context) {
        this.context = context;
        this.hashProgress = new HashProgress(context);
        this.fimIgnoreManager = new FimIgnoreManager(context);
    }

    public State generateState(PrintStream out, String comment, Path rootDir, Path dirToScan) throws NoSuchAlgorithmException {
        this.rootDir = rootDir;

        int threadCount = context.getThreadCount();
        Logger.info(String.format("Scanning recursively local files, using '%s' mode and %d %s",
            hashModeToString(context.getHashMode()), threadCount, plural("thread", threadCount)));
        if (hashProgress.isProgressDisplayed()) {
            out.printf("(Hash progress legend for files grouped %d by %d: %s)%n", PROGRESS_DISPLAY_FILE_COUNT, PROGRESS_DISPLAY_FILE_COUNT, hashProgress.hashLegend());
        }

        State state = new State();
        state.setComment(comment);
        state.setHashMode(context.getHashMode());
        state.getCommitDetails().setHashModeUsedToGetTheStatus(context.getHashMode());

        long start = System.currentTimeMillis();
        hashProgress.outputInit();

        filesToHashQueue = new LinkedBlockingDeque<>(FILES_QUEUE_CAPACITY);
        initializeFileHashers();

        FimIgnore initialFimIgnore = fimIgnoreManager.loadInitialFimIgnore();
        try {
            scanInProgress = new AtomicBoolean(true);
            scanFileTree(filesToHashQueue, dirToScan, initialFimIgnore);
        } finally {
            scanInProgress.set(false);
        }

        // In case the FileHashers have not already been started
        startFileHashers();

        waitAllFilesToBeHashed();

        long overallTotalBytesHashed = 0;
        for (FileHasher fileHasher : fileHashers) {
            state.getFileStates().addAll(fileHasher.getFileStates());
            overallTotalBytesHashed += fileHasher.getTotalBytesHashed();
        }

        Collections.sort(state.getFileStates(), fileNameComparator);

        state.setIgnoredFiles(fimIgnoreManager.getIgnoredFiles());

        hashProgress.outputStop();
        long duration = System.currentTimeMillis() - start;
        displayStatistics(duration, state.getFileCount(), state.getFilesContentLength(), overallTotalBytesHashed);

        return state;
    }

    protected void initializeFileHashers() {
        fileHashersStarted = false;
        fileHashers = new ArrayList<>();
        executorService = Executors.newFixedThreadPool(context.getThreadCount());
    }

    protected void startFileHashers() throws NoSuchAlgorithmException {
        if (!fileHashersStarted) {
            String normalizedRootDir = FileUtil.getNormalizedFileName(rootDir);
            for (int index = 0; index < context.getThreadCount(); index++) {
                FileHasher hasher = new FileHasher(context, scanInProgress, hashProgress, filesToHashQueue, normalizedRootDir);
                executorService.submit(hasher);
                fileHashers.add(hasher);
            }
            fileHashersStarted = true;
        }
    }

    protected void waitAllFilesToBeHashed() {
        try {
            executorService.shutdown();
            executorService.awaitTermination(3, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
            Logger.error("Exception while waiting for files to be hashed", ex, context.isDisplayStackTrace());
        }
    }

    protected void displayStatistics(long duration, int fileCount, long filesContentLength, long totalBytesHashed) {
        String totalFileContentLengthStr = FileUtils.byteCountToDisplaySize(filesContentLength);
        String totalBytesHashedStr = FileUtils.byteCountToDisplaySize(totalBytesHashed);
        String durationStr = DurationFormatUtils.formatDuration(duration, "HH:mm:ss");

        long durationSeconds = duration / 1000;
        if (durationSeconds <= 0) {
            durationSeconds = 1;
        }

        long globalThroughput = totalBytesHashed / durationSeconds;
        String throughputStr = FileUtils.byteCountToDisplaySize(globalThroughput);

        if (context.getHashMode() == dontHash) {
            Logger.info(String.format("Scanned %d %s (%s), during %s%n",
                fileCount, plural("file", fileCount), totalFileContentLengthStr, durationStr));
        } else {
            Logger.info(String.format("Scanned %d %s (%s), hashed %s (avg %s/s), during %s%n",
                fileCount, plural("file", fileCount), totalFileContentLengthStr, totalBytesHashedStr, throughputStr, durationStr));
        }
    }

    private void scanFileTree(BlockingDeque<Path> filesToHashQueue, Path directory, FimIgnore parentFimIgnore) throws NoSuchAlgorithmException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            FimIgnore fimIgnore = fimIgnoreManager.loadLocalIgnore(directory, parentFimIgnore);

            for (Path file : stream) {
                if (!fileHashersStarted && filesToHashQueue.size() > FILES_QUEUE_CAPACITY / 2) {
                    startFileHashers();
                }

                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String fileName = file.getFileName().toString();
                if (fimIgnoreManager.isIgnored(fileName, attributes, fimIgnore)) {
                    fimIgnoreManager.ignoreThisFiles(file, attributes);
                } else {
                    if (attributes.isRegularFile()) {
                        enqueueFile(filesToHashQueue, file);
                    } else if (attributes.isDirectory()) {
                        scanFileTree(filesToHashQueue, file, fimIgnore);
                    }
                }
            }
        } catch (IOException ex) {
            Console.newLine();
            Logger.error("Skipping - Error scanning directory '" + directory + "'", ex, context.isDisplayStackTrace());
        }
    }

    private void enqueueFile(BlockingDeque<Path> filesToHashQueue, Path file) {
        try {
            filesToHashQueue.offer(file, 120, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Logger.error("Exception while enqueuing file '" + file + "'", ex, context.isDisplayStackTrace());
        }
    }
}
