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

import com.zhapi.ApiResponse;
import com.zhapi.json.responses.GetEpicResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;

/**
 * Issues an HTTP request to the ZHMirror service, for the corresponding ZenHub
 * Epics or Epic resource.
 */
public class EpicsService {

	private final ZenHubMirrorApiClient zenhubClient;

	public EpicsService(ZenHubMirrorApiClient zenhubClient) {
		this.zenhubClient = zenhubClient;
	}

	public ApiResponse<GetEpicsResponseJson> getEpics(long repoId) {

		String url = "/epics/" + repoId;

		ApiResponse<GetEpicsResponseJson> response = zenhubClient.get(url, GetEpicsResponseJson.class);

		return new ApiResponse<GetEpicsResponseJson>((GetEpicsResponseJson) response.getResponse(),
				response.getRateLimitStatus(), response.getResponseBody());

	}

	public ApiResponse<GetEpicResponseJson> getEpic(long repo_id, int epic_id) {
		String url = "/epic/" + repo_id + "/" + epic_id;

		ApiResponse<GetEpicResponseJson> response = zenhubClient.get(url, GetEpicResponseJson.class);

		return new ApiResponse<GetEpicResponseJson>((GetEpicResponseJson) response.getResponse(),
				response.getRateLimitStatus(), response.getResponseBody());

	}
}
