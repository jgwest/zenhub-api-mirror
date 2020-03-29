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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhapi.ApiResponse;
import com.zhapi.ZenHubApiException;
import com.zhapi.ZenHubClient;
import com.zhapi.json.IssueEventJson;
import com.zhapi.json.responses.DependenciesForARepoResponseJson;
import com.zhapi.json.responses.GetBoardForRepositoryResponseJson;
import com.zhapi.json.responses.GetEpicResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;
import com.zhapi.json.responses.GetIssueDataResponseJson;
import com.zhapi.services.BoardService;
import com.zhapi.services.DependenciesService;
import com.zhapi.services.EpicsService;
import com.zhapi.services.IssuesService;
import com.zhapi.shared.json.RepositoryChangeEventJson;
import com.zhapimirror.GHOwner.Type;
import com.zhapimirror.ZHWorkQueue.ZHIssueContainer;
import com.zhapimirror.ZHWorkQueue.ZHRepositoryContainer;

/**
 * This class is responsible for requesting work from the ZHWorkQueue, acting on
 * that work (querying the specified resources in the ZenHub API) and then
 * persisting those resources to the database.
 * 
 * A server instance will have multiple worker threads, corresponding to
 * multiple simultaneous connections to the ZH API.
 */
public class ZHWorkerThread extends Thread {

	private final ZHWorkQueue workQueue;

	private boolean acceptingNewWork = true;

	private final int threadId;

	private static final ZHLog log = ZHLog.getInstance();

	private static final boolean WORKER_THREAD_DEBUG = false;

	public ZHWorkerThread(ZHWorkQueue workQueue, int threadId) {
		setName(ZHWorkerThread.class.getName());

		this.threadId = threadId;
		this.workQueue = workQueue;

	}

