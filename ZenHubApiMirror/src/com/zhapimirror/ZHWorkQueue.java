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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.zhapi.ZenHubClient;

/**
 * Maintains a list of all of the repositories/issues that are currently waiting
 * to be processed by a worker thread.
 * 
 * The ZHWorkerThread thread may call an instance of this class, in order to:
 * add additional work, query if work is available, and poll for new work by
 * type.
 */
public class ZHWorkQueue {

	private final Object lock = new Object();

	private final List<ZHRepositoryContainer> repositories_synch_lock = new ArrayList<>();
	private final List<ZHIssueContainer> issues_synch_lock = new ArrayList<>();

	/** Whether a resource is in the work queue; the map value is not used. */
	private final Map<String /* unique key for each resource */, Boolean> resourcesMap = new HashMap<>();

	private final GitHub githubClient;

	private final ZenHubClient zenhubClient;

	private final ZHDatabase database;

	private final ZHFilter filter;

	private static final ZHLog log = ZHLog.getInstance();

	ZHWorkQueue(ZenHubClient zenhubClient, GitHub githubClient, ZHDatabase database, ZHFilter filter) {
		this.githubClient = githubClient;
		this.zenhubClient = zenhubClient;
		this.database = database;
		this.filter = filter;
	}

	void addRepository(GHOwner owner, GHRepository repo, String repoName, long repoId) {

		if (filter != null && !filter.processRepo(owner, repoName)) {
			return;
		}

		ZHRepositoryContainer r = new ZHRepositoryContainer(owner, repo, repoName, repoId);

		String key = r.getKey();

		// Prevent duplicates in the work queue
		if (resourcesMap.containsKey(key)) {
			return;
		}
		log.logDebug("Adding repository: " + repoName);

		synchronized (lock) {
			resourcesMap.put(key, true);
			repositories_synch_lock.add(r);
			lock.notify();
		}
	}

	void addRepositoryFromRetry(ZHRepositoryContainer container) {

		String key = container.getKey();

		// Prevent duplicates in the work queue
		if (resourcesMap.containsKey(key)) {
			return;
		}

		log.logDebug("Adding repository (from retry): " + container.getRepoName());

		synchronized (lock) {
			resourcesMap.put(key, true);
			repositories_synch_lock.add(container);
			lock.notify();
		}
	}

	void addIssue(GHOwner owner, GHRepository repo, GHIssue issue) {

		if (filter != null && !filter.processIssue(owner, repo.getName(), issue.getNumber())) {
			return;
		}

		ZHIssueContainer c = new ZHIssueContainer(owner, repo, issue);

		String key = c.getKey();
		// Prevent duplicates in the work queue
		if (resourcesMap.containsKey(key)) {
			return;
		}

		log.logDebug("Adding issue: " + repo.getName() + " " + issue.getNumber());

		synchronized (lock) {
			issues_synch_lock.add(c);
			resourcesMap.put(key, true);
			lock.notify();
		}

	}

	void addIssueFromRetry(ZHIssueContainer issue) {

		String key = issue.getKey();

		// Prevent duplicates in the work queue
		if (resourcesMap.containsKey(key)) {
			return;
		}
		log.logDebug("Adding issue (from retry): " + issue.getRepo().getName() + " " + issue.getIssue().getNumber());

		synchronized (lock) {
			issues_synch_lock.add(issue);
			resourcesMap.put(key, true);
			lock.notify();
		}
	}

	void waitForAvailableWork() {
		synchronized (lock) {
			while (true) {
				if (availableWork() > 0) {
					return;
				}
				try {
					lock.wait(20);
				} catch (InterruptedException e) {
					ZHUtil.throwAsUnchecked(e);
				}
			}

		}
	}

	long availableWork() {
		long workAvailable = 0;

		synchronized (lock) {
			workAvailable += repositories_synch_lock.size();
			workAvailable += issues_synch_lock.size();
		}

		return workAvailable;

	}

	Optional<ZHRepositoryContainer> pollRepository() {
		synchronized (repositories_synch_lock) {
			if (repositories_synch_lock.isEmpty()) {
				return Optional.empty();
			}

			ZHRepositoryContainer result = repositories_synch_lock.remove(0);
			resourcesMap.remove(result.getKey());
			return Optional.of(result);

		}
	}

	Optional<ZHIssueContainer> pollIssue() {
		synchronized (issues_synch_lock) {
			if (issues_synch_lock.isEmpty()) {
				return Optional.empty();
			}

			ZHIssueContainer result = issues_synch_lock.remove(0);
			resourcesMap.remove(result.getKey());
			return Optional.of(result);

		}
	}

	GitHub getGithubClient() {
		return githubClient;
	}

	ZenHubClient getZenhubClient() {
		return zenhubClient;
	}

	ZHFilter getFilter() {
		return filter;
	}

	/**
	 * A piece of a work in the work queue, specifically an issue, plus additional
	 * required fields.
	 */
	static class ZHIssueContainer {
		private final GHOwner owner;
		private final GHRepository repo;
		private final GHIssue issue;
		private final String hashKey;

		public ZHIssueContainer(GHOwner owner, GHRepository repo, GHIssue issue) {
			this.repo = repo;
			this.issue = issue;
			this.owner = owner;
			this.hashKey = calculateKey();
		}

		public GHRepository getRepo() {
			return repo;
		}

		public GHIssue getIssue() {
			return issue;
		}

		public String getKey() {
			return hashKey;
		}

		private String calculateKey() {
			StringBuilder sb = new StringBuilder();
			sb.append(owner.toString());
			sb.append("-");
			sb.append(repo.getName());
			sb.append("-");
			sb.append(issue);

			return sb.toString();
		}

		@Override
		public boolean equals(Object param) {
			if (!(param instanceof ZHIssueContainer)) {
				return false;
			}
			ZHIssueContainer other = (ZHIssueContainer) param;

			return other.getIssue().getNumber() == this.getIssue().getNumber() && repo.getName().equals(other.repo.getName())
					&& other.owner.equals(this.owner);
		}

	}

	/**
	 * A piece of a work in the work queue, specifically a repository, plus
	 * additional required fields.
	 */
	static class ZHRepositoryContainer {

		private final GHOwner owner;
		private final GHRepository repo;
		private final String repoName;
		private final long repoId;

		private final String hashKey;

		public ZHRepositoryContainer(GHOwner owner, GHRepository repo, String repoName, long repoId) {
			this.owner = owner;
			this.repoName = repoName;
			this.repo = repo;
			this.repoId = repoId;
			this.hashKey = calculateKey();
		}

		public GHOwner getOwner() {
			return owner;
		}

		public String getRepoName() {
			return repoName;
		}

		public long getRepoId() {
			return repoId;
		}

		public GHRepository getRepo() {
			return repo;
		}

		public String getKey() {
			return hashKey;
		}

		private String calculateKey() {
			StringBuilder sb = new StringBuilder();
			sb.append(owner.toString());
			sb.append("-");
			sb.append(repoName);
			sb.append("-");
			sb.append(repoId);

			return sb.toString();
		}

		@Override
		public boolean equals(Object param) {
			if (!(param instanceof ZHRepositoryContainer)) {
				return false;
			}
			ZHRepositoryContainer other = (ZHRepositoryContainer) param;

			return this.repoId == other.repoId && this.repoName.equals(other.repoName) && this.owner.equals(other.owner);

		}
	}

	public ZHDatabase getDb() {
		return database;
	}

}
