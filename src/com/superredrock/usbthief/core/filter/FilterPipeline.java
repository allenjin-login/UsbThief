package com.superredrock.usbthief.core.filter;

import com.superredrock.usbthief.core.config.ConfigManager;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

/**
 * A pipeline that combines multiple file filters using AND logic.
 *
 * <p>A file passes the pipeline only if it passes all filters in sequence.
 * Filters are evaluated in order, with short-circuit evaluation (stops at first failure).
 *
 * <p>Use {@link #createDefault()} to create a standard pipeline with
 * BasicFileFilter and SuffixFilter configured from application settings.
 */
public class FilterPipeline implements FileFilter {

    protected static final Logger logger = Logger.getLogger(FilterPipeline.class.getName());

    private final List<FileFilter> filters;

    /**
     * Creates an empty pipeline.
     */
    public FilterPipeline() {
        this.filters = new ArrayList<>();
    }

    /**
     * Creates a pipeline with the specified filters.
     *
     * @param filters the initial filters to add
     */
    public FilterPipeline(List<FileFilter> filters) {
        this.filters = new ArrayList<>(filters);
    }

    /**
     * Creates a default pipeline with BasicFileFilter and SuffixFilter.
     *
     * <p>The filters are configured from the current ConfigManager settings.
     *
     * @return a new FilterPipeline with default filters
     */
    public static FilterPipeline createDefault() {
        FilterPipeline pipeline = new FilterPipeline();
        pipeline.addFilter(new BasicFileFilter());
        pipeline.addFilter(new SuffixFilter());
        return pipeline;
    }

    /**
     * Creates a default pipeline with a custom ConfigManager.
     * Useful for testing with mock configurations.
     *
     * @param configManager the configuration manager to use
     * @return a new FilterPipeline with default filters using the specified config
     */
    public static FilterPipeline createDefault(ConfigManager configManager) {
        FilterPipeline pipeline = new FilterPipeline();
        pipeline.addFilter(new BasicFileFilter(configManager));
        pipeline.addFilter(new SuffixFilter(configManager));
        return pipeline;
    }

    /**
     * Add a filter to the pipeline.
     *
     * @param filter the filter to add
     * @throws IllegalArgumentException if filter is null
     */
    public void addFilter(FileFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }
        filters.add(filter);
    }

    /**
     * Remove a filter from the pipeline.
     *
     * @param filter the filter to remove
     * @return true if the filter was removed
     */
    public boolean removeFilter(FileFilter filter) {
        return filters.remove(filter);
    }

    /**
     * Clear all filters from the pipeline.
     */
    public void clearFilters() {
        filters.clear();
    }

    /**
     * Get the number of filters in the pipeline.
     *
     * @return the filter count
     */
    public int getFilterCount() {
        return filters.size();
    }

    @Override
    public boolean test(Path path, BasicFileAttributes attrs) {
        // Empty pipeline passes all files
        if (filters.isEmpty()) {
            return true;
        }

        // Evaluate each filter with short-circuit evaluation
        for (FileFilter filter : filters) {
            if (!filter.test(path, attrs)) {
                logger.fine("File blocked by filter " + filter.getClass().getSimpleName() + ": " + path);
                return false;
            }
        }

        return true;
    }

    /**
     * Create a new pipeline that combines this pipeline with another filter.
     *
     * @param other the filter to combine with
     * @return a new pipeline with both filters
     */
    @Override
    public FileFilter and(BiPredicate<? super Path, ? super BasicFileAttributes> other) {
        FilterPipeline combined = new FilterPipeline();
        for (FileFilter filter : this.filters) {
            combined.addFilter(filter);
        }
        combined.addFilter(other::test);
        return combined;
    }
}