	@Override
	public void run() {

		final ZHDatabase db = workQueue.getDb();

		while (acceptingNewWork) {
			try {

				workQueue.waitForAvailableWork();

				ZHRepositoryContainer repo = workQueue.pollRepository().orElse(null);
				if (repo != null) {
					try {
						processRepository(repo, db);
					} catch (Exception e) {
						if (e instanceof ZenHubApiException && e.getMessage().contains("403 for URL")) {
							log.logError("ZH Rate Limit Hit: " + e.getClass().getName() + ": " + e.getMessage());
						} else {
							e.printStackTrace();
						}
						log.logDebug("Thread #" + threadId + " sleeping after rate limit error. Current work in queue: "
								+ workQueue.availableWork());
						ZHUtil.sleep(60 * 1000);

						workQueue.addRepositoryFromRetry(repo);
					}
					continue;
				}

				ZHIssueContainer issue = workQueue.pollIssue().orElse(null);
				if (issue != null) {
					try {
						processIssue(issue, db);
					} catch (Exception e) {
						if (e instanceof ZenHubApiException && e.getMessage().contains("403 for URL")) {
							log.logError("ZH Rate Limit Hit: " + e.getClass().getName() + ": " + e.getMessage());
						} else {
							e.printStackTrace();
						}
						log.logDebug("Thread #" + threadId + " sleeping after rate limit error. Current work in queue: "
								+ workQueue.availableWork());
						ZHUtil.sleep(60 * 1000);

						workQueue.addIssueFromRetry(issue);
					}
					continue;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void processIssue(ZHIssueContainer issue, ZHDatabase db) {
		ZenHubClient zh = workQueue.getZenhubClient();

		long repoId = issue.getRepo().getId();
		int issueNumber = issue.getIssue().getNumber();

		log.logDebug("Processing issue: " + issue.getRepo().getName() + "/" + issueNumber);

		// Issues
		{
			IssuesService issuesService = new IssuesService(zh);
			ApiResponse<GetIssueDataResponseJson> r = issuesService.getIssueData(repoId, issueNumber);
			GetIssueDataResponseJson issueData = r.getResponse();
			if (issueData != null) {
				db.persist(issueData, repoId, issueNumber);
			}

			ApiResponse<List<IssueEventJson>> r2 = issuesService.getIssueEvents(repoId, issueNumber);
			List<IssueEventJson> list = r2.getResponse();
			if (list != null) {
				db.persist(list, repoId, issueNumber);
			}
		}

	}

	private void processRepository(ZHRepositoryContainer repository, ZHDatabase db) throws IOException {
		GitHub gh = workQueue.getGithubClient();

		GHOwner owner = repository.getOwner();

		GHRepository repo;

		String repoName = repository.getRepoName();

		String debugStr;

		if (owner.getType() == Type.ORG) {
			GHOrganization org = gh.getOrganization(owner.getOrgNameOrNull());
			repo = org.getRepository(repoName);

			debugStr = org.getLogin() + "/" + repoName;

			log.logDebug("Processing repo from org: " + debugStr);
		} else {
			GHUser user = gh.getUser(owner.getUserNameOrNull());
			repo = user.getRepository(repoName);

			debugStr = user.getLogin() + "/" + repoName;
			log.logDebug("Processing repo from user: " + debugStr);
		}

		long repoId = repo.getId();
		ZenHubClient zh = workQueue.getZenhubClient();

		ZHFilter filter = workQueue.getFilter();

		// Have any of the repository resources changed on ZH since we last saw them; we
		// answer this question by comparing our local database copy with what we get
		// back from ZH.
		boolean isRepositoryChangedFromDb = false;

		// First we process the repository level ZenHub resources

		// Epics
		{
			EpicsService epicsService = new EpicsService(zh);
			ApiResponse<GetEpicsResponseJson> r = epicsService.getEpics(repoId);
			GetEpicsResponseJson epics = r.getResponse();

			isRepositoryChangedFromDb = isRepositoryChangedFromDb(isRepositoryChangedFromDb, db.getEpics(repoId).orElse(null),
					epics);

			if (epics != null) {

				log.logDebug("Received getEpics response for " + debugStr);
				db.persist(epics, repoId);

				if (epics.getEpic_issues() != null) {

					epics.getEpic_issues().stream().map(e -> e.getIssue_number()).forEach(issueNumber -> {

						if (!filter.processIssue(owner, repoName, issueNumber)) {
							log.logDebug("Filtering out " + debugStr + "/" + issueNumber);
							return;
						}

						retryOnRateLimit(() -> {
							ApiResponse<GetEpicResponseJson> r2 = epicsService.getEpic(repoId, issueNumber);
							GetEpicResponseJson epic = r2.getResponse();
							if (epic != null) {
								log.logDebug("Get epic for " + debugStr + "/" + issueNumber + " persisted.");
								db.persist(epic, repoId, issueNumber);
							} else {
								log.logDebug("Get epic for " + debugStr + "/" + issueNumber + " was null.");
							}
						}, "epic->issues->" + issueNumber);

					});
				} else {
					log.logDebug("The received getEpics response had null issues, " + debugStr);
				}

			} else {
				log.logDebug("Received getEpics response: null, for " + debugStr);
			}
		}

		// Board
		{
			BoardService boardService = new BoardService(zh);
			ApiResponse<GetBoardForRepositoryResponseJson> r = boardService.getZenHubBoardForRepo(repoId);
			GetBoardForRepositoryResponseJson board = r.getResponse();

			isRepositoryChangedFromDb = isRepositoryChangedFromDb(isRepositoryChangedFromDb,
					db.getZenHubBoardForRepo(repoId).orElse(null), board);

			if (board != null) {
				db.persist(board, repoId);
			}

		}

		// Dependencies
		{
			DependenciesService dependenciesService = new DependenciesService(zh);
			ApiResponse<DependenciesForARepoResponseJson> r = dependenciesService.getDependenciesForARepository(repoId);
			DependenciesForARepoResponseJson dependencies = r.getResponse();

			isRepositoryChangedFromDb = isRepositoryChangedFromDb(isRepositoryChangedFromDb,
					db.getDependenciesForARepository(repoId).orElse(null), dependencies);

			if (dependencies != null) {
				db.persist(dependencies, repoId);
			}

		}

		if (isRepositoryChangedFromDb) {
			RepositoryChangeEventJson rcej = new RepositoryChangeEventJson();
			rcej.setRepoId(repoId);
			rcej.setTime(System.currentTimeMillis());
			rcej.setUuid(UUID.randomUUID().toString());

			log.logInfo("Repository resources changed: " + repoId);
			db.persistRepositoryChangeEvent(rcej);
		}

		// Now, process the issues that are in the repository.
		for (GHIssue e : repo.listIssues(GHIssueState.ALL)) {

			if (filter != null && !filter.processIssue(owner, repoName, e.getNumber())) {
				continue;
			}

			// Skip pull requests
			if (e.isPullRequest()) {
				continue;
			}

			workQueue.addIssue(owner, repo, e);
		}

	}

	/**
	 * Have any of the repository resources changed on ZH since we last saw them; we
	 * answer this question by comparing our local database copy with what we get
	 * back from ZH.
	 */
	@SuppressWarnings("unused")
	public static boolean isRepositoryChangedFromDb(boolean isChanged, Object oldDbVersion, Object newVersion)
			throws JsonProcessingException {

		if (oldDbVersion instanceof Optional) {
			throw new IllegalArgumentException();
		}

		if (isChanged) {
			return isChanged;
		}

		if (oldDbVersion == null) {
			oldDbVersion = "{}";
		}
		if (newVersion == null) {
			newVersion = "{}";
		}

		boolean result = !JsonUtil.isEqualBySortedAlphanumerics(oldDbVersion, newVersion, new ObjectMapper());

		if (WORKER_THREAD_DEBUG && result) {

			ObjectMapper om = new ObjectMapper();
			System.out.println("---------------------------------------------------");
			System.out.println(om.writeValueAsString(oldDbVersion));
			System.out.println("------------");
			System.out.println(om.writeValueAsString(newVersion));

		}

		return result;
	}

	private static void retryOnRateLimit(Runnable r, String debugMsg) {
		Exception lastException = null;
		boolean failedAtLeastOnce = false;
		for (int x = 0; x < 3; x++) {
			try {
				r.run();
				if (failedAtLeastOnce) {
					log.logInfo("Succeeded, after previously hitting rate limit: " + debugMsg);
				}
				return;
			} catch (Exception e) {
				if (e instanceof ZenHubApiException && e.getMessage().contains("403 for URL")) {
					failedAtLeastOnce = true;
					lastException = e;
					// Ignore
					log.logError("Rate limit hit, waiting 60 seconds and then retrying: " + debugMsg);
					ZHUtil.sleep(60 * 1000);
				} else {
					ZHUtil.throwAsUnchecked(e);
				}
			}
		}

		if (lastException != null) {
			ZHUtil.throwAsUnchecked(lastException);
		} else {
			throw new RuntimeException("Null exception.");
		}
	}
}
