/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.election;

import poke.server.managers.LogManager;

/**
 * abstraction for election ID generation
 * 
 * @author gash
 * 
 */
public class ElectionIDGenerator {

	private static int masterID = 0;

	/**
	 * note this ID is sensitive to differences across nodes
	 * 
	 * @return
	 */
	public static synchronized int nextID() {
		if (masterID == Integer.MAX_VALUE) {
			masterID = 0;
		}
		return ++masterID;
	}

	public static synchronized void setMasterID(int id) {
		masterID = id;
	}
}