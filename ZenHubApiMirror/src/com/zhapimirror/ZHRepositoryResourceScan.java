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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import com.zhapi.ApiResponse;
import com.zhapi.ZenHubClient;
import com.zhapi.json.responses.DependenciesForARepoResponseJson;
import com.zhapi.json.responses.GetBoardForRepositoryResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;
import com.zhapi.services.BoardService;
import com.zhapi.services.DependenciesService;
import com.zhapi.services.EpicsService;
import com.zhapimirror.GHOwner.Type;
import com.zhapimirror.ZHWorkQueue.ZHRepositoryContainer;

/**
 * Every X number of seconds, this logic is called on each repo; but, this scan
 * only updates some resources, not all (eg issues are not updated as this is
 * too expensive).
 * 
 * Issue scanning is handled as part of the nightly scan.
 * 
 */
public class ZHRepositoryResourceScan {

	private static final ZHLog log = ZHLog.getInstance();

	public static void doScan(GHOwner owner, ZenHubClient zh, GitHub gitHubClient, ZHDatabase db) throws IOException {

		log.logInfo("Beginning resource scan on " + owner);

		// Gather all the repos in the org/user
		List<GHRepository> repositories = new ArrayList<>();
		{
			if (owner.getType() == Type.ORG) {

				GHOrganization org = gitHubClient.getOrganization(owner.getOrgNameOrNull());

				repositories.addAll(org.getRepositories().values());

			} else {

				GHUser user = gitHubClient.getUser(owner.getUserNameOrNull());
				repositories.addAll(user.getRepositories().values());
			}
			Collections.shuffle(repositories);
		}

		for (GHRepository repository : repositories) {

			// On failure, retry up to 3 times
			// We don't throw an exception after 3, because this scan will run anyways,
			// after the next delay interval.
			retry_for: for (int retries = 0; retries <= 3; retries++) {
				try {
					runOnARepository(repository, zh, db);
					break retry_for;
				} catch (Exception e) {
					log.logError(e.getClass().getName() + " - " + e.getMessage() + ". Retrying in 60 seconds.");
					ZHUtil.sleep(60 * 1000);
				}

			}

		}

		log.logInfo("Resource scan complete on " + owner);
	}

	public static void doScan(List<ZHRepositoryContainer> reposParam, ZenHubClient zh, GitHub gitHubClient, ZHDatabase db)
			throws IOException {

		log.logInfo("Beginning resource scan on multiple repos");

		List<ZHRepositoryContainer> repos = new ArrayList<>(reposParam);
		Collections.shuffle(repos);

		repos.stream().map(e -> e.getRepo()).forEach(repository -> {
			// On failure, retry up to 3 times.
			// We don't throw an exception after 3, because this scan will run anyways,
			// after the next delay interval.
			retry_for: for (int retries = 0; retries <= 3; retries++) {
				try {
					runOnARepository(repository, zh, db);
					break retry_for;
				} catch (Exception e) {
					log.logError(e.getClass().getName() + " - " + e.getMessage());
					ZHUtil.sleep(60 * 1000);
				}

			}

		});

		log.logInfo("Resource scan complete on multiple repos.");

	}

	private static void runOnARepository(GHRepository repository, ZenHubClient zh, ZHDatabase db) {
		// Boards
		{
			BoardService boardService = new BoardService(zh);
			ApiResponse<GetBoardForRepositoryResponseJson> r = boardService.getZenHubBoardForRepo(repository.getId());
			if (r != null && r.getResponse() != null) {
				db.persist(r.getResponse(), repository.getId());
			}
		}

		// Dependencies
		{
			DependenciesService dependenciesService = new DependenciesService(zh);
			ApiResponse<DependenciesForARepoResponseJson> r = dependenciesService
					.getDependenciesForARepository(repository.getId());
			if (r != null && r.getResponse() != null) {
				db.persist(r.getResponse(), repository.getId());
			}

		}

		// Epics
		{
			EpicsService epicsService = new EpicsService(zh);
			ApiResponse<GetEpicsResponseJson> r = epicsService.getEpics(repository.getId());
			if (r != null && r.getResponse() != null) {
				db.persist(r.getResponse(), repository.getId());
			}

		}

	}
}
