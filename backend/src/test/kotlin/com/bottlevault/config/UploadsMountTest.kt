// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UploadsMountTest {

    // Real /proc/self/mountinfo lines, trimmed to the fields the parser reads.
    // Field 5 (1-indexed) is the mount point; earlier fields are ids and the
    // major:minor device, later ones the mount options and filesystem type.
    private val rootOnly = listOf(
        "1234 1233 0:64 / / rw,relatime - overlay overlay rw,lowerdir=/x,upperdir=/y",
        "1235 1234 0:65 / /proc rw,nosuid - proc proc rw",
        "1236 1234 0:66 / /sys ro,nosuid - sysfs sysfs ro"
    )

    private fun withMount(mountPoint: String): List<String> =
        rootOnly + "1300 1234 259:2 /host/path $mountPoint rw,relatime - ext4 /dev/nvme0n1p2 rw"

    @Test
    fun `a path backed only by the container filesystem is not persistent`() {
        assertFalse(UploadsMount.isPersistent("/data/uploads", rootOnly))
        assertFalse(UploadsMount.isPersistent("/app/uploads", rootOnly))
    }

    @Test
    fun `a path that is itself a mount point is persistent`() {
        assertTrue(UploadsMount.isPersistent("/data/uploads", withMount("/data/uploads")))
    }

    @Test
    fun `a path under a mounted parent is persistent`() {
        // A self-hoster who mounts one volume at /data still gets durable uploads.
        assertTrue(UploadsMount.isPersistent("/data/uploads", withMount("/data")))
        assertTrue(UploadsMount.isPersistent("/data/uploads/bottles", withMount("/data")))
    }

    @Test
    fun `the rename mismatch is caught`() {
        // The failure this check exists for: the volume still mounted at the old
        // path while the app is configured for the new one.
        assertFalse(UploadsMount.isPersistent("/data/uploads", withMount("/app/uploads")))
    }

    @Test
    fun `containment compares path components, not string prefixes`() {
        assertFalse(UploadsMount.isPersistent("/data/uploads2", withMount("/data/uploads")))
        assertFalse(UploadsMount.isPersistent("/dataset/uploads", withMount("/data")))
    }

    @Test
    fun `the deepest matching mount decides`() {
        // /data is a volume but /data/uploads is explicitly remounted from the
        // container filesystem: the deeper mount wins, and it is on the same
        // device as /, so the answer is false.
        val lines = withMount("/data") +
            "1301 1300 0:64 /ephemeral /data/uploads rw,relatime - overlay overlay rw"
        assertFalse(UploadsMount.isPersistent("/data/uploads", lines))

        // And the reverse: an ephemeral /data with a real volume deeper in.
        val reversed = rootOnly +
            "1302 1234 0:64 /ephemeral /data rw,relatime - overlay overlay rw" +
            "1303 1302 259:2 /host/path /data/uploads rw,relatime - ext4 /dev/nvme0n1p2 rw"
        assertTrue(UploadsMount.isPersistent("/data/uploads", reversed))
    }

    @Test
    fun `octal escapes in the mount point are decoded`() {
        // mountinfo escapes space, tab, newline and backslash in the mount-point
        // field. Undecoded, this line would not match the real directory.
        val lines = rootOnly +
            "1304 1234 259:2 /host /mnt/my\\040volume/uploads rw - ext4 /dev/sda1 rw"
        assertTrue(UploadsMount.isPersistent("/mnt/my volume/uploads", lines))
        assertFalse(UploadsMount.isPersistent("/mnt/my\\040volume/uploads", lines))
    }

    @Test
    fun `trailing slashes and duplicate separators do not change the answer`() {
        assertTrue(UploadsMount.isPersistent("/data/uploads/", withMount("/data/uploads")))
        assertTrue(UploadsMount.isPersistent("//data//uploads", withMount("/data/uploads")))
    }

    @Test
    fun `malformed lines are ignored rather than throwing`() {
        val lines = listOf("", "   ", "1 2 3", "garbage") + withMount("/data/uploads")
        assertTrue(UploadsMount.isPersistent("/data/uploads", lines))
    }

    @Test
    fun `an empty mountinfo is not persistent`() {
        // Nothing to prove persistence with: warn rather than stay silent.
        assertFalse(UploadsMount.isPersistent("/data/uploads", emptyList()))
    }
}
