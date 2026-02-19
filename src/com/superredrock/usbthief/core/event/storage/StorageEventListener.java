package com.superredrock.usbthief.core.event.storage;

/**
 * Convenience interface for listening to all storage-related events.
 * Implementations can override only the event methods they care about.
 *
 * <p>Default implementations do nothing, allowing selective listening.
 *
 * <p>Usage example:
 * <pre>
 * StorageEventListener listener = new StorageEventListener() {
 *     {@literal @}Override
 *     public void onStorageLow(StorageLowEvent event) {
 *         System.out.println("Storage low: " + event.freeBytes());
 *     }
 *     // Other methods can be left as no-ops
 * };
 *
 * EventBus.getInstance().register(StorageLowEvent.class, listener::onStorageLow);
 * EventBus.getInstance().register(StorageRecoveredEvent.class, listener::onStorageRecovered);
 * EventBus.getInstance().register(FilesRecycledEvent.class, listener::onFilesRecycled);
 * EventBus.getInstance().register(EmptyFoldersDeletedEvent.class, listener::onEmptyFoldersDeleted);
 * </pre>
 */
public interface StorageEventListener {

    /**
     * Called when storage is low or critically low.
     *
     * @param event the storage low event
     */
    default void onStorageLow(StorageLowEvent event) {
        // Default: no action
    }

    /**
     * Called when storage recovers from a low state.
     *
     * @param event the storage recovered event
     */
    default void onStorageRecovered(StorageRecoveredEvent event) {
        // Default: no action
    }

    /**
     * Called when files are recycled from storage.
     *
     * @param event the files recycled event
     */
    default void onFilesRecycled(FilesRecycledEvent event) {
        // Default: no action
    }

    /**
     * Called when empty folders are deleted during cleanup.
     *
     * @param event the empty folders deleted event
     */
    default void onEmptyFoldersDeleted(EmptyFoldersDeletedEvent event) {
        // Default: no action
    }
}
