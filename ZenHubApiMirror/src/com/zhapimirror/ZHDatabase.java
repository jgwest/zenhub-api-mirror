/*
 * Copyright 2019, 2020 Jonathan West
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

import java.util.List;
import java.util.Optional;

import com.zhapi.json.IssueEventJson;
import com.zhapi.json.responses.DependenciesForARepoResponseJson;
import com.zhapi.json.responses.GetBoardForRepositoryResponseJson;
import com.zhapi.json.responses.GetEpicResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;
import com.zhapi.json.responses.GetIssueDataResponseJson;
import com.zhapi.shared.json.RepositoryChangeEventJson;

/**
 * This interface abstracts the persistence of ZenHub JSON resources, allowing
 * the underlying database technology to vary independently of the calling
 * class.
 * 
 * Implementing classes include ZHInMemoryCacheDB and ZHPersistJsonDb.
 */
public interface ZHDatabase {

	public static final String LAST_FULL_SCAN = "lastFullScan";

	public Optional<GetIssueDataResponseJson> getIssueData(long repoId, int issueNumber);

	public void persist(GetIssueDataResponseJson json, long repoId, int issueNumber);

	public Optional<List<IssueEventJson>> getIssueEvents(long repoId, int issueNumber);

	public void persist(List<IssueEventJson> events, long repoId, int issueNumber);

	public Optional<GetBoardForRepositoryResponseJson> getZenHubBoardForRepo(long repoId);

	public void persist(GetBoardForRepositoryResponseJson board, long repoId);

	public Optional<DependenciesForARepoResponseJson> getDependenciesForARepository(long repoId);

	public void persist(DependenciesForARepoResponseJson dependencies, long repoId);

	public Optional<GetEpicsResponseJson> getEpics(long repoId);

	public void persist(GetEpicsResponseJson epics, long repoId);

	public Optional<GetEpicResponseJson> getEpic(long repoId, int issueId);

	public void persist(GetEpicResponseJson epic, long repoId, int issueId);

	public void persistRepositoryChangeEvent(RepositoryChangeEventJson newEvent);

	public List<RepositoryChangeEventJson> getRecentRepositoryChangeEvents(long timestampEqualOrGreater);

	public boolean isDatabaseInitialized();

	public void initializeDatabase();

	public void persistLong(String key, long value);

	public Optional<Long> getLong(String key);

	/**
	 * This is called at startup, to ensure the database contents match the repos we
	 * are asked to mirror in the configuration file. If they don't match, the
	 * database should be destroyed and rebuilt.
	 */
	public void uninitializeDatabaseOnContentsMismatch(List<String> orgs, List<String> userRepos, List<String> individualRepos);

	public Optional<String> getString(String key);

	public void persistString(String key, String value);

}
