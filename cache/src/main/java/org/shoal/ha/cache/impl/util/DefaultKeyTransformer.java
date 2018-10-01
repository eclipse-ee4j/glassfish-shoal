/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.ha.store.util.KeyTransformer;
import org.shoal.ha.cache.api.ShoalCacheLoggerConstants;

/**
 * @author Mahesh Kannan
 */
public class DefaultKeyTransformer<K> implements KeyTransformer<K> {

	private static final Logger _logger = Logger.getLogger(ShoalCacheLoggerConstants.CACHE);

	private ClassLoader loader;

	public DefaultKeyTransformer(ClassLoader loader) {
		this.loader = loader;
	}

	@Override
	public byte[] keyToByteArray(K k) {
		ByteArrayOutputStream bos = null;
		ObjectOutputStream oos = null;
		try {
			bos = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bos);
			oos.writeObject(k);
			try {
				oos.close();
			} catch (Exception ex) {
			}
			return bos.toByteArray();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
			// TODO FIXME This cannot be RuntimeException
		} finally {
			try {
				oos.close();
			} catch (Exception ex) {
				_logger.log(Level.FINEST, "Ignorable error while closing ObjectOutputStream");
			}
			try {
				bos.close();
			} catch (Exception ex) {
				_logger.log(Level.FINEST, "Ignorable error while closing ByteArrayOutputStream");
			}
		}
	}

	@Override
	public K byteArrayToKey(byte[] bytes, int index, int len) {
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes, index, len);
		ObjectInputStreamWithLoader ois = null;
		try {
			ois = new ObjectInputStreamWithLoader(bis, loader);
			return (K) ois.readObject();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		} finally {
			try {
				ois.close();
			} catch (Exception ex) {
				_logger.log(Level.FINEST, "Ignorable error while closing ObjectInputStream");
			}
			try {
				bis.close();
			} catch (Exception ex) {
				_logger.log(Level.FINEST, "Ignorable error while closing ByteArrayInputStream");
			}
		}
	}

}
