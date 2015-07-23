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
package org.fim.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class State
{
	private String modelVersion;
	private long timestamp;
	private String comment;
	private int fileCount;
	private List<FileState> fileStates;

	public State()
	{
		modelVersion = "1";
		timestamp = System.currentTimeMillis();
		comment = "";
		fileCount = 0;
		fileStates = new ArrayList<>();
	}

	public static State loadFromGZipFile(Path stateFile) throws IOException
	{
		try (Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(stateFile.toFile()))))
		{
			Gson gson = new Gson();
			State state = gson.fromJson(reader, State.class);
			return state;
		}
	}

	public void saveToGZipFile(Path stateFile) throws IOException
	{
		fileCount = fileStates.size();

		try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(stateFile.toFile()))))
		{
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(this, writer);
		}
	}

	public String getModelVersion()
	{
		return modelVersion;
	}

	public void setModelVersion(String modelVersion)
	{
		this.modelVersion = modelVersion;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	protected void setTimestamp(long timestamp)
	{
		this.timestamp = timestamp;
	}

	public String getComment()
	{
		return comment;
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}

	public int getFileCount()
	{
		return fileCount;
	}

	protected void setFileCount(int fileCount)
	{
		this.fileCount = fileCount;
	}

	public List<FileState> getFileStates()
	{
		return fileStates;
	}

	public void setFileStates(List<FileState> fileStates)
	{
		this.fileStates = fileStates;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (other == null || !(other instanceof State))
		{
			return false;
		}

		State state = (State) other;

		return Objects.equals(this.modelVersion, state.modelVersion)
				&& Objects.equals(this.timestamp, state.timestamp)
				&& Objects.equals(this.comment, state.comment)
				&& Objects.equals(this.fileCount, state.fileCount)
				&& Objects.equals(this.fileStates, state.fileStates);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(modelVersion, timestamp, comment, fileCount, fileStates);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("modelVersion", modelVersion)
				.add("timestamp", timestamp)
				.add("comment", comment)
				.add("fileCount", fileCount)
				.add("fileStates", fileStates)
				.toString();
	}
}
