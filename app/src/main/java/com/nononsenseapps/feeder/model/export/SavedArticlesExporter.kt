package com.nononsenseapps.feeder.model.export

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.nononsenseapps.feeder.R
import com.nononsenseapps.feeder.db.room.FeedItem
import com.nononsenseapps.feeder.db.room.FeedItemDao
import com.nononsenseapps.feeder.util.Either
import com.nononsenseapps.feeder.util.ToastMaker
import com.nononsenseapps.feeder.util.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import kotlin.system.measureTimeMillis

private const val LOG_TAG = "FEEDER_SAVEDARTEXPORT"

suspend fun exportSavedArticles(
    di: DI,
    uri: Uri,
): Either<SavedArticlesExportError, Unit> =
    Either.catching(
        onCatch = {
            Log.e(LOG_TAG, "Failed to export saved articles", it)
            val toastMaker = di.direct.instance<ToastMaker>()
            toastMaker.makeToast(R.string.failed_to_export_saved_articles)
            (it.localizedMessage ?: it.message)?.let { message ->
                toastMaker.makeToast(message)
            }

            SavedArticleExportUnknownError(it)
        },
    ) {
        withContext(Dispatchers.IO) {
            val time =
                measureTimeMillis {
                    val contentResolver: ContentResolver by di.instance()
                    val feedItemDao: FeedItemDao by di.instance()
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { bw ->
                        feedItemDao
                            .getLinksOfBookmarks()
                            .forEach { link ->
                                bw.write(link)
                                bw.newLine()
                            }
                    }
                }
            logDebug(LOG_TAG, "Exported saved articles in $time ms on ${Thread.currentThread().name}")
        }
    }

suspend fun importSavedArticles(
    di: DI,
    uri: Uri,
): Either<SavedArticlesExportError, Unit> =
    Either.catching(
        onCatch = {
            Log.e(LOG_TAG, "Failed to import saved articles", it)
            val toastMaker = di.direct.instance<ToastMaker>()
            toastMaker.makeToast("Failed to import saved articles")
            (it.localizedMessage ?: it.message)?.let { message ->
                toastMaker.makeToast(message)
            }

            SavedArticleExportUnknownError(it)
        },
    ) {
        withContext(Dispatchers.IO) {
            val contentResolver: ContentResolver by di.instance()
            val feedItemDao: FeedItemDao by di.instance()
            val toastMaker: ToastMaker by di.instance()

            var importedCount = 0
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        val url = line.trim()
                        if (url.isNotEmpty()) {
                            // Check if an item with this link already exists and is bookmarked
                            val existingItem = feedItemDao.loadFeedItemByLink(url)
                            if (existingItem != null) {
                                if (!existingItem.bookmarked) {
                                    feedItemDao.setBookmarked(existingItem.id, true)
                                    importedCount++
                                }
                                // Already bookmarked - skip
                            } else {
                                // Create a placeholder item so the bookmark is preserved
                                val newItem = FeedItem(
                                    guid = url,
                                    plainTitle = url,
                                    link = url,
                                    bookmarked = true,
                                    firstSyncedTime = Instant.now(),
                                    primarySortTime = Instant.now(),
                                )
                                feedItemDao.insertFeedItem(newItem)
                                importedCount++
                            }
                        }
                    }
                }
            }
            logDebug(LOG_TAG, "Imported $importedCount saved articles")
            toastMaker.makeToast("Imported $importedCount saved articles")
        }
    }

sealed class SavedArticlesExportError {
    abstract val throwable: Throwable?
}

data class SavedArticleExportUnknownError(
    override val throwable: Throwable,
) : SavedArticlesExportError()
