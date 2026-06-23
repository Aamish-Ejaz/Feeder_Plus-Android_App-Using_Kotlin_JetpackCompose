package com.nononsenseapps.feeder.db.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface BlocklistDao {

    // ---------------------------------------------------------------
    // Helper methods for resetting block status
    // ---------------------------------------------------------------

    @Query("UPDATE feed_items SET block_time = NULL")
    suspend fun clearAllBlockStatus()

    @Query("SELECT id FROM feeds")
    suspend fun getAllFeedIds(): List<Long>

    @Query("UPDATE feed_items SET block_time = NULL WHERE feed_id = :feedId")
    suspend fun clearBlockStatusForFeed(feedId: Long)

    /**
     * Completely resets blocklist for all feeds:
     * 1. Clears all block_time (even for items that were blocked before)
     * 2. Applies global blocklist patterns
     * 3. Applies per-feed filters for every feed
     */
    suspend fun refreshAllBlockStatus(
        blockTime: Instant,
        applyToSummaries: Boolean
    ) {
        // Step 1: Clear all block_time
        clearAllBlockStatus()

        // Step 2: Apply global blocklist
        if (applyToSummaries) {
            setItemBlockStatusWithSummaries(blockTime)
        } else {
            setItemBlockStatusTitleOnly(blockTime)
        }

        // Step 3: Apply per-feed filters for all feeds
        val allFeedIds = getAllFeedIds()
        for (feedId in allFeedIds) {
            applyPerFeedFilters(feedId, blockTime)
        }
    }

    // ---------------------------------------------------------------
    // Existing methods (unchanged)
    // ---------------------------------------------------------------

    @Query(
        """
            INSERT INTO blocklist (id, glob_pattern)
            VALUES (null, '*' || :pattern || '*')
        """,
    )
    suspend fun insertSafely(pattern: String)

    @Query(
        """
            DELETE FROM blocklist
            WHERE glob_pattern = ('*' || :pattern || '*')
        """,
    )
    suspend fun deletePattern(pattern: String)

    @Query(
        """
            SELECT glob_pattern
            FROM blocklist
            ORDER BY glob_pattern
        """,
    )
    fun getGlobPatterns(): Flow<List<String>>

    suspend fun setItemBlockStatus(
        blockTime: Instant,
        applyToSummaries: Boolean,
    ) {
        if (applyToSummaries) {
            setItemBlockStatusWithSummaries(blockTime)
        } else {
            setItemBlockStatusTitleOnly(blockTime)
        }
    }

    @Query(
        """
            UPDATE feed_items
            SET block_time = CASE
                WHEN EXISTS(SELECT 1 FROM blocklist WHERE lower(feed_items.plain_title) GLOB blocklist.glob_pattern)
                THEN coalesce(block_time, :blockTime)
                ELSE NULL
                END
        """,
    )
    suspend fun setItemBlockStatusTitleOnly(blockTime: Instant)

    @Query(
        """
            UPDATE feed_items
            SET block_time = CASE
                WHEN EXISTS(SELECT 1 FROM blocklist WHERE lower(feed_items.plain_title) GLOB blocklist.glob_pattern OR lower(feed_items.plain_snippet) GLOB blocklist.glob_pattern)
                THEN coalesce(block_time, :blockTime)
                ELSE NULL
                END
        """,
    )
    suspend fun setItemBlockStatusWithSummaries(blockTime: Instant)

    suspend fun setItemBlockStatusWhereNull(
        blockTime: Instant,
        applyToSummaries: Boolean,
    ) {
        if (applyToSummaries) {
            setItemBlockStatusWhereNullWithSummaries(blockTime)
        } else {
            setItemBlockStatusWhereNullTitleOnly(blockTime)
        }
    }

    @Query(
        """
            UPDATE feed_items
            SET block_time = CASE
                WHEN EXISTS(SELECT 1 FROM blocklist WHERE lower(feed_items.plain_title) GLOB blocklist.glob_pattern)
                THEN :blockTime
                ELSE NULL
                END
            WHERE block_time IS NULL
        """,
    )
    suspend fun setItemBlockStatusWhereNullTitleOnly(blockTime: Instant)

    @Query(
        """
            UPDATE feed_items
            SET block_time = CASE
                WHEN EXISTS(SELECT 1 FROM blocklist WHERE lower(feed_items.plain_title) GLOB blocklist.glob_pattern OR lower(feed_items.plain_snippet) GLOB blocklist.glob_pattern)
                THEN :blockTime
                ELSE NULL
                END
            WHERE block_time IS NULL
        """,
    )
    suspend fun setItemBlockStatusWhereNullWithSummaries(blockTime: Instant)

    suspend fun setItemBlockStatusForNewInFeed(
        feedId: Long,
        blockTime: Instant,
        applyToSummaries: Boolean,
    ) {
        if (applyToSummaries) {
            setItemBlockStatusForNewInFeedWithSummaries(feedId, blockTime)
        } else {
            setItemBlockStatusForNewInFeedTitleOnly(feedId, blockTime)
        }
        applyPerFeedFilters(feedId, blockTime)
    }

    @Query(
        """
            UPDATE feed_items
            SET block_time = CASE
                WHEN EXISTS(SELECT 1 FROM blocklist WHERE lower(feed_items.plain_title) GLOB blocklist.glob_pattern)
                THEN :blockTime
                ELSE NULL
                END
            WHERE feed_id = :feedId AND block_time IS NULL
        """,
    )
    suspend fun setItemBlockStatusForNewInFeedTitleOnly(
        feedId: Long,
        blockTime: Instant,
    )

    @Query(
        """
            UPDATE feed_items
            SET block_time = CASE
                WHEN EXISTS(SELECT 1 FROM blocklist WHERE lower(feed_items.plain_title) GLOB blocklist.glob_pattern OR lower(feed_items.plain_snippet) GLOB blocklist.glob_pattern)
                THEN :blockTime
                ELSE NULL
                END
            WHERE feed_id = :feedId AND block_time IS NULL
        """,
    )
    suspend fun setItemBlockStatusForNewInFeedWithSummaries(
        feedId: Long,
        blockTime: Instant,
    )

    // ---------------------------------------------------------------
    // Per-feed filtering
    // ---------------------------------------------------------------

    suspend fun applyPerFeedFilters(
        feedId: Long,
        blockTime: Instant,
    ) {
        val feed = getFeedFilters(feedId) ?: return
        applyPerFeedFilters(feedId, blockTime, feed.localBlockList, feed.localAllowList)
    }

    suspend fun applyPerFeedFilters(
        feedId: Long,
        blockTime: Instant,
        localBlockList: String,
        localAllowList: String,
    ) {
        // Apply per-feed block list
        val blockPatterns = localBlockList
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (rawPattern in blockPatterns) {
            val glob = if (rawPattern.contains("*")) rawPattern.lowercase() else "*${rawPattern.lowercase()}*"
            blockItemsMatchingPattern(feedId, glob, blockTime)
        }

        // Apply per-feed allow list
        val allowPatterns = localAllowList
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (allowPatterns.isNotEmpty()) {
            val globs = allowPatterns.map { rawPattern ->
                if (rawPattern.contains("*")) rawPattern.lowercase() else "*${rawPattern.lowercase()}*"
            }
            blockItemsNotMatchingAllowList(feedId, globs, blockTime)
        }
    }

    @Query("SELECT local_block_list, local_allow_list FROM feeds WHERE id = :feedId")
    suspend fun getFeedFilters(feedId: Long): FeedFilters?

    @Query(
        """
            UPDATE feed_items
            SET block_time = :blockTime
            WHERE feed_id = :feedId
              AND block_time IS NULL
              AND lower(plain_title) GLOB :pattern
        """,
    )
    suspend fun blockItemsMatchingPattern(
        feedId: Long,
        pattern: String,
        blockTime: Instant,
    )

    @Query(
        """
            UPDATE feed_items
            SET block_time = :blockTime
            WHERE feed_id = :feedId
              AND NOT (
                    lower(plain_title) GLOB :pattern1
                 OR (:pattern2 IS NOT NULL AND lower(plain_title) GLOB :pattern2)
                 OR (:pattern3 IS NOT NULL AND lower(plain_title) GLOB :pattern3)
                 OR (:pattern4 IS NOT NULL AND lower(plain_title) GLOB :pattern4)
                 OR (:pattern5 IS NOT NULL AND lower(plain_title) GLOB :pattern5)
              )
        """,
    )
    suspend fun blockItemsNotMatchingPatterns(
        feedId: Long,
        pattern1: String,
        pattern2: String?,
        pattern3: String?,
        pattern4: String?,
        pattern5: String?,
        blockTime: Instant,
    )

    suspend fun blockItemsNotMatchingAllowList(
        feedId: Long,
        patterns: List<String>,
        blockTime: Instant,
    ) {
        if (patterns.isEmpty()) return
        val p = patterns + listOf(null, null, null, null, null)
        blockItemsNotMatchingPatterns(
            feedId = feedId,
            pattern1 = p[0]!!,
            pattern2 = p[1],
            pattern3 = p[2],
            pattern4 = p[3],
            pattern5 = p[4],
            blockTime = blockTime,
        )
    }
}

data class FeedFilters(
    @ColumnInfo(name = "local_block_list") val localBlockList: String,
    @ColumnInfo(name = "local_allow_list") val localAllowList: String,
)
