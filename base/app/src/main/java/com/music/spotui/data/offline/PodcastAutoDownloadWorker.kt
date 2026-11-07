package com.music.spotui.data.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.spotui.data.api.Response
import com.music.spotui.data.preferences.PodcastPrefs
import com.music.spotui.di.Graph
import app.myspoti.host.android.MyspotiRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Periodically checks followed-to-download podcast shows, even when their page is not open. */
class PodcastAutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!applicationContext.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE)
                .getBoolean("LoggedIn", false)
        ) return Result.success()

        return try {
            MyspotiRuntime.api.ready.first { it }
            var hadError = false
            for (showId in PodcastPrefs.autoDownloadShowIds()) {
                if (isStopped) break
                val response = Graph.repository.provideShowPage(showId).first {
                    it is Response.Success || it is Response.Error
                }
                when (response) {
                    is Response.Success -> {
                        val page = response.data
                        if (!page.audiobook && page.items.isNotEmpty()) {
                            PodcastPrefs.autoDownloadCandidates(showId, page.items).forEach { episode ->
                                PodcastDownloader.enqueue(episode)
                            }
                        }
                    }
                    is Response.Error -> hadError = true
                    else -> Unit
                }
            }
            if (hadError) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

/** Six-hour cadence; Android runs it when networking and system scheduling allow. */
object PodcastAutoDownloadScheduler {
    private const val WORK_NAME = "myspoti-podcast-auto-download"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PodcastAutoDownloadWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
