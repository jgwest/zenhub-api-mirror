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

package com.zhapi.service.yaml;

import java.util.ArrayList;
import java.util.List;

public class ZHConfigFileYaml {

	private String githubServer;

	private String githubUsername;
	private String githubPassword;

	private String zenhubServer;
	private String zenhubApiKey;

	private List<String> userRepoList = new ArrayList<>();

	private List<String> orgList = new ArrayList<>();

	private List<String> individualRepoList = new ArrayList<>();

	private String presharedKey;

	private String dbPath;

	public String getGithubServer() {
		return githubServer;
	}

	public void setGithubServer(String githubServer) {
		this.githubServer = githubServer;
	}

	public String getGithubUsername() {
		return githubUsername;
	}

	public void setGithubUsername(String githubUsername) {
		this.githubUsername = githubUsername;
	}

	public String getGithubPassword() {
		return githubPassword;
	}

	public void setGithubPassword(String githubPassword) {
		this.githubPassword = githubPassword;
	}

	public List<String> getOrgList() {
		return orgList;
	}

	public void setOrgList(List<String> orgList) {
		this.orgList = orgList;
	}

	public List<String> getIndividualRepoList() {
		return individualRepoList;
	}

	public void setIndividualRepoList(List<String> individualRepoList) {
		this.individualRepoList = individualRepoList;
	}

	public String getPresharedKey() {
		return presharedKey;
	}

	public void setPresharedKey(String presharedKey) {
		this.presharedKey = presharedKey;
	}

	public String getDbPath() {
		return dbPath;
	}

	public void setDbPath(String dbPath) {
		this.dbPath = dbPath;
	}

	public List<String> getUserRepoList() {
		return userRepoList;
	}

	public void setUserRepoList(List<String> userRepoList) {
		this.userRepoList = userRepoList;
	}

	public String getZenhubServer() {
		return zenhubServer;
	}

	public void setZenhubServer(String zenhubServer) {
		this.zenhubServer = zenhubServer;
	}

	public String getZenhubApiKey() {
		return zenhubApiKey;
	}

	public void setZenhubApiKey(String zenhubApiKey) {
		this.zenhubApiKey = zenhubApiKey;
	}

}
