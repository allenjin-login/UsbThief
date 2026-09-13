package com.superredrock.usbthief.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the batch-2-B path overload of {@link DeviceUtils#getPath(Path, Path, Volume, String)}:
 * the category folder sits directly below {@code workPath/(storeName_serial)} and the relative
 * sub-tree is dropped, so a file lands at {@code (store)/<category>/<fileName>}.
 *
 * <p>The volume is injected, which keeps these cases pure path arithmetic: no file-store lookup
 * and no device-manager access are needed.</p>
 */
class DeviceUtilsCategoryPathTest {

    @TempDir
    Path workDir;

    private Volume volume(String name, String serial) {
        return new Volume(workDir, serial) {
            @Override
            public String getVolumeName() {
                return name;
            }
        };
    }

    @Test
    void categoryFolderIsInsertedBelowTheVolumeFolder() throws IOException {
        Path target = workDir.resolve("usb").resolve("DCIM").resolve("holiday").resolve("photo.jpg");
        Path result = DeviceUtils.getPath(workDir, target, volume("KINGSTON", "ABC123"), "Images");

        assertEquals(workDir.resolve("KINGSTON_ABC123").resolve("Images").resolve("photo.jpg"), result);
        assertFalse(result.toString().contains("DCIM"),
                "the file-level layout drops the relative sub-tree: " + result);
    }

    @Test
    void nullOrEmptyCategoryKeepsTheCurrentLayout() throws IOException {
        Path target = workDir.resolve("usb").resolve("DCIM").resolve("photo.jpg");
        Path relative = target.getRoot().relativize(target);
        Path volumeDir = workDir.resolve("KINGSTON_ABC123");

        assertEquals(volumeDir.resolve(relative),
                DeviceUtils.getPath(workDir, target, volume("KINGSTON", "ABC123"), null));
        assertEquals(volumeDir.resolve(relative),
                DeviceUtils.getPath(workDir, target, volume("KINGSTON", "ABC123"), ""));
        assertEquals(volumeDir.resolve(relative),
                DeviceUtils.getPath(workDir, target, volume("KINGSTON", "ABC123")));
    }

    @Test
    void threeArgumentOverloadMatchesTheUncategorisedBehaviour() throws IOException {
        Path target = workDir.resolve("usb").resolve("notes.txt");
        assertEquals(DeviceUtils.getPath(workDir, target, volume("V", "1")),
                DeviceUtils.getPath(workDir, target, volume("V", "1"), null));
    }
}
