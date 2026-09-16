// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Decides whether a directory inside a container lives on a mounted volume,
 * from the contents of `/proc/self/mountinfo`.
 *
 * Kept free of I/O so the parsing rules can be tested without a container.
 */
object UploadsMount {

    /**
     * True when [uploadsPath] is covered by a mount other than the container's
     * own root filesystem — i.e. data written there survives `docker compose up`
     * recreating the container.
     *
     * The *deepest* mount point containing the path decides, because a deeper
     * mount shadows a shallower one, and ties go to the later line for the same
     * reason. That mount counts as persistent only if its device differs from
     * the device behind `/`: a mount whose device is the image's own overlay is
     * as ephemeral as no mount at all, wherever it is attached.
     */
    fun isPersistent(uploadsPath: String, mountinfoLines: List<String>): Boolean {
        val target = components(uploadsPath)
        val mounts = mountinfoLines.mapNotNull(::parseMount)
        val rootDevice = mounts.lastOrNull { it.point.isEmpty() }?.device
        val covering = mounts.filter { target.isUnder(it.point) }
        // maxByOrNull keeps the first maximum; the last one is the effective mount.
        val deepest = covering.lastOrNull { m -> covering.none { it.point.size > m.point.size } }
            ?: return false
        return deepest.point.isNotEmpty() && deepest.device != rootDevice
    }

    private class Mount(val device: String, val point: List<String>)

    /** mountinfo fields: id parentId major:minor root mountPoint options... */
    private fun parseMount(line: String): Mount? {
        val fields = line.split(' ')
        if (fields.size < 5) return null
        return Mount(fields[2], components(unescape(fields[4])))
    }

    /** Path split into non-empty components, so containment is not a string prefix test. */
    private fun components(path: String): List<String> =
        path.split('/').filter { it.isNotEmpty() }

    /** True when this path is the given directory or sits below it. */
    private fun List<String>.isUnder(dir: List<String>): Boolean =
        size >= dir.size && subList(0, dir.size) == dir

    /**
     * mountinfo octal-escapes the four characters that would otherwise break its
     * space-separated fields.
     */
    private fun unescape(field: String): String =
        field.replace("\\040", " ")
            .replace("\\011", "\t")
            .replace("\\012", "\n")
            .replace("\\134", "\\")
}

/**
 * Warns once at startup when bottle photos are being written somewhere that a
 * container recreate will erase — a volume mounted at the wrong path, or no
 * volume at all. Both have happened to this stack: the live Dockge stack once
 * ran with no uploads volume, and the `/app/uploads` → `/data/uploads` rename
 * gives anyone updating only one of `UPLOADS_DIR` and the mount target the same
 * silent failure.
 *
 * Warn, never fail: a deliberate volume-less trial run must still boot.
 */
@Component
@Profile("!dev & !test")
class UploadsMountCheck(
    @Value("\${app.uploads.dir}") private val uploadsDirRaw: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun warnIfNotPersistent() {
        val mountinfo = Paths.get("/proc/self/mountinfo")
        if (!Files.isReadable(mountinfo)) {
            // Not Linux, or a kernel without /proc: nothing to check against.
            log.debug("Skipping uploads mount check: {} is not readable", mountinfo)
            return
        }
        val uploadsDir: Path = Paths.get(uploadsDirRaw).toAbsolutePath().normalize()
        val lines = runCatching { Files.readAllLines(mountinfo) }.getOrElse {
            log.debug("Skipping uploads mount check: could not read {}", mountinfo, it)
            return
        }
        if (UploadsMount.isPersistent(uploadsDir.toString().replace('\\', '/'), lines)) return

        log.warn(
            "Bottle photos are being stored at {}, which is NOT on a mounted volume — " +
                "every uploaded photo will be lost when this container is recreated, and " +
                "existing photos will 404. UPLOADS_DIR and the container side of the uploads " +
                "volume must name the same path (both are /data/uploads in the shipped " +
                "compose files; this path was /app/uploads before). See DEPLOY.md.",
            uploadsDir
        )
    }
}
