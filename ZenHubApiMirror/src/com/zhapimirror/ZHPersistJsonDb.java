/*
 * Copyright 2019 Jonathan West
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
*/

package com.zhapimirror;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhapi.json.IssueEventJson;
import com.zhapi.json.responses.DependenciesForARepoResponseJson;
import com.zhapi.json.responses.GetBoardForRepositoryResponseJson;
import com.zhapi.json.responses.GetEpicResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;
import com.zhapi.json.responses.GetIssueDataResponseJson;

/**
 * Persists the ZH JSON resources to disk, using path and filenames to
 * distinguish resources types in the data hierarchy. The output directory is
 * specified in the constructor.
 * 
 * This class is thread safe.
 */
public class ZHPersistJsonDb implements ZHDatabase {

	private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

	private final Lock readLock = rwLock.readLock();
	private final Lock writeLock = rwLock.writeLock();

	private final File outputDirectory;

	private final AtomicBoolean initialized = new AtomicBoolean();

	private final ZHLog log = ZHLog.getInstance();

	private final static String KEY_ZENHUB_CONTENTS_HASH = "ZenHubContentsHash";

	private final static boolean DEBUG_IGNORE_OLD_DATABASE = false;

	public ZHPersistJsonDb(File outputDirectory) {
		this.outputDirectory = outputDirectory;

		initialized.set(outputDirectory.exists() && outputDirectory.listFiles().length > 0);
	}

