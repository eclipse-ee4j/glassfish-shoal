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

package com.sun.enterprise.ee.cms.logging;

import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.ResourceBundle;
import java.util.HashMap;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.MessageFormat;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * NiceLogFormatter conforms to the logging format defined by the
 * Log Working Group in Java Webservices Org.
 * The specified format is
 * "[#|DATETIME|LOG_LEVEL|PRODUCT_ID|LOGGER NAME|OPTIONAL KEY VALUE PAIRS|
 * MESSAGE|#]\n"
 *
 * @author Shreedhar Ganapathy
 *         Date: Jun 6, 2006
 * @version $Revision$
 */
public class NiceLogFormatter extends Formatter {

    // loggerResourceBundleTable caches references to all the ResourceBundle
    // and can be searched using the LoggerName as the key

    private HashMap<String, ResourceBundle> loggerResourceBundleTable;
    // A Dummy Container Date Object is used to format the date
    private Date date = new Date();
    private static boolean LOG_SOURCE_IN_KEY_VALUE = true;

    private static boolean RECORD_NUMBER_IN_KEY_VALUE = false;

    static {
        String logSource = System.getProperty(
                "com.sun.aas.logging.keyvalue.logsource");
        if ((logSource != null)
                && (logSource.equals("true"))) {
            LOG_SOURCE_IN_KEY_VALUE = true;
        }

        String recordCount = System.getProperty(
                "com.sun.aas.logging.keyvalue.recordnumber");
        if ((recordCount != null)
                && (recordCount.equals("true"))) {
            RECORD_NUMBER_IN_KEY_VALUE = true;
        }
    }

    private long recordNumber = 0;

    @SuppressWarnings("unchecked")
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static final String RECORD_BEGIN_MARKER = "[#|";
    private static final String RECORD_END_MARKER = "|#]" + LINE_SEPARATOR +
                                                    LINE_SEPARATOR;
    private static final char FIELD_SEPARATOR = '|';
    private static final char NVPAIR_SEPARATOR = ';';
    private static final char NV_SEPARATOR = '=';

    private static final String RFC_3339_DATE_FORMAT =
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat( RFC_3339_DATE_FORMAT );
    private static final String PRODUCT_VERSION = "Shoal";

    public NiceLogFormatter() {
        super();
        loggerResourceBundleTable = new HashMap<String, ResourceBundle>();
    }


    public String format( LogRecord record) {
        return uniformLogFormat(record);
    }

    public String formatMessage(LogRecord record) {
        return uniformLogFormat(record);
    }


    /**
     * Sun One AppServer SE/EE can override to specify their product version
     *
     * @return product ID
     */
    protected String getProductId() {
        return PRODUCT_VERSION;
    }


    /**
     * Note: This method is not synchronized, we are assuming that the
     * synchronization will happen at the Log Handler.publish( ) method.
     *
     * @param record log record
     * @return the log message
     */
    private String uniformLogFormat(LogRecord record) {

        try {

            StringBuilder recordBuffer = new StringBuilder( RECORD_BEGIN_MARKER );
            // The following operations are to format the date and time in a
            // human readable  format.
            // _REVISIT_: Use HiResolution timer to analyze the number of
            // Microseconds spent on formatting date object
            date.setTime(record.getMillis());
            recordBuffer.append( dateFormatter.format(date));
            recordBuffer.append( FIELD_SEPARATOR );

            recordBuffer.append(record.getLevel()).append( FIELD_SEPARATOR );
            recordBuffer.append(getProductId()).append( FIELD_SEPARATOR );
            recordBuffer.append(record.getLoggerName()).append( FIELD_SEPARATOR );

            recordBuffer.append("_ThreadID").append( NV_SEPARATOR );
            recordBuffer.append(record.getThreadID()).append( NVPAIR_SEPARATOR );

            recordBuffer.append("_ThreadName").append( NV_SEPARATOR );
            recordBuffer.append(Thread.currentThread().getName());
            recordBuffer.append( NVPAIR_SEPARATOR );

            // See 6316018. ClassName and MethodName information should be
            // included for FINER and FINEST log levels.
            Level level = record.getLevel();
            String className = record.getSourceClassName();
            className = className.substring(className.lastIndexOf(".") + 1, className.length());
            if ( LOG_SOURCE_IN_KEY_VALUE ||
                    (level.intValue() <= Level.FINE.intValue())) {
                recordBuffer.append("ClassName").append( NV_SEPARATOR );
                recordBuffer.append(className);
                recordBuffer.append( NVPAIR_SEPARATOR );
                recordBuffer.append("MethodName").append( NV_SEPARATOR );
                recordBuffer.append(record.getSourceMethodName());
                recordBuffer.append( NVPAIR_SEPARATOR );
            }

            if ( RECORD_NUMBER_IN_KEY_VALUE ) {
                recordBuffer.append("RecordNumber").append( NV_SEPARATOR );
                recordBuffer.append(recordNumber++).append( NVPAIR_SEPARATOR );
            }
            recordBuffer.append( FIELD_SEPARATOR );

            String logMessage = record.getMessage();
            if (logMessage == null) {
                logMessage = "The log message is null.";
            }
            if (logMessage.indexOf("{0}") >= 0) {
                // If we find {0} or {1} etc., in the message, then it's most
                // likely finer level messages for Method Entry, Exit etc.,
                logMessage = java.text.MessageFormat.format(
                        logMessage, record.getParameters());
            } else {
                ResourceBundle rb = getResourceBundle(record.getLoggerName());
                if (rb != null) {
                    try {
                        logMessage = MessageFormat.format(
                                rb.getString(logMessage),
                                record.getParameters());
                    } catch (java.util.MissingResourceException e) {
                        // If we don't find an entry, then we are covered
                        // because the logMessage is intialized already
                    }
                }
            }
            recordBuffer.append(logMessage);

            if (record.getThrown() != null) {
                recordBuffer.append( LINE_SEPARATOR );
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                recordBuffer.append(sw.toString());
            }

            recordBuffer.append( RECORD_END_MARKER );
            return recordBuffer.toString();

        } catch (Exception ex) {
            new ErrorManager().error(
                    "Error in formatting Logrecord", ex,
                    ErrorManager.FORMAT_FAILURE);
            // We've already notified the exception, the following
            // return is to keep javac happy
            return new String("");
        }
    }

    private synchronized ResourceBundle getResourceBundle(String loggerName) {
        if (loggerName == null) {
            return null;
        }
        ResourceBundle rb = loggerResourceBundleTable.get(
                loggerName);

        if (rb == null) {
            rb = LogManager.getLogManager().getLogger(loggerName).getResourceBundle();
            loggerResourceBundleTable.put(loggerName, rb);
        }
        return rb;
    }
}
