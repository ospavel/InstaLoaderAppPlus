package com.alphacorp.instaloader.data.python

import com.alphacorp.instaloader.data.model.DownloadOptions
import com.alphacorp.instaloader.data.model.DownloadProgress
import com.alphacorp.instaloader.data.model.DownloadTargetType
import com.alphacorp.instaloader.data.model.InstagramSession
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONObject

class PythonBridge {

    private val python by lazy { Python.getInstance() }
    private val module by lazy { python.getModule("bridge") }
    private val builtins by lazy { python.getModule("builtins") }

    fun downloadWithOptions(
        targetType: DownloadTargetType,
        targetValue: String,
        options: DownloadOptions,
        baseDir: String,
        session: InstagramSession?,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val callback = onProgress.toPythonCallback()
        module.callAttr(
            "download_with_options",
            targetType.pythonValue,
            targetValue,
            options.toPyDict(baseDir, session),
            callback,
        )
    }

    fun getPostCount(username: String, options: DownloadOptions, session: InstagramSession?): Int {
        return module.callAttr("get_post_count", username, options.toPyDict("", session))
            .toInt()
    }

    fun verifyBrowserSession(cookieHeader: String, userAgent: String): InstagramSession {
        val response = module.callAttr("verify_browser_session", cookieHeader, userAgent).toString()
        return parseSessionResponse(response, userAgent)
    }

    fun legacySessionJson(username: String, sessionFile: String): InstagramSession {
        val response = module.callAttr("legacy_session_json", username, sessionFile).toString()
        return parseSessionResponse(response, userAgent = "")
    }

    fun cancelDownload() {
        module.callAttr("cancel_download")
    }

    private fun ((DownloadProgress) -> Unit).toPythonCallback(): (
        String,
        String,
        String,
        String,
        String,
        String,
        String,
        String,
    ) -> Unit = { phase, total, completed, failed, skipped, remaining, current, message ->
        this(
            DownloadProgress(
                phase = phase,
                total = total.toIntOrNull(),
                completed = completed.toIntOrNull() ?: 0,
                failed = failed.toIntOrNull() ?: 0,
                skipped = skipped.toIntOrNull() ?: 0,
                remaining = remaining.toIntOrNull(),
                current = current,
                message = message,
            ),
        )
    }

    private fun DownloadOptions.toPyDict(
        baseDir: String,
        session: InstagramSession?,
    ): PyObject {
        val entries = buildList<Pair<String, Any>> {
            add("quiet" to quiet)
            add("download_pictures" to downloadPictures)
            add("download_videos" to downloadVideos)
            add("download_video_thumbnails" to downloadVideoThumbnails)
            add("download_geotags" to downloadGeotags)
            add("download_comments" to downloadComments)
            add("save_metadata" to saveMetadata)
            add("compress_json" to compressJson)
            add("save_captions" to saveCaptions)
            add("post_metadata_txt_pattern" to postMetadataTxtPattern)
            add("storyitem_metadata_txt_pattern" to storyitemMetadataTxtPattern)
            add("profile_pic" to profilePic)
            add("posts" to posts)
            add("tagged" to tagged)
            add("igtv" to igtv)
            add("highlights" to highlights)
            add("stories" to stories)
            add("reels" to reels)
            add("fast_update" to fastUpdate)
            maxCount?.let { add("max_count" to it) }
            if (postFilter.isNotBlank()) add("post_filter" to postFilter)
            if (storyitemFilter.isNotBlank()) add("storyitem_filter" to storyitemFilter)
            if (dirnamePattern.isNotBlank()) add("dirname_pattern" to dirnamePattern)
            if (filenamePattern.isNotBlank()) add("filename_pattern" to filenamePattern)
            if (titlePattern.isNotBlank()) add("title_pattern" to titlePattern)
            add("sanitize_paths" to sanitizePaths)
            if (userAgent.isNotBlank()) add("user_agent" to userAgent)
            add("max_connection_attempts" to maxConnectionAttempts)
            add("request_timeout" to requestTimeout.toFloat())
            add("iphone_support" to iphoneSupport)
            add("enable_resume" to enableResume)
            add("resume_prefix" to resumePrefix)
            add("check_resume_bbd" to checkResumeBbd)
            if (slide.isNotBlank()) add("slide" to slide)
            add("base_dir" to baseDir)
            session?.let {
                add("session_username" to it.username)
                add("session_cookies_json" to it.cookiesJson)
                add("session_user_agent" to it.userAgent)
            }
        }
        val pairs: Array<Array<Any>> = entries
            .map { (key, value) -> arrayOf<Any>(key, value) }
            .toTypedArray()

        return builtins.callAttr("dict", pairs)
    }

    private fun parseSessionResponse(response: String, userAgent: String): InstagramSession = try {
        val envelope = JSONObject(response)
        val cookies = envelope.getJSONObject("cookies")
        val username = envelope.getString("username")
        if (username.isBlank() || cookies.optString("sessionid").isBlank()) {
            throw IllegalArgumentException("Incomplete session response")
        }
        InstagramSession(
            username = username,
            cookiesJson = cookies.toString(),
            userAgent = userAgent,
        )
    } catch (_: Exception) {
        throw IllegalStateException("Instagram returned an incomplete session response. Try signing in again.")
    }
}
