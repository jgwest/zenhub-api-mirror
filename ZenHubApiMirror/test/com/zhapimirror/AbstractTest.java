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

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.zhapi.ZHUtil;
import com.zhapi.ZenHubApiException;
import com.zhapimirror.ZHServerInstance.ZHServerInstanceBuilder;

/**
 * Utility methods used by the test class(es).
 * 
 * In order to run these tests, you will need to generate an API token using
 * your GitHub ID, at https://app.zenhub.com/dashboard/tokens
 * 
 * When running the test, use the parameter: -Dzenhubapikey=(your key api)
 * 
 * Instead of passing the above parameter, you may instead create a file at
 * ~/.zenhubapitests/test.properties with the following line:
 * 
 * zenhubapikey=(your API key)
 * 
 */
public abstract class AbstractTest {

	// https://github.com/OpenLiberty/open-liberty/
	protected final long openLibertyRepoId = 103694377;

	protected static ZHServerInstanceBuilder getClientBuilder() {
		File f = new File(new File(System.getProperty("user.home"), ".zenhubapitests"), "test.properties");

		Properties props = new Properties();
		if (f.exists()) {
			try {
				props.load(new FileInputStream(f));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		String apiKey = getProperty("zenhubapikey", props);

		if (apiKey == null) {
			// Generate a token in the API Tokens section of your ZenHub Dashboard
			// (https://app.zenhub.com/dashboard/tokens)
			throw new ZenHubApiException(
					"ZenHub API Key not found, use -DskipTests or specify -Dzenhubapikey=(your key api) with your key. Generate a key at https://app.zenhub.com/dashboard/tokens");
		}

		String ghUsername = getProperty("github.username", props);

		String ghPassword = getProperty("github.password", props);

		ZHServerInstanceBuilder build = ZHServerInstance.builder();

		build = build.githubUsername(ghUsername).githubPassword(ghPassword).zenhubServerName("api.zenhub.io")
				.zenhubApiKey(apiKey);

		return build;

	}

	private static String getProperty(String property, Properties props) {

		String apiKey = (String) props.getOrDefault(property, null);

		if (apiKey == null) {
			apiKey = System.getProperty(property);
		}

		if (apiKey == null) {
			fail("Unable to find property '" + property + "'. Specify with -D" + property
					+ "=(...value..), see com.zhapimirror.AbstractTest for details.");
		}

		return apiKey;

	}

	void waitForPass(long timeToWaitInSeconds, Runnable r) {
		waitForPass(timeToWaitInSeconds, false, r);

	}

	void waitForPass(long timeToWaitInSeconds, boolean showExceptions, Runnable r) {

		long expireTimeInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeToWaitInSeconds, TimeUnit.SECONDS);

		Throwable lastThrowable = null;

		int delay = 1000;

		while (System.nanoTime() < expireTimeInNanos) {

			try {
				r.run();
				return;
			} catch (Throwable t) {
				if (showExceptions) {
					t.printStackTrace();
				}

				lastThrowable = t;
				ZHUtil.sleep(delay);
				delay *= 1.5;
				if (delay >= 5000) {
					delay = 5000;
				}
			}

		}

		if (lastThrowable instanceof RuntimeException) {
			throw (RuntimeException) lastThrowable;
		} else {
			throw (Error) lastThrowable;
		}

	}
}
