/*
 * Copyright 2019, 2020 Jonathan West
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Various simple JSON utility methods. */
public class JsonUtil {

	public static String toString(Object j) {
		ObjectMapper om = new ObjectMapper();
		try {
			return om.writeValueAsString(j);
		} catch (JsonProcessingException e) {
			ZHUtil.throwAsUnchecked(e);
			return null;
		}
	}

	public static String toPrettyString(Object j) {
		ObjectMapper om = new ObjectMapper();

		try {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(j);
		} catch (JsonProcessingException e) {
			ZHUtil.throwAsUnchecked(e);
			return null;
		}

	}

	/**
	 * Serialize one and two into JSON objects, split each into component
	 * alphanumeric characters, sort them by characters, and then compare the
	 * result.
	 */
	public static boolean isEqualBySortedAlphanumerics(Object one, Object two, ObjectMapper om) throws JsonProcessingException {

		List<String> jsonStrings = Arrays.asList(one, two).stream().map(e -> {
			try {
				if (e == null) {
					return "";
				} else {
					return om.writeValueAsString(e);
				}
			} catch (JsonProcessingException e1) {
				throw new RuntimeException(e1);
			}
		}).map(e -> {
			List<String> chars = new ArrayList<>();
			for (int x = 0; x < e.length(); x++) {
				char ch = e.charAt(x);
				if (Character.isLetterOrDigit(ch)) {
					chars.add(Character.toString(ch));
				}
			}

			return chars.stream().sorted().reduce((a, b) -> a + b).orElse("");

		}).collect(Collectors.toList());

		return jsonStrings.get(0).equals(jsonStrings.get(1));

	}

}
