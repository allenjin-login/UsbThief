package com.superredrock.usbthief.worker;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class CategoryModeTest {

    private final Path photo = Path.of("/tmp/usb/DCIM/photo.JPG");

    @Test
    void offCreatesNoCategoryFolder() {
        assertNull(CategoryMode.OFF.categoryDirectory(photo),
                "OFF must not add a folder - it keeps the pre-existing layout");
    }

    @Test
    void byTypeUsesTheClassificationTable() {
        assertEquals(FileCategory.IMAGE.directoryName(), CategoryMode.BY_TYPE.categoryDirectory(photo));
        assertEquals(FileCategory.DOCUMENT.directoryName(),
                CategoryMode.BY_TYPE.categoryDirectory(Path.of("/tmp/usb/doc.PDF")));
        assertEquals(FileCategory.OTHER.directoryName(),
                CategoryMode.BY_TYPE.categoryDirectory(Path.of("/tmp/usb/no-extension")));
    }

    @Test
    void byDateUsesTheCopyDate() {
        String directory = CategoryMode.BY_DATE.categoryDirectory(photo);
        assertTrue(directory.matches("\\d{4}-\\d{2}-\\d{2}"),
                "date folder should be yyyy-MM-dd, was " + directory);
        assertEquals(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now()), directory);
    }

    @Test
    void safeValueOfParsesEveryMode() {
        for (CategoryMode mode : CategoryMode.values()) {
            assertEquals(mode, CategoryMode.safeValueOf(mode.name()));
        }
    }

    @Test
    void safeValueOfFallsBackToOff() {
        assertEquals(CategoryMode.OFF, CategoryMode.safeValueOf("BY_FOLDER"));
        assertEquals(CategoryMode.OFF, CategoryMode.safeValueOf(null));
        assertEquals(CategoryMode.OFF, CategoryMode.safeValueOf(""));
    }

    @Test
    void offIsTheDefaultOfTheConfigEntry() {
        assertEquals(CategoryMode.OFF.name(),
                com.superredrock.usbthief.core.config.configs.CategoryConfig.CATEGORY_MODE.defaultValue());
    }
}
