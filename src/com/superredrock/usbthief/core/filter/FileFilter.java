package com.superredrock.usbthief.core.filter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Functional interface for filtering files during discovery.
 *
 * <p>Extends {@link BiPredicate}&lt;Path, BasicFileAttributes&gt; for seamless
 * integration with Java's functional APIs. Inherits {@code and()}, {@code or()},
 * and {@code negate()} methods from BiPredicate.
 *
 * <p>For use with Stream API where only Path is available, use {@link #asPredicate()}.
 *
 * @see BasicFileFilter
 * @see SuffixFilter
 * @see FilterPipeline
 */
@FunctionalInterface
public interface FileFilter extends BiPredicate<Path, BasicFileAttributes> {

    /**
     * Test whether a file should be accepted for processing.
     *
     * @param path  the path to the file
     * @param attrs the file's basic attributes
     * @return {@code true} if the file should be accepted, {@code false} otherwise
     */
    @Override
    boolean test(Path path, BasicFileAttributes attrs);

    /**
     * Returns a composed filter that represents a short-circuiting logical AND
     * of this filter and another. Overrides BiPredicate's {@code and()} to
     * return FileFilter type.
     *
     * @param other the filter to combine with
     * @return a composed filter that returns {@code true} only if both filters return {@code true}
     */
    @Override
    default FileFilter and(BiPredicate<? super Path, ? super BasicFileAttributes> other) {
        return (path, attrs) -> this.test(path, attrs) && other.test(path, attrs);
    }

    /**
     * Returns a composed filter that represents a short-circuiting logical OR
     * of this filter and another. Overrides BiPredicate's {@code or()} to
     * return FileFilter type.
     *
     * @param other the filter to combine with
     * @return a composed filter that returns {@code true} if either filter returns {@code true}
     */
    @Override
    default FileFilter or(BiPredicate<? super Path, ? super BasicFileAttributes> other) {
        return (path, attrs) -> this.test(path, attrs) || other.test(path, attrs);
    }

    /**
     * Returns a filter that represents the logical negation of this filter.
     * Overrides BiPredicate's {@code negate()} to return FileFilter type.
     *
     * @return a filter that returns {@code true} when this filter returns {@code false}
     */
    @Override
    default FileFilter negate() {
        return (path, attrs) -> !this.test(path, attrs);
    }

    /**
     * Converts this filter to a {@link Predicate<Path>} for use with Stream API.
     *
     * <p>The predicate reads file attributes on each call and handles IOException
     * by returning {@code false}. This is suitable for streaming operations where
     * attributes are not preloaded.
     *
     * <p>Example usage:
     * <pre>{@code
     * Files.list(directory)
     *     .filter(pipeline.asPredicate())
     *     .forEach(this::processFile);
     * }</pre>
     *
     * @return a Predicate that wraps this filter
     */
    default Predicate<Path> asPredicate() {
        return path -> {
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                return test(path, attrs);
            } catch (IOException e) {
                return false;
            }
        };
    }
}
