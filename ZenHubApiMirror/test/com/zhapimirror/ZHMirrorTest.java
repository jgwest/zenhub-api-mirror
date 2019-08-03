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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.zhapi.json.BoardPipelineEntryJson;
import com.zhapi.json.BoardPipelineIssueEntryJson;
import com.zhapi.json.DependencyBlockingOrBlockedJson;
import com.zhapi.json.DependencyJson;
import com.zhapi.json.EpicIssueJson;
import com.zhapi.json.IssueEventJson;
import com.zhapi.json.PipelineJson;
import com.zhapi.json.responses.DependenciesForARepoResponseJson;
import com.zhapi.json.responses.GetBoardForRepositoryResponseJson;
import com.zhapi.json.responses.GetEpicResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;
import com.zhapi.json.responses.GetIssueDataResponseJson;

/**
 * These tests will start the server instance/database, ask the server to
 * process a specific repository, then wait for the database to contain the
 * expected data.
 */
public class ZHMirrorTest extends AbstractTest {

	@Test
	public void runMirrorTest() throws IOException {

		File dirDb = Files.createTempDirectory("zh").toFile();

		String orgName = "OpenLiberty";
		String repoName = "open-liberty";

		System.out.println("Temporary database path: " + dirDb.getPath());

		ZHFilter tf = new ZHFilter() {

			@Override
			public boolean processRepo(GHOwner owner, String repoNameParam) {

				boolean result = repoNameParam.equals(repoName);
//				if (result) {
//					System.out.println("!" + repoName + "!/[" + repoNameParam + "]   " + owner + " " + result);
//				}
				return result;
			}

			@Override
			public boolean processIssue(GHOwner owner, String repoNameParam, int issue) {
				return repoNameParam.equals(repoName) && ((issue >= 300 & issue <= 400) || (issue <= 155) || (issue == 1423));
			}

		};

		ZHServerInstance si = getClientBuilder().dbDir(dirDb).filter(tf).orgNames(Arrays.asList(orgName)).build();

		waitForPass(60 * 30, () -> {
			ZHDatabase db = si.getDb();

			try {
				doBoardTest(db);
				doDependenciesTest(db);
				doEpicsTest(db);
				doIssuesTest(db);
			} catch (Throwable t) {
				t.printStackTrace();
				throw t;
			}
		});
	}

	public void doBoardTest(ZHDatabase db) {
		GetBoardForRepositoryResponseJson ar = db.getZenHubBoardForRepo(openLibertyRepoId).orElse(null);

		assertNotNull(ar);

		List<BoardPipelineEntryJson> pipelines = ar.getPipelines();

		assertNotNull(pipelines);

		assertTrue("Not enough pipelines", pipelines.size() > 1);

		String[] expectedPipelines = new String[] { "New Issues", "Epics", "Icebox", "Backlog", "In Progress", "Review/QA",
				"Done" };

		List<String> pipelineNames = pipelines.stream().map(e -> e.getName()).collect(Collectors.toList());

		for (String expected : expectedPipelines) {
			assertTrue(expected + " not found", pipelineNames.contains(expected));
		}

		// At least one pipeline must contain several issues
		assertTrue(pipelines.stream().anyMatch(board -> {
			List<BoardPipelineIssueEntryJson> issues = board.getIssues();
			if (issues.size() <= 1) {
				return false;
			}

			// At least one issue must match
			if (!board.getIssues().stream().anyMatch(issue -> {

				if (issue.getPosition() <= 5) {
					return false;
				}
				if (issue.getEstimate() == null) {
					return false;
				}

				if (issue.getEstimate().getValue() <= 2) {
					return false;
				}

				if (issue.getIssue_number() <= 100) {
					return false;
				}

				return true;
			})) {
				return false;
			}

			// At least one match: similar issue tests as above, but for epic.
			if (!board.getIssues().stream().anyMatch(issue -> {
				if (issue.getPosition() <= 1) {
					return false;
				}
				if (issue.getEstimate() == null) {
					return false;
				}

				if (issue.getEstimate().getValue() <= 2) {
					return false;
				}

				if (issue.getIssue_number() <= 100) {
					return false;
				}

				return issue.isIs_epic();

			})) {

			}

			return true;

		}));

	}

	public void doDependenciesTest(ZHDatabase db) {

		DependenciesForARepoResponseJson d = db.getDependenciesForARepository(openLibertyRepoId).orElse(null);

		assertNotNull(d);

		List<DependencyJson> dependencies = d.getDependencies();

		assertNotNull(dependencies);

		assertTrue(dependencies.size() > 10);

		// Look for a specific known dependency
		assertTrue(dependencies.stream().anyMatch(dependency -> {

			DependencyBlockingOrBlockedJson blocked = dependency.getBlocked();
			DependencyBlockingOrBlockedJson blocking = dependency.getBlocking();
			if (blocked == null || blocking == null) {
				return false;
			}

			return blocked.getIssue_number() == 320 && blocked.getRepo_id() == openLibertyRepoId
					&& blocking.getIssue_number() == 155 && blocking.getRepo_id() == openLibertyRepoId;
		}));

		// All dependencies should be non-null, and the values should be sane
		assertTrue(dependencies.stream().allMatch(dependency -> {

			DependencyBlockingOrBlockedJson blocked = dependency.getBlocked();
			DependencyBlockingOrBlockedJson blocking = dependency.getBlocking();
			if (blocked == null || blocking == null) {
				return false;
			}

			return blocked.getIssue_number() > 1 && blocked.getRepo_id() > 1 && blocking.getIssue_number() > 1
					&& blocking.getRepo_id() > 1;

		}));

	}

