/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.shoal.ha.cache.impl.util;

import org.glassfish.ha.store.api.Storeable;
import org.shoal.ha.cache.api.*;
import org.shoal.ha.mapper.DefaultKeyMapper;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class SimpleDataStoreShell {

	DataStore<String, String> ds;

	ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<String, String>();

	int counter = 0;

	public static void main(String[] args) throws Exception {

		DataStoreContext<String, String> conf = new DataStoreContext<String, String>();
		conf.setStoreName(args[0]).setInstanceName(args[1]).setGroupName(args[2]).setKeyClazz(String.class).setValueClazz(String.class)
		        .setClassLoader(ClassLoader.getSystemClassLoader()).setStartGMS(true).setDoAddCommands();
		DataStore<String, String> rds = (new DataStoreFactory()).createDataStore(conf);

		SimpleDataStoreShell main = new SimpleDataStoreShell();
		main.runShell(rds);
	}

	private void runShell(DataStore<String, String> ds) {
		this.ds = ds;
		String line = "";
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
		do {
			prompt();
			try {
				line = br.readLine();
				List<String> args = new ArrayList<String>();
				for (StringTokenizer tok = new StringTokenizer(line, "\n\r\t\f \f"); tok.hasMoreTokens();) {
					String str = tok.nextToken();
					args.add(str);
				}

				if (args.size() > 0) {
					String command = args.remove(0);
					String[] params = args.toArray(new String[0]);

					execute(command, params);
					counter++;
				}
			} catch (IOException ioEx) {
				ioEx.printStackTrace();
			}
		} while (!"quit".equalsIgnoreCase(line));
	}

	private void prompt() {
		System.out.print("" + counter + ">");
		System.out.flush();
	}

	private void execute(String command, String[] params) {

		try {
			if ("put".equalsIgnoreCase(command)) {
				String key = params[0];
				ds.put(key, params[1]);
				System.out.println("PUT " + key);
				ds.put(key, params[1]);
				/*
				 * for (int i=1; i<8; i++) { String key1 = params[0] + ":" + i; MyStoreable st1 = cache.get(key1); if (st1 == null) {
				 * st1 = new MyStoreable(); cache.put(key1, st1); }
				 * 
				 * if (params.length > 1) { st.setStr1(params[1] + ":" + i); } if (params.length > 2) { st.setStr2(params[2] + ":" + i);
				 * } st.touch(); System.out.println("PUT " + st); ds.save(key1, st1, true); }
				 */
			} else if ("get".equalsIgnoreCase(command)) {
				String value = ds.get(params[0]);
				System.out.println("get(" + params[0] + ") => " + value);
				if (value != null) {
					cache.put(params[0], value);
				}
			} else if ("remove".equalsIgnoreCase(command)) {
				ds.remove(params[0]);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static class MyStoreable implements Storeable {

		long version;

		long accessTime;

		long maxIdleTime;

		String str1;

		String str2;

		boolean[] dirty = new boolean[2];

		public void touch() {
			version++;
			accessTime = System.currentTimeMillis();
			maxIdleTime = 15 * 1000;
		}

		@Override
		public long _storeable_getVersion() {
			return version;
		}

		public String getStr1() {
			return str1;
		}

		public void setStr1(String str1) {
			boolean same = str1 != null ? str1.equals(this.str1) : this.str1 == null;
			if (!same) {
				this.str1 = str1;
				dirty[0] = true;
			}
		}

		public String getStr2() {
			return str2;
		}

		public void setStr2(String str2) {
			boolean same = str2 != null ? str2.equals(this.str2) : this.str2 == null;
			if (!same) {
				this.str2 = str2;
				dirty[1] = true;
			}
		}

		@Override
		public void _storeable_setVersion(long version) {
			this.version = version;
		}

		@Override
		public long _storeable_getLastAccessTime() {
			return maxIdleTime;
		}

		@Override
		public void _storeable_setLastAccessTime(long accessTime) {
			this.accessTime = accessTime;
		}

		@Override
		public long _storeable_getMaxIdleTime() {
			return maxIdleTime;
		}

		@Override
		public void _storeable_setMaxIdleTime(long maxIdleTime) {
			this.maxIdleTime = maxIdleTime;
		}

		@Override
		public String[] _storeable_getAttributeNames() {
			return new String[] { "str1", "str2" };
		}

		@Override
		public boolean[] _storeable_getDirtyStatus() {
			return dirty;
		}

		@Override
		public void _storeable_writeState(OutputStream os) throws IOException {
			DataOutputStream dos = null;
			try {
				dos = new DataOutputStream(os);
				dos.writeBoolean(dirty[0]);
				if (dirty[0]) {
					dos.writeInt(str1 == null ? 0 : str1.length());
					if (str1 != null) {
						dos.write(str1.getBytes(Charset.defaultCharset()));
					}
				}
				dos.writeBoolean(dirty[1]);
				if (dirty[1]) {
					dos.writeInt(str2 == null ? 0 : str2.length());
					if (str2 != null) {
						dos.write(str2.getBytes());
					}
				}
			} finally {
				try {
					dos.flush();
					dos.close();
				} catch (IOException ex) {
				}
			}
		}

		@Override
		public void _storeable_readState(InputStream is) throws IOException {
			DataInputStream dis = null;
			try {
				dis = new DataInputStream(is);
				dirty[0] = dis.readBoolean();
				if (dirty[0]) {
					int strLen = dis.readInt();
					if (strLen > 0) {
						byte[] strBytes = new byte[strLen];
						dis.read(strBytes);
						str1 = new String(strBytes, Charset.defaultCharset());
					}
				}

				dirty[1] = dis.readBoolean();
				if (dirty[1]) {
					int strLen = dis.readInt();
					if (strLen > 0) {
						byte[] strBytes = new byte[strLen];
						dis.read(strBytes);
						str2 = new String(strBytes);
					}
				}
			} finally {
				try {
					dis.close();
				} catch (IOException ex) {
					Logger.getLogger(ShoalCacheLoggerConstants.CACHE).log(Level.FINEST, "Ignorable error while closing DataInputStream");
				}
			}
		}

		@Override
		public String toString() {
			return "MyStoreable{" + "version=" + version + ", accessTime=" + accessTime + ", maxIdleTime=" + maxIdleTime + ", str1='" + str1 + '\'' + ", str2='"
			        + str2 + '\'' + ", dirty=" + Arrays.toString(dirty) + '}';
		}
	}
}
