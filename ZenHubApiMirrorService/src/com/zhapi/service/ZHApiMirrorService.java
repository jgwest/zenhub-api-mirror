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

package com.zhapi.service;

import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.zhapi.json.IssueEventJson;
import com.zhapi.json.responses.DependenciesForARepoResponseJson;
import com.zhapi.json.responses.GetBoardForRepositoryResponseJson;
import com.zhapi.json.responses.GetEpicResponseJson;
import com.zhapi.json.responses.GetEpicsResponseJson;
import com.zhapi.json.responses.GetIssueDataResponseJson;
import com.zhapimirror.JsonUtil;
import com.zhapimirror.ZHDatabase;
import com.zhapimirror.ZHUtil;

/**
 * A JAX-RS resource class that listens on resource requests to
 * Epic/Epics/IssueData/IssueEvents/Dependencies, queries the database, then
 * returns the result.
 * 
 * Before processing a request, the pre-shared key is verified, here.
 */
@RequestScoped
@Path("/")
public class ZHApiMirrorService {

	@Context
	HttpHeaders headers;

	@GET
	@Path("/dependencies/{repoId}")
	public Response getDependenciesForARepository(@PathParam("repoId") long repoId) {

		verifyHeaderAuth();
		ZHDatabase db = getDb();

		DependenciesForARepoResponseJson deps = db.getDependenciesForARepository(repoId).orElse(null);
		if (deps != null) {
			return Response.ok(JsonUtil.toString(deps)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/board/{repoId}")
	public Response getZenHubBoardForRepo(@PathParam("repoId") long repoId) {

		verifyHeaderAuth();
		ZHDatabase db = getDb();

		GetBoardForRepositoryResponseJson board = db.getZenHubBoardForRepo(repoId).orElse(null);
		if (board != null) {
			return Response.ok(JsonUtil.toString(board)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/epic/{repoId}/{issueId}")
	public Response getEpic(@PathParam("repoId") long repoId, @PathParam("issueId") int issueId) {

		verifyHeaderAuth();
		ZHDatabase db = getDb();

		GetEpicResponseJson epic = db.getEpic(repoId, issueId).orElse(null);
		if (epic != null) {
			return Response.ok(JsonUtil.toString(epic)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/epics/{repoId}")
	public Response getEpics(@PathParam("repoId") long repoId) {

		verifyHeaderAuth();
		ZHDatabase db = getDb();

		GetEpicsResponseJson board = db.getEpics(repoId).orElse(null);
		if (board != null) {
			return Response.ok(JsonUtil.toString(board)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/issueData/{repoId}/{issueId}")
	public Response getIssueData(@PathParam("repoId") long repoId, @PathParam("issueId") int issueId) {

		verifyHeaderAuth();
		ZHDatabase db = getDb();

		GetIssueDataResponseJson issueData = db.getIssueData(repoId, issueId).orElse(null);
		if (issueData != null) {
			return Response.ok(JsonUtil.toString(issueData)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/issueEvents/{repoId}/{issueId}")
	public Response getIssueEvents(@PathParam("repoId") long repoId, @PathParam("issueId") int issueId) {

		verifyHeaderAuth();
		ZHDatabase db = getDb();

		List<IssueEventJson> issueData = db.getIssueEvents(repoId, issueId).orElse(null);
		if (issueData != null) {
			return Response.ok(JsonUtil.toString(issueData)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	private void verifyHeaderAuth() {
		String key = ZHApiMirrorInstance.getInstance().getPresharedKey();

		String authHeader = headers.getHeaderString("Authorization");

		if (authHeader != null && key != null && key.equalsIgnoreCase(authHeader)) {
			return;
		}

		// Delay failure to partially mitigate brute-forcing.
		ZHUtil.sleep(1000);

		throw new IllegalArgumentException("No authorization header was found in the client request.");

	}

	private ZHDatabase getDb() {
		return ZHApiMirrorInstance.getInstance().getDb();
	}
}
