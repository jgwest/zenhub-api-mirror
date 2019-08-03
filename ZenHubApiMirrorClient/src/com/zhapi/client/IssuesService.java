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

package com.zhapi.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.zhapi.ApiResponse;
import com.zhapi.json.IssueEventJson;
import com.zhapi.json.responses.GetIssueDataResponseJson;

/**
 * Issues an HTTP request to the ZHMirror service, for the corresponding Issue
 * or Issue events board resources.
 */
public class IssuesService {

	private final ZenHubMirrorApiClient zenhubClient;

	public IssuesService(ZenHubMirrorApiClient zenhubClient) {
		this.zenhubClient = zenhubClient;
	}

	public ApiResponse<GetIssueDataResponseJson> getIssueData(long repoId, int issueNumber) {

		String url = "/issueData/" + repoId + "/" + issueNumber;

		ApiResponse<GetIssueDataResponseJson> response = zenhubClient.get(url, GetIssueDataResponseJson.class);

		return new ApiResponse<GetIssueDataResponseJson>((GetIssueDataResponseJson) response.getResponse(),
				response.getRateLimitStatus(), response.getResponseBody());

	}

	public ApiResponse<List<IssueEventJson>> getIssueEvents(long repoId, int issueNumber) {

		String url = "/issueEvents/" + repoId + "/" + issueNumber;

		ApiResponse<IssueEventJson[]> response = zenhubClient.get(url, IssueEventJson[].class);

		IssueEventJson[] arrayResult = (IssueEventJson[]) response.getResponse();

		List<IssueEventJson> result = new ArrayList<>();

		result.addAll(Arrays.asList(arrayResult));

		return new ApiResponse<List<IssueEventJson>>(result, response.getRateLimitStatus(), response.getResponseBody());

	}
}
