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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.zhapi.service.yaml.ZHConfigFileYaml;
import com.zhapimirror.ZHDatabase;
import com.zhapimirror.ZHServerInstance;
import com.zhapimirror.ZHServerInstance.ZHServerInstanceBuilder;

/**
 * Only a single instance of a number of objects are maintained in the
 * application, include the ZHServerInstance and ZHDatabase. This class
 * maintains references to those.
 * 
 * This class also handles initial configuration of the above classes.
 */
public class ZHApiMirrorInstance {

	private static final ZHApiMirrorInstance instance = new ZHApiMirrorInstance();

	private ZHApiMirrorInstance() {

		String configPath = lookupString("zenhub-api-mirror/config-path", true).get();

		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

			List<String> userReposList = new ArrayList<>();
			List<String> orgList = new ArrayList<>();
			List<String> individualReposList = new ArrayList<>();

			ZHConfigFileYaml sf = mapper.readValue(new FileInputStream(new File(configPath)), ZHConfigFileYaml.class);

			this.presharedKey = sf.getPresharedKey();

			if (sf.getUserRepoList() != null) {
				userReposList.addAll(sf.getUserRepoList());
			}

			if (sf.getIndividualRepoList() != null) {
				individualReposList.addAll(sf.getIndividualRepoList());
			}

			if (sf.getOrgList() != null) {
				orgList.addAll(sf.getOrgList());
			}

			if (orgList.size() == 0 && userReposList.size() == 0 && individualReposList.size() == 0) {
				throw new RuntimeException(
						"Neither a list of organizations, a list of users, nor individual repos, was specified in the server.xml");
			}

			String dbPath = sf.getDbPath();

			// The database path of the YAML can be override by this JNDI value in the
			// server.xml. This is used when running within a container.
			Optional<String> jndiOverride = lookupString("zenhub-api-mirror/db-path", false);
			if (jndiOverride.isPresent()) {
				dbPath = jndiOverride.get();
			}

			ZHServerInstanceBuilder builder = ZHServerInstance.builder().githubServerName(sf.getGithubServer())
					.githubUsername(sf.getGithubUsername()).githubPassword(sf.getGithubPassword())
					.zenhubServerName(sf.getZenhubServer()).zenhubApiKey(sf.getZenhubApiKey()).dbDir(new File(dbPath));

			if (!orgList.isEmpty()) {
				builder = builder.orgNames(orgList);
			}
			if (!userReposList.isEmpty()) {
				builder = builder.userRepos(userReposList);
			}

			if (!individualReposList.isEmpty()) {
				builder = builder.individualRepos(individualReposList);
			}

			this.serverInstance = builder.build();

			db = serverInstance.getDb();

		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	private static Optional<String> lookupString(String key, boolean required) {

		try {
			InitialContext context = new InitialContext();
			return Optional.of((String) context.lookup(key));
		} catch (NamingException e) {
			if (required) {
				throw new RuntimeException("JNDI key not found in server.xml: " + key);
			}
			return Optional.empty();
		}
	}

	public static ZHApiMirrorInstance getInstance() {
		return instance;
	}

	// -----------------------------------------

	private final ZHServerInstance serverInstance;

	private final ZHDatabase db;

	private final String presharedKey;

	public ZHDatabase getDb() {
		return db;
	}

	public String getPresharedKey() {
		return presharedKey;
	}

}
