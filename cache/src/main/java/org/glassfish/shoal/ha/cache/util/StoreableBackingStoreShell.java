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

package org.glassfish.shoal.ha.cache.util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.ha.store.api.BackingStore;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStoreException;
import org.glassfish.ha.store.api.Storeable;
import org.glassfish.shoal.ha.cache.store.backing.ReplicatedBackingStoreFactory;

/**
 * @author Mahesh Kannan
 */
public class StoreableBackingStoreShell {

    BackingStore<String, MyStoreable> ds;

    ConcurrentHashMap<String, MyStoreable> cache = new ConcurrentHashMap<String, MyStoreable>();

    int counter = 0;

    public static void main(String[] args) throws Exception {

        BackingStoreConfiguration<String, MyStoreable> conf = new BackingStoreConfiguration<String, MyStoreable>();
        conf.setStoreName(args[0]).setInstanceName(args[1]).setClusterName(args[2]).setKeyClazz(String.class).setValueClazz(MyStoreable.class)
                .setClassLoader(ClassLoader.getSystemClassLoader());
        Map<String, Object> map = conf.getVendorSpecificSettings();
        map.put("start.gms", true);
        // map.put("local.caching", true);
        map.put("class.loader", ClassLoader.getSystemClassLoader());
        map.put("async.replication", true);
        BackingStore<String, MyStoreable> ds = (new ReplicatedBackingStoreFactory()).createBackingStore(conf);

        StoreableBackingStoreShell main = new StoreableBackingStoreShell();
        main.runShell(ds);
    }

    private void runShell(BackingStore<String, MyStoreable> ds) {
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
            } catch (BackingStoreException bsEx) {
                bsEx.printStackTrace();

            }
        } while (!"quit".equalsIgnoreCase(line));
    }

    private void prompt() {
        System.out.print("" + counter + ">");
        System.out.flush();
    }

    private void execute(String command, String[] params) throws BackingStoreException {

        if ("put".equalsIgnoreCase(command)) {
            String key = params[0];
            /*
             * MyStoreable st = cache.get(key); if (st == null) { st = new MyStoreable(); cache.put(key, st); } if (params.length >
             * 1) { st.setStr1(params[1]); } if (params.length > 2) { st.setStr2(params[2]); } st.touch();
             *
             * System.out.println("PUT " + st); ds.save(key, st, true);
             */
            for (int i = 0; i < 8; i++) {
                String key1 = params[0] + ":" + i;
                MyStoreable st1 = cache.get(key1);
                if (st1 == null) {
                    st1 = new MyStoreable();
                    cache.put(key1, st1);
                }

                if (params.length > 1) {
                    st1.setStr1(params[1] + ":" + i);
                }
                if (params.length > 2) {
                    st1.setStr2(params[2] + ":" + i);
                }
                st1.touch();
                String replica = ds.save(key1, st1, true);
                System.out.println("PUT key = " + key1 + " : " + st1 + " ==> " + replica);
            }

        } else if ("get".equalsIgnoreCase(command)) {
            MyStoreable st = ds.load(params[0], params.length > 1 ? params[1] : null);
            System.out.println("get(" + params[0] + ") => " + st);
            if (st != null) {
                cache.put(params[0], st);
            }
        } else if ("touch".equalsIgnoreCase(command)) {
            MyStoreable st = ds.load(params[0], params.length > 1 ? params[1] : null);
            st.touch();
            String result = null;
            ds.updateTimestamp(params[0], st._storeable_getLastAccessTime());
            System.out.println("Result of touch: " + result);
        } else if ("remove".equalsIgnoreCase(command)) {
            ds.remove(params[0]);
        } /*
           * else if ("list-backing-store-config".equalsIgnoreCase(command)) { ReplicationFramework framework = ds.getFramework();
           * ListBackingStoreConfigurationCommand cmd = new ListBackingStoreConfigurationCommand(); try { framework.execute(cmd);
           * ArrayList<String> confs = cmd.getResult(6, TimeUnit.SECONDS); for (String str : confs) { System.out.println(str); } }
           * catch (DataStoreException dse) { System.err.println(dse); } } else if ("list-entries".equalsIgnoreCase(command)) {
           * ReplicationFramework framework = ds.getFramework(); ListReplicaStoreEntriesCommand cmd = new
           * ListReplicaStoreEntriesCommand(params[0]); try { framework.execute(cmd); ArrayList<String> confs = cmd.getResult(6,
           * TimeUnit.SECONDS); for (String str : confs) { System.out.println(str); } } catch (DataStoreException dse) {
           * System.err.println(dse); } }
           */
    }

    public static class MyStoreable implements Storeable {

        /**
         *
         */
        private static final long serialVersionUID = 6878799840494060076L;

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
                dirty = new boolean[2];
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
