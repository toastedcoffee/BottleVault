// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.security

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Optional daily-rolling log files under LOG_DIR. Done in code rather than
 * logback-spring.xml because the files are optional (LOG_DIR may be unset) and
 * Logback's XML conditionals need Janino.
 *
 * Size budget: the logs dataset has a 2 GiB quota in the reference deployment.
 * security.log (500 MB) + app.log (1 GB) stays under it; do not raise either cap
 * without raising the quota.
 */
internal object RollingLogFile {

    fun attach(
        loggerName: String,
        appenderName: String,
        dir: String,
        baseName: String,
        pattern: String,
        maxHistory: Int,
        totalSizeCap: String,
    ) {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger(loggerName)
        if (logger.getAppender(appenderName) != null) return
        if (!Files.isWritable(Path.of(dir))) {
            LoggerFactory.getLogger(RollingLogFile::class.java).warn(
                "Log directory {} is not writable, so {}.log is not kept and that output goes to the console only. " +
                    "The API runs as uid 568: chown 568:568 the directory on the host.",
                dir,
                baseName,
            )
            return
        }
        val appender = RollingFileAppender<ILoggingEvent>()
        appender.context = context
        appender.name = appenderName
        appender.file = "$dir/$baseName.log"
        val policy = TimeBasedRollingPolicy<ILoggingEvent>()
        policy.context = context
        policy.fileNamePattern = "$dir/$baseName.%d{yyyy-MM-dd}.log"
        policy.maxHistory = maxHistory
        policy.setTotalSizeCap(FileSize.valueOf(totalSizeCap))
        policy.setParent(appender)
        policy.start()
        val encoder = PatternLayoutEncoder()
        encoder.context = context
        encoder.pattern = pattern
        encoder.start()
        appender.rollingPolicy = policy
        appender.encoder = encoder
        appender.start()
        logger.addAppender(appender)
    }

    /** Detaches and closes the appender, releasing its file. For tests. */
    fun detach(loggerName: String, appenderName: String) {
        val logger: Logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(loggerName)
        logger.getAppender(appenderName)?.let {
            logger.detachAppender(it)
            it.stop()
        }
    }
}

/** <dir>/security.log on the SECURITY logger: one JSON event per line, kept 30 days, capped at 500 MB. */
internal object SecurityLogFile {
    private const val APPENDER = "SECURITY_FILE"

    fun attach(dir: String) = RollingLogFile.attach(
        SecurityEvents.LOGGER_NAME, APPENDER, dir, "security", "%msg%n", maxHistory = 30, totalSizeCap = "500MB",
    )

    fun detach() = RollingLogFile.detach(SecurityEvents.LOGGER_NAME, APPENDER)
}

/**
 * <dir>/app.log on the root logger: the API's general log, kept 14 days and
 * capped at 1 GB, so it survives a container being recreated (Docker's own log
 * is lost with the container — exactly when an incident needs it).
 *
 * Unlike security.log, this file is not covered by the hashed-identifiers
 * invariant: it holds whatever the application logs.
 */
internal object ApplicationLogFile {
    private const val APPENDER = "APP_FILE"
    private const val PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p [%t] %logger{40} : %msg%n%throwable"

    fun attach(dir: String) = RollingLogFile.attach(
        Logger.ROOT_LOGGER_NAME, APPENDER, dir, "app", PATTERN, maxHistory = 14, totalSizeCap = "1GB",
    )

    fun detach() = RollingLogFile.detach(Logger.ROOT_LOGGER_NAME, APPENDER)
}
