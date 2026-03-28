package com.nononsenseapps.feeder.db.room

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface BlocklistDao {
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
            with recursive split(feed_id, pattern, rest, is_allow) as (
                select id, '', local_block_list || char(10), 0 from feeds where local_block_list != ''
                union all
                select id, '', local_allow_list || char(10), 1 from feeds where local_allow_list != ''
                union all
                select feed_id, lower(substr(rest, 1, instr(rest, char(10)) - 1)), substr(rest, instr(rest, char(10)) + 1), is_allow
                from split where rest != ''
            )
            update feed_items
            set block_time = case
                when
                    exists(select 1 from blocklist where lower(feed_items.plain_title) glob blocklist.glob_pattern)
                    OR
                    exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 0 and split.pattern != '' and lower(feed_items.plain_title) glob '*' || split.pattern || '*')
                    OR
                    (
                        exists(select 1 from feeds where id = feed_items.feed_id and local_allow_list != '')
                        AND NOT exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 1 and split.pattern != '' and lower(feed_items.plain_title) glob '*' || split.pattern || '*')
                    )
                then coalesce(block_time, :blockTime)
                else null
                end
        """,
    )
    suspend fun setItemBlockStatusTitleOnly(blockTime: Instant)

    @Query(
        """
            with recursive split(feed_id, pattern, rest, is_allow) as (
                select id, '', local_block_list || char(10), 0 from feeds where local_block_list != ''
                union all
                select id, '', local_allow_list || char(10), 1 from feeds where local_allow_list != ''
                union all
                select feed_id, lower(substr(rest, 1, instr(rest, char(10)) - 1)), substr(rest, instr(rest, char(10)) + 1), is_allow
                from split where rest != ''
            )
            update feed_items
            set block_time = case
                when
                    exists(select 1 from blocklist where lower(feed_items.plain_title) glob blocklist.glob_pattern or lower(feed_items.plain_snippet) glob blocklist.glob_pattern)
                    OR
                    exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 0 and split.pattern != '' and (lower(feed_items.plain_title) glob '*' || split.pattern || '*' or lower(feed_items.plain_snippet) glob '*' || split.pattern || '*'))
                    OR
                    (
                        exists(select 1 from feeds where id = feed_items.feed_id and local_allow_list != '')
                        AND NOT exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 1 and split.pattern != '' and (lower(feed_items.plain_title) glob '*' || split.pattern || '*' or lower(feed_items.plain_snippet) glob '*' || split.pattern || '*'))
                    )
                then coalesce(block_time, :blockTime)
                else null
                end
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
            with recursive split(feed_id, pattern, rest, is_allow) as (
                select id, '', local_block_list || char(10), 0 from feeds where local_block_list != ''
                union all
                select id, '', local_allow_list || char(10), 1 from feeds where local_allow_list != ''
                union all
                select feed_id, lower(substr(rest, 1, instr(rest, char(10)) - 1)), substr(rest, instr(rest, char(10)) + 1), is_allow
                from split where rest != ''
            )
            update feed_items
            set block_time = case
                when
                    exists(select 1 from blocklist where lower(feed_items.plain_title) glob blocklist.glob_pattern)
                    OR
                    exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 0 and split.pattern != '' and lower(feed_items.plain_title) glob '*' || split.pattern || '*')
                    OR
                    (
                        exists(select 1 from feeds where id = feed_items.feed_id and local_allow_list != '')
                        AND NOT exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 1 and split.pattern != '' and lower(feed_items.plain_title) glob '*' || split.pattern || '*')
                    )
                then :blockTime
                else null
                end
            where block_time is null
        """,
    )
    suspend fun setItemBlockStatusWhereNullTitleOnly(blockTime: Instant)

    @Query(
        """
            with recursive split(feed_id, pattern, rest, is_allow) as (
                select id, '', local_block_list || char(10), 0 from feeds where local_block_list != ''
                union all
                select id, '', local_allow_list || char(10), 1 from feeds where local_allow_list != ''
                union all
                select feed_id, lower(substr(rest, 1, instr(rest, char(10)) - 1)), substr(rest, instr(rest, char(10)) + 1), is_allow
                from split where rest != ''
            )
            update feed_items
            set block_time = case
                when
                    exists(select 1 from blocklist where lower(feed_items.plain_title) glob blocklist.glob_pattern or lower(feed_items.plain_snippet) glob blocklist.glob_pattern)
                    OR
                    exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 0 and split.pattern != '' and (lower(feed_items.plain_title) glob '*' || split.pattern || '*' or lower(feed_items.plain_snippet) glob '*' || split.pattern || '*'))
                    OR
                    (
                        exists(select 1 from feeds where id = feed_items.feed_id and local_allow_list != '')
                        AND NOT exists(select 1 from split where split.feed_id = feed_items.feed_id and split.is_allow = 1 and split.pattern != '' and (lower(feed_items.plain_title) glob '*' || split.pattern || '*' or lower(feed_items.plain_snippet) glob '*' || split.pattern || '*'))
                    )
                then :blockTime
                else null
                end
            where block_time is null
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
    }

    @Query(
        """
            with recursive split(feed_id, pattern, rest, is_allow) as (
                select id, '', local_block_list || char(10), 0 from feeds where id = :feedId
                union all
                select id, '', local_allow_list || char(10), 1 from feeds where id = :feedId
                union all
                select feed_id, lower(substr(rest, 1, instr(rest, char(10)) - 1)), substr(rest, instr(rest, char(10)) + 1), is_allow
                from split where rest != ''
            )
            update feed_items
            set block_time = case
                when
                    exists(select 1 from blocklist where lower(feed_items.plain_title) glob blocklist.glob_pattern)
                    OR
                    exists(select 1 from split where split.is_allow = 0 and split.pattern != '' and lower(feed_items.plain_title) glob '*' || split.pattern || '*')
                    OR
                    (
                        exists(select 1 from feeds where id = :feedId and local_allow_list != '')
                        AND NOT exists(select 1 from split where split.is_allow = 1 and split.pattern != '' and lower(feed_items.plain_title) glob '*' || split.pattern || '*')
                    )
                then coalesce(block_time, :blockTime)
                else null
                end
            where feed_id = :feedId
        """,
    )
    suspend fun setItemBlockStatusForNewInFeedTitleOnly(
        feedId: Long,
        blockTime: Instant,
    )

    @Query(
        """
            with recursive split(feed_id, pattern, rest, is_allow) as (
                select id, '', local_block_list || char(10), 0 from feeds where id = :feedId
                union all
                select id, '', local_allow_list || char(10), 1 from feeds where id = :feedId
                union all
                select feed_id, lower(substr(rest, 1, instr(rest, char(10)) - 1)), substr(rest, instr(rest, char(10)) + 1), is_allow
                from split where rest != ''
            )
            update feed_items
            set block_time = case
                when
                    exists(select 1 from blocklist where lower(feed_items.plain_title) glob blocklist.glob_pattern or lower(feed_items.plain_snippet) glob blocklist.glob_pattern)
                    OR
                    exists(select 1 from split where split.is_allow = 0 and split.pattern != '' and (lower(feed_items.plain_title) glob '*' || split.pattern || '*' or lower(feed_items.plain_snippet) glob '*' || split.pattern || '*'))
                    OR
                    (
                        exists(select 1 from feeds where id = :feedId and local_allow_list != '')
                        AND NOT exists(select 1 from split where split.is_allow = 1 and split.pattern != '' and (lower(feed_items.plain_title) glob '*' || split.pattern || '*' or lower(feed_items.plain_snippet) glob '*' || split.pattern || '*'))
                    )
                then coalesce(block_time, :blockTime)
                else null
                end
            where feed_id = :feedId
        """,
    )
    suspend fun setItemBlockStatusForNewInFeedWithSummaries(
        feedId: Long,
        blockTime: Instant,
    )
}
