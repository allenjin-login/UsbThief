package com.superredrock.usbthief.core.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FilterPipeline.
 */
class FilterPipelineTest {

    private FilterPipeline pipeline;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pipeline = new FilterPipeline();
    }

    @Test
    void testEmptyPipeline() throws IOException {
        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        assertTrue(pipeline.test(file, attrs), "Empty pipeline should pass all files");
    }

    @Test
    void testSingleFilter_pass() throws IOException {
        pipeline.addFilter((path, attrs) -> true);

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        assertTrue(pipeline.test(file, attrs), "Should pass when single filter passes");
    }

    @Test
    void testSingleFilter_block() throws IOException {
        pipeline.addFilter((path, attrs) -> false);

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        assertFalse(pipeline.test(file, attrs), "Should block when single filter blocks");
    }

    @Test
    void testMultipleFilters_allPass() throws IOException {
        pipeline.addFilter((path, attrs) -> true);
        pipeline.addFilter((path, attrs) -> true);
        pipeline.addFilter((path, attrs) -> true);

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        assertTrue(pipeline.test(file, attrs), "Should pass when all filters pass");
    }

    @Test
    void testMultipleFilters_oneBlocks() throws IOException {
        pipeline.addFilter((path, attrs) -> true);
        pipeline.addFilter((path, attrs) -> false);
        pipeline.addFilter((path, attrs) -> true);

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        assertFalse(pipeline.test(file, attrs), "Should block when any filter blocks");
    }

    @Test
    void testMultipleFilters_allBlock() throws IOException {
        pipeline.addFilter((path, attrs) -> false);
        pipeline.addFilter((path, attrs) -> false);

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        assertFalse(pipeline.test(file, attrs), "Should block when all filters block");
    }

    @Test
    void testAddFilter_nullFilter() {
        assertThrows(IllegalArgumentException.class, () -> {
            pipeline.addFilter(null);
        }, "Should reject null filter");
    }

    @Test
    void testClearFilters() throws IOException {
        pipeline.addFilter((path, attrs) -> false);
        pipeline.clearFilters();

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        assertTrue(pipeline.test(file, attrs), "Empty pipeline after clear should pass all files");
    }

    @Test
    void testGetFilterCount() {
        assertEquals(0, pipeline.getFilterCount());

        pipeline.addFilter((path, attrs) -> true);
        assertEquals(1, pipeline.getFilterCount());

        pipeline.addFilter((path, attrs) -> true);
        assertEquals(2, pipeline.getFilterCount());
    }

    @Test
    void testShortCircuitEvaluation() throws IOException {
        boolean[] secondFilterCalled = {false};

        pipeline.addFilter((path, attrs) -> false);
        pipeline.addFilter((path, attrs) -> {
            secondFilterCalled[0] = true;
            return true;
        });

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        pipeline.test(file, attrs);

        assertFalse(secondFilterCalled[0], "Second filter should not be called if first blocks");
    }

    @Test
    void testRemoveFilter() throws IOException {
        FileFilter filter1 = (path, attrs) -> true;
        FileFilter filter2 = (path, attrs) -> false;

        pipeline.addFilter(filter1);
        pipeline.addFilter(filter2);

        assertEquals(2, pipeline.getFilterCount());

        pipeline.removeFilter(filter2);

        assertEquals(1, pipeline.getFilterCount());

        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        assertTrue(pipeline.test(file, attrs), "Should pass after removing blocking filter");
    }

    @Test
    void testAndMethod() throws IOException {
        pipeline.addFilter((path, attrs) -> true);
        
        FileFilter combined = pipeline.and((path, attrs) -> true);
        
        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertTrue(combined.test(file, attrs), "Combined filter should pass");
    }

    @Test
    void testAndMethod_withBlockingFilter() throws IOException {
        pipeline.addFilter((path, attrs) -> true);
        
        FileFilter combined = pipeline.and((path, attrs) -> false);
        
        Path file = createFile("test.txt");
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        
        assertFalse(combined.test(file, attrs), "Combined filter should block");
    }

    // ==================== Helper Methods ====================

    private Path createFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "content");
        return file;
    }
}
