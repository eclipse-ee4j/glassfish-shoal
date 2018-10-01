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

package com.sun.enterprise.mgmt.transport.grizzly.grizzly1_9;

import java.io.IOException;

import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.filter.ParserProtocolFilter;

/**
 * {@link ParserProtocolFilter}, which allows just OP_READ.
 *
 * @author Alexey Stashok
 */
public abstract class ParserFilter extends ParserProtocolFilter {

	@Override
	public boolean execute(final Context context) throws IOException {
		if (context.getCurrentOpType() == Context.OpType.OP_WRITE) {
			return false;
		}

		return super.execute(context);
	}

	@Override
	public boolean postExecute(final Context context) throws IOException {
		if (context.getCurrentOpType() == Context.OpType.OP_WRITE) {
			return false;
		}

		final GrizzlyMessageProtocolParser parser = (GrizzlyMessageProtocolParser) context.getAttribute(ProtocolParser.PARSER);

		if (parser == null) {
			return true;
		}

		if (parser.isError()) {
			parser.releaseBuffer();
			context.setKeyRegistrationState(Context.KeyRegistrationState.CANCEL);
			return false;
		}

		return super.postExecute(context);
	}

}
