/*
 * Copyright 2020 Jonathan West
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
import com.zhapi.shared.json.RepositoryChangeEventJson;

public class ZenHubMirrorEventsService {

	private final ZenHubMirrorApiClient zenhubClient;

	public ZenHubMirrorEventsService(ZenHubMirrorApiClient zenhubClient) {
		this.zenhubClient = zenhubClient;
	}

	public List<RepositoryChangeEventJson> getResourceChangeEvents(long timestampEqualOrGreater) {

		ApiResponse<RepositoryChangeEventJson[]> response = zenhubClient
				.get("/repositoryChangeEvent?since=" + timestampEqualOrGreater, RepositoryChangeEventJson[].class);

		List<RepositoryChangeEventJson> result = new ArrayList<>();

		result.addAll(Arrays.asList(response.getResponse()));

		return result;

	}

}
