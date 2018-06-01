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

import org.glassfish.ha.store.api.BackingStore;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStoreException;
import org.shoal.adapter.store.ReplicatedBackingStoreFactory;
import org.shoal.ha.cache.api.*;
import org.shoal.ha.mapper.DefaultKeyMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mahesh Kannan
 */
public class DataStoreShell {

    BackingStore<String, Serializable> ds;

    int counter = 0;
  private final Logger csc_log = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_SAVE_COMMAND);
  private final Logger clrc_log = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_LOAD_REQUEST_COMMAND);
  private final Logger clresp_log = Logger.getLogger(ShoalCacheLoggerConstants.CACHE_LOAD_RESPONSE_COMMAND);

  public static void main(String[] args)
        throws Exception {
        BackingStoreConfiguration<String, Serializable> conf = new BackingStoreConfiguration<String, Serializable>();
        conf.setStoreName(args[0])
                .setInstanceName(args[1])
                .setClusterName(args[2])
                .setKeyClazz(String.class)
                .setValueClazz(Serializable.class);
        Map<String, Object> map = conf.getVendorSpecificSettings();
        map.put("start.gms", true);
        map.put("max.idle.timeout.in.seconds", 90L);
        //map.put("local.caching", true);
        map.put("class.loader", ClassLoader.getSystemClassLoader());
        BackingStore<String, Serializable> ds =
                (new ReplicatedBackingStoreFactory()).createBackingStore(conf);

        DataStoreShell main = new DataStoreShell();
        main.runShell(ds);
    }

    private void runShell(BackingStore<String, Serializable> ds) {
        csc_log.setLevel(Level.ALL);
        clrc_log.setLevel(Level.ALL);
        clresp_log.setLevel(Level.ALL);
        
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
            } catch (IOException  ioEx) {
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

    private void execute(String command, String[] params)
        throws BackingStoreException {

        if ("put".equalsIgnoreCase(command)) {
            String hint = ds.save(params[0], params[1], true);
            System.out.println("Saved; hint: " + hint);
        } else if ("get".equalsIgnoreCase(command)) {
            String hint = params.length > 1 ? params[1] : null;
            System.out.println("get(" + params[0] + ") => " + ds.load(params[0], hint));
        } else if ("remove".equalsIgnoreCase(command)) {
            ds.remove(params[0]);
        } else if ("size".equalsIgnoreCase(command)) {
            int size = ds.size();
            System.out.println("Size: " + size);
        } else if ("expireIdle".equalsIgnoreCase(command)) {
            int count = ds.removeExpired(15);
            System.out.println("** Idle Entries Removed: " + count);
        }
    }
}
