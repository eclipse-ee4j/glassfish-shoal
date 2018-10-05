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

package com.sun.enterprise.ee.cms.core;

import static com.sun.enterprise.ee.cms.core.GroupManagementService.MemberType.CORE;
import static com.sun.enterprise.ee.cms.core.GroupManagementService.MemberType.WATCHDOG;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

/**
 * Encapsulates the member token and the member type in a serializable
 *
 * @author Shreedhar Ganapathy Date: Mar 16, 2005
 * @version $Revision$
 */
public class GMSMember implements Serializable {
    static final long serialVersionUID = -938961303520509595L;

    private final String memberToken;
    private final String memberType;
    private long id;
    private final String groupName;
    private final Long startTime;

    /**
     * Constructor
     */
    public GMSMember(final String memberToken, final String memberType, final String groupName, final Long startTime) {

        this.memberToken = memberToken;
        this.memberType = memberType;
        this.groupName = groupName;
        this.startTime = startTime;
    }

    /**
     * returns the member token
     *
     * @return String member token
     */
    public String getMemberToken() {
        return memberToken;
    }

    /**
     * returns the member type
     *
     * @return String member type
     */
    public String getMemberType() {
        return memberType;
    }

    public void setSnapShotId(final long id) {
        this.id = id;
    }

    public long getSnapShotId() {
        return id;
    }

    public String getGroupName() {
        return groupName;
    }

    /**
     * Returns the time the member joined the group.
     *
     * @return the time the member joined the group
     */
    public long getStartTime() {
        return startTime;
    }

    public boolean isWatchDog() {
        return WATCHDOG.toString().equalsIgnoreCase(memberType);
    }

    public boolean isCore() {
        return CORE.toString().equalsIgnoreCase(memberType);
    }

    public String toString() {
        String result = MessageFormat.format("GMSMember name: {0}  group: {1} memberType: {2} startTime: {3,date} {3,time,full}", memberToken, groupName,
                memberType, new Date(startTime));
        return result;
    }

    public GMSMember getByMemberToken(List<GMSMember> view) {
        GMSMember result = null;
        for (GMSMember current : view) {
            if (memberToken.equals(current.memberToken)) {
                result = current;
                break;
            }
        }
        return result;
    }
}
