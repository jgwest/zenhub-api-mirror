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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;

import com.zhapi.ZenHubClient;
import com.zhapimirror.ZHWorkQueue.ZHRepositoryContainer;

/**
 * An instance of this class will mirror ZenHub resources using the given
 * parameters. This includes initializing required internal resources
 * (databases, background threads), maintaining references to the internal state
 * of the process, and managing the lifecycle of that process.
 * 
 * To construct an instance of this class, call ZHServerInstance.builder().
 */
public class ZHServerInstance {

	private final ZHWorkQueue queue;

	private final List<GHOrganization> ghOrgList;

	private final List<GHUser> ghUserReposList;

	private final List<ZHRepositoryContainer> ghIndividualReposList;

	private final ZHDatabase db;

	private final ZenHubClient zenhubClient;

	private final long timeBetweenEventScansInNanos = TimeUnit.NANOSECONDS.convert(4, TimeUnit.MINUTES);

	private final GitHub githubClient;

	private final ZHBackgroundSchedulerThread backgroundSchedulerThread;

	private final ZHLog log = ZHLog.getInstance();

	private ZHServerInstance(String username, String password, String serverName, String zenhubServerName, String zenhubApiKey,
			List<String> orgNames, List<String> userRepos, List<String> individualRepos, File dbDir, ZHFilter filter) {

		if (filter == null) {
			filter = new PermissiveFilter();
		}

		// Verify that we don't have both an org, and an individual repo under that org.
		// eg: asking the server to index both the eclipse org, and the
		// eclipse/che individual repo.
		List<String> ownersOfIndividualRepos = individualRepos.stream().map(e -> e.substring(0, e.indexOf("/"))).distinct()
				.collect(Collectors.toList());
		if (ownersOfIndividualRepos.stream().anyMatch(e -> orgNames.contains(e))
				|| ownersOfIndividualRepos.stream().anyMatch(e -> userRepos.contains(e))) {
			throw new RuntimeException(
					"You cannot include an individual repo if you have also included the organization of that repo.");
		}

		this.db = new ZHInMemoryCacheDb(new ZHPersistJsonDb(dbDir));
		this.db.uninitializeDatabaseOnContentsMismatch(orgNames, userRepos, individualRepos);

		try {
			GitHubBuilder builder = GitHubBuilder.fromEnvironment();

			if (username == null || password == null) {
				System.err.println(
						"WARNING: Using anonymous GitHub access; this is not recommend as GitHub has very strict rate limits for anonymous users.");
			} else {
				builder = builder.withPassword(username, password);
			}

			if (serverName != null && !serverName.toLowerCase().endsWith("github.com")) {
				builder = builder.withEndpoint("https://" + serverName + "/api/v3");
			}

			builder = builder.withAbuseLimitHandler(AbuseLimitHandler.FAIL).withRateLimitHandler(RateLimitHandler.FAIL);

			githubClient = builder.build();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		zenhubClient = new ZenHubClient(zenhubServerName, zenhubApiKey);

		queue = new ZHWorkQueue(zenhubClient, githubClient, db, filter);
		ghOrgList = new ArrayList<>();

		ghUserReposList = new ArrayList<>();

		ghIndividualReposList = new ArrayList<>();

		// If we have hit the API rate limit, keep trying to get the org/user until we
		// succeed.
		boolean success = false;
		do {

			try {

				ghOrgList.clear();

				if (orgNames != null) {
					for (String orgName : orgNames) {
						ghOrgList.add(githubClient.getOrganization(orgName));
					}
				}

				ghUserReposList.clear();

				if (userRepos != null) {
					for (String userName : userRepos) {
						ghUserReposList.add(githubClient.getUser(userName));
					}
				}

				ghIndividualReposList.clear();
				if (individualRepos != null) {

					// Determine if the repo string refers to an org or a user repo, resolve it,
					// then add it to the individual repo list.
					for (String fullRepoName : individualRepos) {
						int slashIndex = fullRepoName.indexOf("/");
						if (slashIndex == -1) {
							throw new RuntimeException("Invalid repository format: " + fullRepoName);
						}

						String ownerName = fullRepoName.substring(0, slashIndex);
						String repoName = fullRepoName.substring(slashIndex + 1);

						GHUser user = null;
						GHOrganization org = githubClient.getOrganization(ownerName);
						if (org == null) {
							user = githubClient.getUser(ownerName);
							if (user == null) {
								throw new RuntimeException("Unable to find user repo or org for: " + fullRepoName);
							}
						}

						GHOwner owner = GHOwner.org(ownerName);

						GHRepository repo;

						if (org != null) {
							repo = org.getRepository(repoName);
						} else {
							repo = user.getRepository(repoName);
						}

						if (repo == null) {
							throw new RuntimeException(
									"Unable to find user repo or org, after request to GitHub API: " + fullRepoName);
						}

						ZHRepositoryContainer rc = new ZHRepositoryContainer(owner, repo, repoName, repo.getId());
						ghIndividualReposList.add(rc);
					}

				}

				success = true;
			} catch (Exception e) {
				e.printStackTrace();
				success = false;
				System.err.println("Failed in constructor, retrying in 60 seconds.");
				ZHUtil.sleep(60 * 1000);
			}

		} while (!success);

		for (int x = 0; x < 2; x++) {
			ZHWorkerThread wt = new ZHWorkerThread(queue, x + 1);
			wt.start();
		}

		backgroundSchedulerThread = new ZHBackgroundSchedulerThread();
		backgroundSchedulerThread.start();

	}

	public ZHDatabase getDb() {
		return db;
	}

	public static ZHServerInstanceBuilder builder() {
		return new ZHServerInstanceBuilder();
	}

	/**
	 * A single background thread is running at all times, for a single server
	 * instance. This thread is responsible for retrieving an updated copy of the
	 * lowest-bandwidth ZH resources every X seconds, and also doing a full resource
	 * scan every day.
	 */
	private class ZHBackgroundSchedulerThread extends Thread {

		private boolean fullScanInProgress = false;

		public ZHBackgroundSchedulerThread() {
			setName(ZHBackgroundSchedulerThread.class.getName());
			setDaemon(true);
		}

		/** This is called every 60 seconds */
		private void innerRun(Map<Long /* (year * 1000) + day_of_year */, Boolean> hasDailyScanRunToday,
				AtomicLong nextEventScanInNanos) throws IOException {

			Calendar c = Calendar.getInstance();
			int hour = c.get(Calendar.HOUR_OF_DAY);
			int dayOfYear = c.get(Calendar.DAY_OF_YEAR);
			int year = c.get(Calendar.YEAR);

			boolean runFullScan = (hour == 3 || !getDb().isDatabaseInitialized());

			// Run a full scan if the database has not been refreshed in X hours
			Long lastFullScan = db.getLong(ZHDatabase.LAST_FULL_SCAN).orElse(null);
			if (lastFullScan != null && !fullScanInProgress) {
				long elapsedTimeInHours = TimeUnit.HOURS.convert(System.currentTimeMillis() - lastFullScan,
						TimeUnit.MILLISECONDS);
				if (elapsedTimeInHours > 48) {
					log.logInfo("* Database is " + elapsedTimeInHours + " hours old, so running full scan.");
					runFullScan = true;
				}
			}

			if (runFullScan) {

				// Run the full scan if the DB has not been initialized, or it is 3AM

				if (!getDb().isDatabaseInitialized()) {
					getDb().initializeDatabase();
				}

				long value = year * 1000 + dayOfYear;
				if (!hasDailyScanRunToday.containsKey(value)) {
					hasDailyScanRunToday.put(value, true);

					ghOrgList.forEach(e -> {

						GHOwner owner = GHOwner.org(e.getLogin());

						try {
							e.getRepositories().values().forEach(repo -> {
								queue.addRepository(owner, repo, repo.getName(), repo.getId());
							});
						} catch (IOException e1) {
							ZHUtil.throwAsUnchecked(e1);
						}

					});

					ghUserReposList.forEach(e -> {
						GHOwner owner = GHOwner.user(e.getLogin());

						try {
							e.getRepositories().values().forEach(repo -> {
								queue.addRepository(owner, repo, repo.getName(), repo.getId());
							});
						} catch (IOException e1) {
							ZHUtil.throwAsUnchecked(e1);
						}

					});

					ghIndividualReposList.forEach(e -> {
						GHOwner owner = e.getOwner();
						queue.addRepository(owner, e.getRepo(), e.getRepoName(), e.getRepoId());
					});

					this.fullScanInProgress = true;

				}

			} else if (hour >= 2 && hour <= 4) {
				/* ignore */

			} else if (queue.availableWork() <= 100) {

				if (queue.availableWork() == 0 && this.fullScanInProgress) {
					// A full scan is considered completed if started, and then
					// the available work dropped to 0.
					this.fullScanInProgress = false;
					db.persistLong(ZHDatabase.LAST_FULL_SCAN, System.currentTimeMillis());
				}

				if (System.nanoTime() >= nextEventScanInNanos.get()) {
					nextEventScanInNanos.set(System.nanoTime() + timeBetweenEventScansInNanos);
					for (GHOrganization org : ghOrgList) {
						ZHRepositoryResourceScan.doScan(GHOwner.org(org.getLogin()), zenhubClient, githubClient, db);
					}

					for (GHUser user : ghUserReposList) {
						ZHRepositoryResourceScan.doScan(GHOwner.user(user.getLogin()), zenhubClient, githubClient, db);
					}

					if (ghIndividualReposList.size() > 0) {
						ZHRepositoryResourceScan.doScan(ghIndividualReposList, zenhubClient, githubClient, db);
					}

				}

			}

		}

		@Override
		public void run() {

			AtomicLong nextEventScanInNanos = new AtomicLong(System.nanoTime());

			// Whether the daily scan has run today
			Map<Long /* year * 1000 + day_of_year */, Boolean> hasDailyScanRunToday = new HashMap<>();

			while (true) {

				try {
					innerRun(hasDailyScanRunToday, nextEventScanInNanos);
				} catch (Exception e) {
					// Log and ignore
					log.logError("Error occurred in " + this.getClass().getName(), e);
				}

				ZHUtil.sleep(60 * 1000);

			}

		}

	}

	/**
	 * Call ZHServerInstance.builder() to get an instance of this class; this class
	 * is used to construct an instance of ZHServerInstance using a fluent builder
	 * API.
	 */
	public static class ZHServerInstanceBuilder {
		private String ghUsername;
		private String ghPassword;
		private String ghServerName;
		private String zenhubServerName;
		private String zenhubApiKey;
		private List<String> orgNames = new ArrayList<>();
		private List<String> userRepos = new ArrayList<>();
		private List<String> individualRepos = new ArrayList<>();
		private File dbDir;

		private ZHFilter filter;

		private ZHServerInstanceBuilder() {
		}

		public ZHServerInstanceBuilder githubUsername(String username) {
			this.ghUsername = username;
			return this;
		}

		public ZHServerInstanceBuilder githubPassword(String password) {
			this.ghPassword = password;
			return this;
		}

		public ZHServerInstanceBuilder githubServerName(String servername) {
			if (servername == null) {
				servername = "github.com";
			}
			this.ghServerName = servername;
			return this;
		}

		public ZHServerInstanceBuilder zenhubServerName(String servername) {
			if (servername == null) {
				servername = "api.zenhub.io";
			}

			this.zenhubServerName = servername;
			return this;
		}

		public ZHServerInstanceBuilder zenhubApiKey(String zhapikey) {
			this.zenhubApiKey = zhapikey;
			return this;
		}

		public ZHServerInstanceBuilder orgNames(List<String> orgNames) {
			this.orgNames = orgNames;
			return this;
		}

		public ZHServerInstanceBuilder userRepos(List<String> userRepoNames) {
			this.userRepos = userRepoNames;
			return this;
		}

		public ZHServerInstanceBuilder individualRepos(List<String> reposList) {
			this.individualRepos = reposList;
			return this;
		}

		public ZHServerInstanceBuilder dbDir(File dbDir) {
			this.dbDir = dbDir;
			return this;
		}

		public ZHServerInstanceBuilder filter(ZHFilter filter) {
			this.filter = filter;
			return this;
		}

		public ZHServerInstance build() {
			return new ZHServerInstance(ghUsername, ghPassword, ghServerName, zenhubServerName, zenhubApiKey, orgNames, userRepos,
					individualRepos, dbDir, filter);
		}

	}

	/** If no filter is specified, we use a filter that accepts all resources. */
	private static class PermissiveFilter implements ZHFilter {

		@Override
		public boolean processRepo(GHOwner owner, String repoName) {
			return true;
		}

		@Override
		public boolean processIssue(GHOwner owner, String repoName, int issue) {
			return true;
		}

	}
}
