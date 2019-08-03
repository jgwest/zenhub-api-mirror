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

import java.io.IOException;
import java.util.List;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

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
							System.err.println("exception message 2 is: " + e.getMessage() + " " + e);
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

		if (owner.getType() == Type.ORG) {
			GHOrganization org = gh.getOrganization(owner.getOrgNameOrNull());
			repo = org.getRepository(repoName);

			log.logDebug("Processing org: " + org.getLogin() + "/" + repoName);
		} else {
			GHUser user = gh.getUser(owner.getUserNameOrNull());
			repo = user.getRepository(repoName);

			log.logDebug("Processing user: " + user.getLogin() + "/" + repoName);
		}

		long repoId = repo.getId();
		ZenHubClient zh = workQueue.getZenhubClient();

		ZHFilter filter = workQueue.getFilter();

		// First we process the repository level ZenHub resources

		// Epics
		{
			EpicsService epicsService = new EpicsService(zh);
			ApiResponse<GetEpicsResponseJson> r = epicsService.getEpics(repoId);
			GetEpicsResponseJson epics = r.getResponse();
			if (epics != null) {
				db.persist(epics, repoId);

				if (epics.getEpic_issues() != null) {

					epics.getEpic_issues().stream().map(e -> e.getIssue_number()).forEach(issueNumber -> {

						if (!filter.processIssue(owner, repoName, issueNumber)) {
							return;
						}

						retryOnRateLimit(() -> {
							ApiResponse<GetEpicResponseJson> r2 = epicsService.getEpic(repoId, issueNumber);
							GetEpicResponseJson epic = r2.getResponse();
							if (epic != null) {
								db.persist(epic, repoId, issueNumber);
							}
						}, "epic->issues->" + issueNumber);

					});
				}
			}
		}

		// Board
		{
			BoardService boardService = new BoardService(zh);
			ApiResponse<GetBoardForRepositoryResponseJson> r = boardService.getZenHubBoardForRepo(repoId);
			GetBoardForRepositoryResponseJson board = r.getResponse();
			if (board != null) {
				db.persist(board, repoId);
			}
		}

		// Dependencies
		{
			DependenciesService dependenciesService = new DependenciesService(zh);
			ApiResponse<DependenciesForARepoResponseJson> r = dependenciesService.getDependenciesForARepository(repoId);
			DependenciesForARepoResponseJson dependencies = r.getResponse();
			if (dependencies != null) {
				db.persist(dependencies, repoId);
			}
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
