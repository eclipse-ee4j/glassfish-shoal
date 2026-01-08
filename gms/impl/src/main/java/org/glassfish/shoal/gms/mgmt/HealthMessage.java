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

package org.glassfish.shoal.gms.mgmt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.glassfish.shoal.gms.base.PeerID;
import org.glassfish.shoal.gms.base.SystemAdvertisement;
import org.glassfish.shoal.gms.base.Utility;
import org.glassfish.shoal.gms.logging.GMSLogDomain;

/**
 * This class contains health states of members
 *
 * {@link org.glassfish.shoal.gms.mgmt.HealthMonitor} uses this messages to check the member's health.
 */
public class HealthMessage implements Serializable {

    static final long serialVersionUID = -5452866103975155397L;

    private static final Logger LOG = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    private List<Entry> entries = new ArrayList<Entry>();
    private PeerID srcID;

    /**
     * Default Constructor
     */
    public HealthMessage() {
    }

    /**
     * gets the entries list
     *
     * @return List The List containing Entries
     */
    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * gets the src id
     *
     * @return Peerid The sender's peer id
     */
    public PeerID getSrcID() {
        return srcID;
    }

    public void add(final Entry entry) {
        if (!entries.contains(entry)) {
            entries.add(entry);
        }
    }

    public void remove(final Entry entry) {
        if (entries.contains(entry)) {
            entries.remove(entries.indexOf(entry));
        }
    }

    /**
     * sets the unique id
     *
     * @param id The id
     */
    public void setSrcID(final PeerID id) {
        this.srcID = (id == null ? null : id);
    }

    /**
     * returns the document string representation of this object
     *
     * @return String representation of the of this message type
     */
    public String toString() {
        return HealthMessage.class.getSimpleName() + "[" + srcID + ":" + entries + "]";
    }

    /**
     * Entries class
     */
    public static final class Entry implements Serializable, Cloneable {

        static final long serialVersionUID = 7485962183100651020L;
        /**
         * Entry ID entry id
         */
        final PeerID id;
        /**
         * Entry adv SystemAdvertisement
         */
        final SystemAdvertisement adv;
        /**
         * Entry state
         */
        String state;

        /**
         * Entry timestamp
         */
        long timestamp;

        /**
         * * Entry sequence ID
         */
        final long seqID;
        transient long srcStartTime = 0;

        /**
         * Creates a Entry with id and state
         *
         * @param adv SystemAdvertisement
         * @param state state value
         * @param seqID health message sequence ID
         */
        public Entry(final SystemAdvertisement adv, final String state, long seqID) {
            this.state = state;
            this.adv = adv;
            this.id = adv.getID();
            this.timestamp = System.currentTimeMillis();
            this.seqID = seqID;
        }

        // copy ctor
        public Entry(final Entry entry) {
            this(entry.adv, entry.state, entry.seqID);
        }

        public long getSeqID() {
            return seqID;
        }

        // todo: change calling methods to check for NO_SUCH_TIME instead of -1
        public long getSrcStartTime() {
            srcStartTime = Utility.getStartTime(adv);
            return (srcStartTime == Utility.NO_SUCH_TIME ? -1 : srcStartTime);
        }

        /**
         * Since MasterNode reports on other peers that they are DEAD or INDOUBT, be sure not to compare sequence ids between a
         * peer and a MasterNode health message report on that peer.
         *
         * @param other the entry of other peer
         * @return true if this HM.entry and other are from same member.
         */
        public boolean isFromSameMember(HealthMessage.Entry other) {
            return (other != null && id.equals(other.id));
        }

        /**
         * Detect when one hm is from a failed member and the new hm is from the restart of that member.
         *
         * @param other the entry of other peer
         * @return true if same instantiation of member sent this health message.
         */
        public boolean isFromSameMemberStartup(HealthMessage.Entry other) {
            return (other != null && id.equals(other.id) && getSrcStartTime() == other.getSrcStartTime());
        }

        public boolean isState(int theState) {
            return state.equals(HealthMonitor.states[theState]);
        }

        /**
         * {@inheritDoc}
         */
        public boolean equals(final Object obj) {
            return obj instanceof Entry && this == obj || obj != null && id.equals(((HealthMessage.Entry) obj).id);
        }

        /**
         * {@inheritDoc}
         */
        public int hashCode() {
            return id.hashCode() * 45191;
        }

        /**
         * {@inheritDoc}
         */
        public String toString() {
            return "HealthMessage.Entry: Id = " + id.toString() + "; State = " + state + "; LastTimeStamp = " + timestamp + "; Sequence ID = " + seqID;
        }

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }
}
