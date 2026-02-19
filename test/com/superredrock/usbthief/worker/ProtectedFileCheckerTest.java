package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedFileCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void isNewFile_WhenFileIsYoungerThanMaxAge_ReturnsTrue() throws IOException {
        // Create a file with current timestamp
        Path file = tempDir.resolve("newFile.txt");
        Files.writeString(file, "test");

        // File should be considered new (0 hours old < 1 hour)
        assertTrue(ProtectedFileChecker.isNewFile(file, 1));
    }

    @Test
    void isNewFile_WhenFileIsOlderThanMaxAge_ReturnsFalse() throws IOException {
        // Create a file and set it to 2 hours ago
        Path file = tempDir.resolve("oldFile.txt");
        Files.writeString(file, "test");

        Files.setAttribute(file, "lastModifiedTime",
                FileTime.from(Instant.now().minusSeconds(7200)));

        // File should NOT be considered new (2 hours old > 1 hour)
        assertFalse(ProtectedFileChecker.isNewFile(file, 1));
    }

    @Test
    void isNewFile_WhenExactlyAtBoundary_ReturnsFalse() throws IOException {
        // Create a file exactly 1 hour ago
        Path file = tempDir.resolve("boundaryFile.txt");
        Files.writeString(file, "test");

        Files.setAttribute(file, "lastModifiedTime",
                FileTime.from(Instant.now().minusSeconds(3600)));

        // File at boundary should NOT be considered new (not < 1 hour)
        assertFalse(ProtectedFileChecker.isNewFile(file, 1));
    }

    @Test
    void isNewFile_WhenCannotReadModificationTime_ReturnsTrue() {
        // Create a path that doesn't exist
        Path nonExistent = tempDir.resolve("doesNotExist.txt");

        // Should return true (safe default = protected)
        assertTrue(ProtectedFileChecker.isNewFile(nonExistent, 1));
    }

    @Test
    void isLocked_WhenFileIsNotLocked_ReturnsFalse() throws IOException {
        // Create a regular file
        Path file = tempDir.resolve("unlocked.txt");
        Files.writeString(file, "test");

        // File should NOT be considered locked
        assertFalse(ProtectedFileChecker.isLocked(file));
    }

    @Test
    void isLocked_WhenFileIsLocked_ReturnsTrue() throws IOException {
        // Create a file
        Path file = tempDir.resolve("locked.txt");
        Files.writeString(file, "test");

        // Lock the file manually
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {

            assertNotNull(lock, "Failed to acquire lock for test");

            // File should be considered locked
            assertTrue(ProtectedFileChecker.isLocked(file));
        }
    }

    @Test
    void isLocked_WhenCannotAccessFile_ReturnsTrue() {
        // Create a path that doesn't exist
        Path nonExistent = tempDir.resolve("doesNotExist.txt");

        // Should return true (safe default = protected)
        assertTrue(ProtectedFileChecker.isLocked(nonExistent));
    }

    @Test
    void isSystemFile_WhenNormalFile_ReturnsFalse() throws IOException {
        // Create a normal file in temp directory
        Path file = tempDir.resolve("normal.txt");
        Files.writeString(file, "test");

        // Should NOT be considered a system file
        assertFalse(ProtectedFileChecker.isSystemFile(file));
    }

    @Test
    void isSystemFile_WhenHiddenFile_ReturnsTrue() throws IOException {
        // Create a file and mark it as hidden
        Path hiddenFile = tempDir.resolve("hidden.txt");
        Files.writeString(hiddenFile, "test");

        // Set hidden attribute (works on Windows and Unix where supported)
        try {
            Files.setAttribute(hiddenFile, "dos:hidden", true);
        } catch (UnsupportedOperationException e) {
            // If dos:hidden not supported, skip this test
            return;
        }

        // Should be considered a system file (hidden)
        assertTrue(ProtectedFileChecker.isSystemFile(hiddenFile));
    }

    @Test
    void isSystemFile_WhenInWindowsSystemDirectory_ReturnsTrue() {
        // Test Windows path detection
        Path windowsPath = Paths.get("C:\\Windows\\System32\\test.dll");
        assertTrue(ProtectedFileChecker.isSystemFile(windowsPath));

        Path programFilesPath = Paths.get("C:\\Program Files\\App\\test.exe");
        assertTrue(ProtectedFileChecker.isSystemFile(programFilesPath));
    }

    @Test
    void isSystemFile_WhenInProgramFilesX86_ReturnsTrue() {
        Path programFilesX86 = Paths.get("C:\\Program Files (x86)\\App\\test.exe");
        assertTrue(ProtectedFileChecker.isSystemFile(programFilesX86));
    }

    @Test
    void isSystemFile_WhenInProgramData_ReturnsTrue() {
        Path programData = Paths.get("C:\\ProgramData\\App\\config.ini");
        assertTrue(ProtectedFileChecker.isSystemFile(programData));
    }

    @Test
    void isSystemFile_WhenInSystem32_ReturnsTrue() {
        Path system32 = Paths.get("C:\\Windows\\System32\\drivers\\test.sys");
        assertTrue(ProtectedFileChecker.isSystemFile(system32));
    }

    @Test
    void isSystemFile_WhenPathCaseInsensitive_ReturnsTrue() {
        // Test case-insensitive path matching (Windows)
        Path upperCasePath = Paths.get("C:\\WINDOWS\\SYSTEM32\\test.dll");
        assertTrue(ProtectedFileChecker.isSystemFile(upperCasePath));

        Path mixedCasePath = Paths.get("C:\\Program Files\\APP\\test.exe");
        assertTrue(ProtectedFileChecker.isSystemFile(mixedCasePath));
    }

    @Test
    void isSystemFile_WhenCannotDetermineHiddenStatus_ReturnsTrue() {
        // Create a path that doesn't exist
        Path nonExistent = tempDir.resolve("doesNotExist.txt");

        // Should return true (safe default = protected)
        assertTrue(ProtectedFileChecker.isSystemFile(nonExistent));
    }

    @Test
    void isProtected_WhenNewFile_ReturnsTrue() throws IOException {
        // Create a new file
        Path newFile = tempDir.resolve("new.txt");
        Files.writeString(newFile, "test");

        // New file should be protected
        assertTrue(ProtectedFileChecker.isProtected(newFile));
    }

    @Test
    void isProtected_WhenOldNormalFile_ReturnsFalse() throws IOException {
        // Create an old file
        Path oldFile = tempDir.resolve("old.txt");
        Files.writeString(oldFile, "test");

        // Set modification time to 2 hours ago
        Files.setAttribute(oldFile, "lastModifiedTime",
                FileTime.from(Instant.now().minusSeconds(7200)));

        // Old, normal, unlocked file should NOT be protected
        assertFalse(ProtectedFileChecker.isProtected(oldFile));
    }

    @Test
    void isProtected_WhenLockedFile_ReturnsTrue() throws IOException {
        // Create a locked file
        Path lockedFile = tempDir.resolve("locked.txt");
        Files.writeString(lockedFile, "test");

        // Set modification time to 2 hours ago (not new)
        Files.setAttribute(lockedFile, "lastModifiedTime",
                FileTime.from(Instant.now().minusSeconds(7200)));

        // Lock the file manually
        try (FileChannel channel = FileChannel.open(lockedFile,
                StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {

            assertNotNull(lock, "Failed to acquire lock for test");

            // Locked file should be protected
            assertTrue(ProtectedFileChecker.isProtected(lockedFile));
        }
    }

    @Test
    void isProtected_WhenSystemFile_ReturnsTrue() {
        // Windows system path should be protected
        Path systemFile = Paths.get("C:\\Windows\\System32\\test.dll");

        // System file should be protected
        assertTrue(ProtectedFileChecker.isProtected(systemFile));
    }

    @Test
    void isProtected_WhenAnyCheckReturnsTrue_ReturnsTrue() throws IOException {
        // Test OR logic: file is new OR locked OR system → protected

        // Case 1: New file
        Path newFile = tempDir.resolve("new.txt");
        Files.writeString(newFile, "test");
        assertTrue(ProtectedFileChecker.isProtected(newFile));

        // Case 2: Hidden file (system) - mark as hidden
        Path hiddenFile = tempDir.resolve("hidden.txt");
        Files.writeString(hiddenFile, "test");
        Files.setAttribute(hiddenFile, "lastModifiedTime",
                FileTime.from(Instant.now().minusSeconds(7200)));
        try {
            Files.setAttribute(hiddenFile, "dos:hidden", true);
            assertTrue(ProtectedFileChecker.isProtected(hiddenFile));
        } catch (UnsupportedOperationException e) {
            // If dos:hidden not supported, skip this case
        }
    }
}
