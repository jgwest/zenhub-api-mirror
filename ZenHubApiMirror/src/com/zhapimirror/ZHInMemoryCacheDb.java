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

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.zhapi.json.IssueEventJson;
import com.zhapi.json.responses.DependenciesForARepoResponseJson;
import com.zhapi.json.responses.GetBoardForRepositoryResponseJson;
import com.zhapi.json.responses.GetEpicResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;
import com.zhapi.json.responses.GetIssueDataResponseJson;

/**
 * This class wraps an 'inner' database, and speeds up retrieval operations from
 * that database, by caching the result of persistent operations to that
 * database. The cache of the inner database is stored in memory, and is only
 * limited by the amount of JVM heap.
 * 
 * All objects in the cache are maintained via soft references, to ensure that
 * these objects are GC-ed when not otherwise used (preventing memory leaks).
 */
public class ZHInMemoryCacheDb implements ZHDatabase {

	private static final boolean DEBUG = true;

	private final ZHDatabase inner;

	private final Map<String, SoftReference<Object>> cache_synch = new HashMap<>();

	private Long debug_cacheAttempts_synch_lock = 0l;

	private Long debug_cacheHits_synch_lock = 0l;

	private static final ZHLog log = ZHLog.getInstance();

	public ZHInMemoryCacheDb(ZHDatabase inner) {
		this.inner = inner;
	}

	private Object getByKey(String key) {
		SoftReference<Object> entry;
		synchronized (cache_synch) {
			entry = cache_synch.get(key);

			if (DEBUG) {
				debug_cacheAttempts_synch_lock++;
				if (entry != null) {
					debug_cacheHits_synch_lock++;
				}
				if (debug_cacheAttempts_synch_lock % 300 == 0) {
					log.logDebug("zh-cache %: " + ((100 * debug_cacheHits_synch_lock) / debug_cacheAttempts_synch_lock));
//					System.out.println("cache: " + debug_cacheHits_synch_lock + " " + debug_cacheAttempts_synch_lock);
				}
			}

		}
		if (entry == null) {
			return null;
		}

		return entry.get();

	}

	private void putByKeyOptional(String key, Optional<?> value) {
		if (value.isPresent()) {
			putByKey(key, value.get());
		}
	}

	private void putByKey(String key, Object value) {
		synchronized (cache_synch) {
			cache_synch.put(key, new SoftReference<Object>(value));
		}
	}

	@Override
	public Optional<GetIssueDataResponseJson> getIssueData(long repoId, int issueNumber) {
		String key = ZHDatabaseUtil.generateIssueDataKey(repoId, issueNumber);

		GetIssueDataResponseJson cachedResult = (GetIssueDataResponseJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<GetIssueDataResponseJson> result = inner.getIssueData(repoId, issueNumber);
		putByKeyOptional(key, result);

		return result;

	}

	@Override
	public void persist(GetIssueDataResponseJson json, long repoId, int issueNumber) {
		String key = ZHDatabaseUtil.generateIssueDataKey(repoId, issueNumber);

		inner.persist(json, repoId, issueNumber);

		putByKey(key, json);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Optional<List<IssueEventJson>> getIssueEvents(long repoId, int issueNumber) {
		String key = ZHDatabaseUtil.generateIssueEventsKey(repoId, issueNumber);

		List<IssueEventJson> cachedResult = (List<IssueEventJson>) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<List<IssueEventJson>> result = inner.getIssueEvents(repoId, issueNumber);
		putByKeyOptional(key, result);

		return result;

	}

	@Override
	public void persist(List<IssueEventJson> events, long repoId, int issueNumber) {
		String key = ZHDatabaseUtil.generateIssueEventsKey(repoId, issueNumber);

		inner.persist(events, repoId, issueNumber);

		putByKey(key, events);
	}

	@Override
	public Optional<GetBoardForRepositoryResponseJson> getZenHubBoardForRepo(long repoId) {
		String key = ZHDatabaseUtil.generateZenHubBoardKey(repoId);

		GetBoardForRepositoryResponseJson cachedResult = (GetBoardForRepositoryResponseJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<GetBoardForRepositoryResponseJson> result = inner.getZenHubBoardForRepo(repoId);
		putByKeyOptional(key, result);

		return result;

	}

	@Override
	public void persist(GetBoardForRepositoryResponseJson board, long repoId) {
		String key = ZHDatabaseUtil.generateZenHubBoardKey(repoId);

		inner.persist(board, repoId);

		putByKey(key, board);
	}

	@Override
	public Optional<DependenciesForARepoResponseJson> getDependenciesForARepository(long repoId) {
		String key = ZHDatabaseUtil.generateDependenciesForARepoKey(repoId);

		DependenciesForARepoResponseJson cachedResult = (DependenciesForARepoResponseJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<DependenciesForARepoResponseJson> result = inner.getDependenciesForARepository(repoId);
		putByKeyOptional(key, result);

		return result;

	}

	@Override
	public void persist(DependenciesForARepoResponseJson dependencies, long repoId) {
		String key = ZHDatabaseUtil.generateDependenciesForARepoKey(repoId);

		inner.persist(dependencies, repoId);

		putByKey(key, dependencies);
	}

	@Override
	public Optional<GetEpicsResponseJson> getEpics(long repoId) {
		String key = ZHDatabaseUtil.generateEpicsPluralKey(repoId);

		GetEpicsResponseJson cachedResult = (GetEpicsResponseJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<GetEpicsResponseJson> result = inner.getEpics(repoId);
		putByKeyOptional(key, result);

		return result;

	}

	@Override
	public void persist(GetEpicsResponseJson epics, long repoId) {
		String key = ZHDatabaseUtil.generateEpicsPluralKey(repoId);

		inner.persist(epics, repoId);

		putByKey(key, epics);
	}

	@Override
	public Optional<GetEpicResponseJson> getEpic(long repoId, int issueId) {
		String key = ZHDatabaseUtil.generateEpicKey(repoId, issueId);

		GetEpicResponseJson cachedResult = (GetEpicResponseJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<GetEpicResponseJson> result = inner.getEpic(repoId, issueId);
		putByKeyOptional(key, result);

		return result;

	}

	@Override
	public void persist(GetEpicResponseJson epic, long repoId, int issueId) {
		String key = ZHDatabaseUtil.generateEpicKey(repoId, issueId);

		inner.persist(epic, repoId, issueId);

		putByKey(key, epic);
	}

	@Override
	public boolean isDatabaseInitialized() {
		return inner.isDatabaseInitialized();
	}

	@Override
	public void initializeDatabase() {
		inner.initializeDatabase();
	}

	@Override
	public void persistLong(String key, long value) {
		key = "long-" + key;

		inner.persistLong(key, value);
		putByKey(key, value);
	}

	@Override
	public Optional<Long> getLong(final String keyParam) {
		String key = "long-" + keyParam;

		Long cachedResult = (Long) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<Long> result = inner.getLong(keyParam);
		putByKeyOptional(key, result);

		return result;
	}

	@Override
	public void uninitializeDatabaseOnContentsMismatch(List<String> orgs, List<String> userRepos, List<String> individualRepos) {
		inner.uninitializeDatabaseOnContentsMismatch(orgs, userRepos, individualRepos);
	}

	@Override
	public void persistString(String key, String value) {
		key = "string-" + key;

		inner.persistString(key, value);
		putByKey(key, value);
	}

	@Override
	public Optional<String> getString(String keyParam) {
		String key = "string-" + keyParam;

		String cachedResult = (String) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<String> result = inner.getString(keyParam);
		putByKeyOptional(key, result);

		return result;
	}

}
