/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.shoal.gms.tools;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Handles i18n tasks for tools.
 */
public class StringManager {

    private static final StringManager instance = new StringManager();

    private static final ResourceBundle bundle = ResourceBundle.getBundle("org.glassfish.shoal.gms.tools.LocalStrings", Locale.getDefault());

    private StringManager() {
    }

    static StringManager getInstance() {
        return instance;
    }

    /*
     * This is a utility method so that the rest of the code doesn't have to deal with resource bundles and formatting
     * strings.
     */
    String get(String key, Object... params) {
        final String message = bundle.getString(key);
        if (params == null || params.length == 0) {
            return message;
        }
        return MessageFormat.format(message, params);
    }
}
