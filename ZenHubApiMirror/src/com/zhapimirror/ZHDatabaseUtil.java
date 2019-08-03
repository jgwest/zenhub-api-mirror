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

/**
 * Utility functions that may be used by implementers of the ZHDatabase
 * interface.
 */
public class ZHDatabaseUtil {

	private static void generateKey(StringBuilder sb, long repoId) {
		sb.append(repoId);
	}

	private static void generateKey(StringBuilder sb, long repoId, int issueId) {
		sb.append(repoId);
		sb.append("/");
		sb.append(issueId);
	}

	public static String generateIssueDataKey(long repoId, int issueId) {
		StringBuilder sb = new StringBuilder();

		generateKey(sb, repoId, issueId);
		sb.append("/issue-data");

		return sb.toString();

	}

	public static String generateIssueEventsKey(long repoId, int issueId) {
		StringBuilder sb = new StringBuilder();

		generateKey(sb, repoId, issueId);
		sb.append("/issue-events");

		return sb.toString();
	}

	public static String generateZenHubBoardKey(long repoId) {
		StringBuilder sb = new StringBuilder();

		generateKey(sb, repoId);
		sb.append("/zenhub-board");

		return sb.toString();
	}

	public static String generateDependenciesForARepoKey(long repoId) {
		StringBuilder sb = new StringBuilder();

		generateKey(sb, repoId);
		sb.append("/dependencies");

		return sb.toString();
	}

	public static String generateEpicsPluralKey(long repoId) {
		StringBuilder sb = new StringBuilder();

		generateKey(sb, repoId);
		sb.append("/epics");

		return sb.toString();
	}

	public static String generateEpicKey(long repoId, int issueId) {
		StringBuilder sb = new StringBuilder();

		generateKey(sb, repoId, issueId);
		sb.append("/epic");

		return sb.toString();
	}

}