	@Override
	public Optional<GetIssueDataResponseJson> getIssueData(long repoId, int issueNumber) {

		String key = ZHDatabaseUtil.generateIssueDataKey(repoId, issueNumber);

		File inputFile = new File(outputDirectory, key + "json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		GetIssueDataResponseJson result = readValue(contents, GetIssueDataResponseJson.class);

		return Optional.ofNullable(result);

	}

	@Override
	public void persist(GetIssueDataResponseJson json, long repoId, int issueNumber) {
		String key = ZHDatabaseUtil.generateIssueDataKey(repoId, issueNumber);
//		String key = ZHDatabaseUtil.generateKey(repoId, issueNumber);

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(json);
		writeToFile(contents, outputFile);

	}

	@Override
	public Optional<List<IssueEventJson>> getIssueEvents(long repoId, int issueNumber) {
		String key = ZHDatabaseUtil.generateIssueEventsKey(repoId, issueNumber);

		File inputFile = new File(outputDirectory, key + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		IssueEventJson[] result = readValue(contents, IssueEventJson[].class);

		return Optional.ofNullable(Arrays.asList(result));
	}

	@Override
	public void persist(List<IssueEventJson> events, long repoId, int issueNumber) {
		String key = ZHDatabaseUtil.generateIssueEventsKey(repoId, issueNumber);

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(events);
		writeToFile(contents, outputFile);
	}

	@Override
	public Optional<GetBoardForRepositoryResponseJson> getZenHubBoardForRepo(long repoId) {
		String key = ZHDatabaseUtil.generateZenHubBoardKey(repoId);

		File inputFile = new File(outputDirectory, key + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		GetBoardForRepositoryResponseJson result = readValue(contents, GetBoardForRepositoryResponseJson.class);

		return Optional.ofNullable(result);

	}

	@Override
	public void persist(GetBoardForRepositoryResponseJson board, long repoId) {
		String key = ZHDatabaseUtil.generateZenHubBoardKey(repoId);

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(board);
		writeToFile(contents, outputFile);
	}

	@Override
	public Optional<DependenciesForARepoResponseJson> getDependenciesForARepository(long repoId) {
		String key = ZHDatabaseUtil.generateDependenciesForARepoKey(repoId);

		File inputFile = new File(outputDirectory, key + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		DependenciesForARepoResponseJson result = readValue(contents, DependenciesForARepoResponseJson.class);

		return Optional.ofNullable(result);

	}

	@Override
	public void persist(DependenciesForARepoResponseJson dependencies, long repoId) {
		String key = ZHDatabaseUtil.generateDependenciesForARepoKey(repoId);

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(dependencies);
		writeToFile(contents, outputFile);
	}

	@Override
	public Optional<GetEpicsResponseJson> getEpics(long repoId) {
		String key = ZHDatabaseUtil.generateEpicsPluralKey(repoId);

		File inputFile = new File(outputDirectory, key + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		GetEpicsResponseJson result = readValue(contents, GetEpicsResponseJson.class);

		return Optional.ofNullable(result);

	}

	@Override
	public void persist(GetEpicsResponseJson epics, long repoId) {
		String key = ZHDatabaseUtil.generateEpicsPluralKey(repoId);

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(epics);
		writeToFile(contents, outputFile);
	}

	@Override
	public Optional<GetEpicResponseJson> getEpic(long repoId, int issueId) {
		String key = ZHDatabaseUtil.generateEpicKey(repoId, issueId);

		File inputFile = new File(outputDirectory, key + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		GetEpicResponseJson result = readValue(contents, GetEpicResponseJson.class);

		return Optional.ofNullable(result);
	}

	@Override
	public void persist(GetEpicResponseJson epic, long repoId, int issueId) {
		String key = ZHDatabaseUtil.generateEpicKey(repoId, issueId);

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(epic);
		writeToFile(contents, outputFile);
	}

	@Override
	public boolean isDatabaseInitialized() {

		return initialized.get();

	}

	@Override
	public void initializeDatabase() {
		initialized.set(true);
	}

	@Override
	public void persistLong(String key, long value) {

		persistString(key, Long.toString(value));
	}

	@Override
	public Optional<Long> getLong(String key) {

		Optional<String> result = getString(key);

		if (!result.isPresent()) {
			return Optional.empty();
		}

		return Optional.of(Long.parseLong(result.get()));

	}

	@Override
	public void persistString(String key, String value) {
		File outputFile = new File(outputDirectory, "keys/" + key + ".txt");

		writeToFile(value, outputFile);
	}

	@Override
	public Optional<String> getString(String key) {
		File inputFile = new File(outputDirectory, "keys/" + key + ".txt");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		return Optional.ofNullable(contents);
	}

	private <T> T readValue(String contents, Class<T> c) {
		if (contents == null) {
			return null;
		}

		ObjectMapper om = new ObjectMapper();
		try {
			return om.readValue(contents, c);
		} catch (Exception e) {
			ZHUtil.throwAsUnchecked(e);
			return null;
		}
	}

	private String writeValueAsString(Object o) {
		ObjectMapper om = new ObjectMapper();
		String result = null;
		try {
			result = om.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			ZHUtil.throwAsUnchecked(e);
		}
		return result;
	}

	private Optional<String> readFromFile(File f) {
		try {
			readLock.lock();

			if (!f.exists()) {
				return Optional.empty();
			}

			StringBuilder sb = new StringBuilder();

			try {

				byte[] barr = new byte[1024 * 64];
				int c;

				FileInputStream fis = new FileInputStream(f);
				while (-1 != (c = fis.read(barr))) {

					sb.append(new String(barr, 0, c));
				}
				fis.close();

			} catch (IOException e) {
				log.logSevere("Error from file: " + f.getPath(), e);
				ZHUtil.throwAsUnchecked(e);
			}

			return Optional.of(sb.toString());

		} finally {
			readLock.unlock();
		}
	}

	private void writeToFile(String contents, File f) {

		try {
			writeLock.lock();

			f.getParentFile().mkdirs();

			FileWriter fw = null;
			try {
				fw = new FileWriter(f);
				fw.write(contents);
				fw.close();
			} catch (IOException e) {
				ZHUtil.throwAsUnchecked(e);
			} finally {
				if (fw != null) {
					try {
						fw.close();
					} catch (IOException e) {
						/* ignore */ }
				}
			}

		} finally {
			writeLock.unlock();
		}

	}

	@Override
	public void uninitializeDatabaseOnContentsMismatch(List<String> orgs, List<String> userRepos, List<String> individualRepos) {
		if (!isDatabaseInitialized()) {
			return;
		}

		if (DEBUG_IGNORE_OLD_DATABASE) {
			for (int x = 0; x < 30; x++) {
				System.err.println("Debug: Ignoring old database!!!!!!!!!!");
			}
			return;
		}

		if (orgs == null) {
			orgs = new ArrayList<>();
		}
		if (userRepos == null) {
			userRepos = new ArrayList<>();
		}
		if (individualRepos == null) {
			individualRepos = new ArrayList<>();
		}

		// Convert to lowercase and sort
		Arrays.asList(orgs, userRepos, individualRepos).stream().forEach(e -> {

			List<String> newContents = e.stream().map(f -> f.toLowerCase()).sorted().collect(Collectors.toList());

			e.clear();
			e.addAll(newContents);

		});

		List<String> contents = new ArrayList<>();
		contents.add("orgs:");
		contents.addAll(orgs);
		contents.add("user-repos:");
		contents.addAll(userRepos);
		contents.add("individual-repos:");
		contents.addAll(individualRepos);

		String encoded;

		// Convert the array list to a hash
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(contents.stream().reduce((a, b) -> a + " " + b).get().getBytes("UTF-8"));

			encoded = Base64.getEncoder().encodeToString(bytes);

		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			throw new RuntimeException(e); // Convert to unchecked
		}

		boolean uninitializeDatabase = false;

		Optional<String> gitHubContentsHash = getString(KEY_ZENHUB_CONTENTS_HASH);
		if (!gitHubContentsHash.isPresent()) { // key not found
			uninitializeDatabase = true;
		} else if (!gitHubContentsHash.get().equals(encoded)) {
			uninitializeDatabase = true; // key doesn't match
		}

		if (uninitializeDatabase) {

			File oldDir = new File(outputDirectory, "old");
			if (!oldDir.exists()) {
				if (!oldDir.mkdirs()) {
					throw new RuntimeException("Unable to create: " + oldDir.getParentFile());
				}
			}

			long time = System.currentTimeMillis();

			for (File f : outputDirectory.listFiles()) {
				if (f.getPath().equals(oldDir.getPath())) {
					continue; // Don't move the old directory
				}

				try {
					Files.move(f.toPath(), new File(oldDir, f.getName() + ".old." + time).toPath());
				} catch (IOException e1) {
					throw new RuntimeException("Unable to move: " + f.getPath(), e1);
				}
			}

			log.logInfo("* Old database has been moved to " + oldDir.getPath());

			initialized.set(false);
		}

	}

}
