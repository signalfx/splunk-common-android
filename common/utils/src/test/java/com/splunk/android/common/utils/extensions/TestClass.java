/*
Copyright 2026 Splunk Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.splunk.android.common.utils.extensions;

public class TestClass {

	private boolean booleanValue = true;
	private int intValue = 1;
	private long longValue = 1;
	private float floatValue = 1f;
	private double doubleValue = 1.0;
	private String stringValue = "0";
	private InnerClass innerClassValue = new InnerClass();

	private void callVoid() {
	}

	private boolean getBoolean() {
		return booleanValue;
	}

	private int getInt() {
		return intValue;
	}

	private long getLong() {
		return longValue;
	}

	private float getFloat() {
		return floatValue;
	}

	private double getDouble() {
		return doubleValue;
	}

	private String getString() {
		return stringValue;
	}

	private InnerClass getInnerClass() {
		return innerClassValue;
	}

	public static class InnerClass {
	}
}
