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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

/** Very simple log utility. */
public class ZHLog {

	private static final ZHLog instance = new ZHLog();

	private final SimpleDateFormat PRETTY_DATE_FORMAT = new SimpleDateFormat("MMM d h:mm:ss.SSS a");

	private final long startTimeInNanos = System.nanoTime();

	private final LogLevel level;

	private final LogLevel DEFAULT_LOG_LEVEL = LogLevel.INFO;

	private enum LogLevel {
		DEBUG, INFO, ERROR, SEVERE
	}

	private ZHLog() {
		level = DEFAULT_LOG_LEVEL;
	}

	public static ZHLog getInstance() {
		return instance;
	}

	private void out(String str) {
		System.out.println(time() + " " + str);
	}

	private void err(String str) {
		String output = time() + " " + str;

		System.err.println(output);
	}

	public void processing(GHRepository repo) {
		out("Processing repo " + repo.getName());
	}

	public void processing(GHIssue issue, String parent) {
		out("Processing issue " + parent + "/" + issue.getNumber());
	}

	public void processing(GHUser user) {
		out("Processing user " + user.getLogin());
	}

	public void processing(String ownerName) {
		out("Processing owner " + ownerName);
	}

	public void logDebug(String msg) {
		if (level != LogLevel.DEBUG) {
			return;
		}
		out(msg);
	}

	public void logInfo(String msg) {
		if (level == LogLevel.ERROR || level == LogLevel.SEVERE) {
			return;
		}
		out(msg);
	}

	public void logError(String msg) {
		this.logError(msg, null);
	}

	public void logError(String msg, Throwable t) {
		if (level == LogLevel.SEVERE) {
			return;
		}

		String outputMsg = "ERROR  ----- " + msg;

		if (t != null) {
			outputMsg += " " + convertStackTraceToString(t);
		}

		err(outputMsg);
	}

	public void logSevere(String msg) {
		this.logSevere(msg, null);
	}

	public void logSevere(String msg, Throwable t) {

		String outputMsg = "SEVERE ----- " + msg;

		if (t != null) {
			outputMsg += " " + convertStackTraceToString(t);
		}

		err(outputMsg);

	}

	private final String time() {
		long time = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);

		long seconds = time / 1000;

		long msecs = time % 1000;

		String msecsStr = Long.toString(msecs);

		while (msecsStr.length() < 3) {
			msecsStr = "0" + msecsStr;
		}

		return PRETTY_DATE_FORMAT.format(new Date()) + " [" + seconds + "." + msecsStr + "]";

	}

	private static String convertStackTraceToString(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}
}
