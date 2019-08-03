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
 * Owner refers to either a GitHub organization, or a GitHub user. In both
 * cases, the GitHub resource will reference a list of owned repositories.
 */
public class GHOwner {

	public static enum Type {
		ORG, USER
	}

	private final String name;
	private final Type type;

	private GHOwner(String name, Type type) {
		if (name == null) {
			throw new IllegalArgumentException();
		}
		this.name = name;
		this.type = type;
	}

	public String getOrgNameOrNull() {
		if (type == Type.ORG) {
			return name;
		} else {
			return null;
		}
	}

	public String getUserNameOrNull() {
		if (type == Type.USER) {
			return name;
		} else {
			return null;
		}
	}

	public Type getType() {
		return type;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof GHOwner)) {
			return false;
		}
		GHOwner other = (GHOwner) obj;

		return other.getType() == this.getType() && other.name.equals(this.name);

	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(type.name());
		sb.append(" - ");
		sb.append(name);
		return sb.toString();
	}

	// ------------------------------------------

	public static GHOwner org(String name) {
		return new GHOwner(name, Type.ORG);
	}

	public static GHOwner user(String name) {
		return new GHOwner(name, Type.USER);
	}

	public String getName() {
		return name;
	}
}