	public void doEpicsTest(ZHDatabase db) {

		GetEpicsResponseJson olEpics = db.getEpics(openLibertyRepoId).orElse(null);
		assertNotNull(olEpics);

		List<EpicIssueJson> issues = olEpics.getEpic_issues();
		assertNotNull(issues);

		assertTrue(issues.size() > 10);

		assertTrue(issues.stream().allMatch(issue -> issue.getIssue_url() != null && !issue.getIssue_url().trim().isEmpty()));

		// Match a specific known epic
		assertTrue(issues.stream().allMatch(issue -> issue.getIssue_number() > 1 && issue.getRepo_id() == openLibertyRepoId));

		// Match a specific known epic
		assertTrue(issues.stream().anyMatch(issue -> issue.getIssue_number() == 75 && issue.getRepo_id() == openLibertyRepoId
				&& issue.getIssue_url().equals("https://github.com/OpenLiberty/open-liberty/issues/75")));

		System.out.println(issues.subList(0, 5).stream().map(e -> e.getIssue_number() + " ").collect(Collectors.toList()));

		List<GetEpicResponseJson> firstFiveResponses = issues.subList(0, 5).parallelStream().map(e -> {
			GetEpicResponseJson gerj = db.getEpic(openLibertyRepoId, e.getIssue_number()).orElse(null);
			if (gerj == null) {
				System.out.println("Unable to find:" + e.getIssue_number());
			}
			return gerj;
		}).collect(Collectors.toList());

		firstFiveResponses.stream().forEach(epic -> {
			assertNotNull(epic);

			assertNotNull(epic.getPipeline());

			assertNotNull(epic.getTotal_epic_estimates());
			assertTrue(epic.getTotal_epic_estimates().getValue() > 1);

			assertNotNull(epic.getIssues());
			assertTrue(epic.getIssues().size() > 0);

			epic.getIssues().forEach(issue -> {
				assertTrue(issue.getIssue_number() > 1);
				assertTrue(issue.getRepo_id() > 1);
			});

		});

		// At least one must have an estimate
		assertTrue(firstFiveResponses.stream().anyMatch(e -> {
			return e.getEstimate() != null && e.getEstimate().getValue() > 1;
		}));

	}

	public void doIssuesTest(ZHDatabase db) {

		// Test issue data for a specific issue
		{
			GetIssueDataResponseJson gidrj = db.getIssueData(openLibertyRepoId, 1423).orElse(null);
			assertNotNull(gidrj);

			assertTrue(gidrj.isIs_epic());

		}

		// Test issue events for a specific issue
		{

			List<IssueEventJson> events = db.getIssueEvents(openLibertyRepoId, 1423).orElse(null);
			assertNotNull(events);

			List<IssueEventJson> expectedEvents = new ArrayList<>();

			// Filter out any events newer than when this test was created.
			events = events.stream().filter(e -> e.getCreated_at().before(new Date(1554826382578l + 1)))
					.collect(Collectors.toList());

			assertTrue(events.size() == 5);

			IssueEventJson next = new IssueEventJson();
			next.setUser_id(5427967);
			next.setType("transferIssue");
			next.setCreated_at(new Date(1554826382578l));
			next.setWorkspace_id("5c3e0d32cd4b0547e1da3c5b");

			PipelineJson fromPipeline = new PipelineJson();
			fromPipeline.setName("New Issues");
			next.setFrom_pipeline(fromPipeline);

			PipelineJson toPipeline = new PipelineJson();
			toPipeline.setName("In Progress");
			next.setTo_pipeline(toPipeline);

			expectedEvents.addAll(Arrays.asList(next,

					createIssueEventJson(5427967, "addIssueToEpic", 1554826369923l),
					createIssueEventJson(5427967, "addIssueToEpic", 1550429936395l),
					createIssueEventJson(5427967, "addEpicToIssue", 1514929331990l),
					createIssueEventJson(5427967, "convertIssueToEpic", 1514929319394l)));

			assertTrue(expectedEvents.toString().equals(events.toString()));

		}
	}

	private static IssueEventJson createIssueEventJson(int userId, String type, long createdAt) {
		IssueEventJson iej = new IssueEventJson();
		iej.setUser_id(userId);
		iej.setType(type);
		iej.setCreated_at(new Date(createdAt));
		return iej;
	}

}
