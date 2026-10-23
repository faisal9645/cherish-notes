package com.example.ui.chat

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A sent GIF's text: like "Sent a photo", never shown under it. */
const val GIF_CAPTION = "Sent a GIF"

/** One GIF from GIPHY: what the grid shows, and what's sent. */
data class GiphyGif(
    val id: String,
    // Small and light, for the grid
    val previewUrl: String,
    // The GIF that's sent: GIPHY's "downsized" one (under 2 MB)
    val sendUrl: String,
    // Its first frame, for the gallery's small tiles
    val stillUrl: String?,
    val width: Int,
    val height: Int
)

/**
 * GIPHY's trending GIFs and its search. Its key comes from .env (GIPHY_API_KEY, kept out of git);
 * without one there's no GIF tab. Pages are kept a few minutes, so opening the picker again, or
 * going back to a search, asks GIPHY nothing (its free key allows about 100 requests an hour).
 */
internal object Giphy {
    const val PAGE_SIZE = 24
    private const val KEEP_MS = 10 * 60 * 1000L

    private val pages = object : LinkedHashMap<String, Pair<Long, List<GiphyGif>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<GiphyGif>>>?) = size > 40
    }

    val isAvailable: Boolean
        get() = com.example.BuildConfig.GIPHY_API_KEY.isNotBlank()

    /** A page of trending GIFs, or null when GIPHY couldn't be reached. */
    suspend fun trending(offset: Int): List<GiphyGif>? = fetch("trending", null, offset)

    /** A page of GIFs for [query], or null when GIPHY couldn't be reached. */
    suspend fun search(query: String, offset: Int): List<GiphyGif>? = fetch("search", query, offset)

    private suspend fun fetch(kind: String, query: String?, offset: Int): List<GiphyGif>? = withContext(Dispatchers.IO) {
        val key = com.example.BuildConfig.GIPHY_API_KEY
        if (key.isBlank()) return@withContext null
        val pageKey = "$kind|${query?.lowercase()}|$offset"
        synchronized(pages) {
            pages[pageKey]?.let { (at, gifs) -> if (SystemClock.elapsedRealtime() - at < KEEP_MS) return@withContext gifs }
        }
        val url = buildString {
            append("https://api.giphy.com/v1/gifs/").append(kind)
            append("?api_key=").append(URLEncoder.encode(key, "UTF-8"))
            if (query != null) append("&q=").append(URLEncoder.encode(query, "UTF-8")).append("&lang=en")
            append("&limit=").append(PAGE_SIZE).append("&offset=").append(offset)
            append("&rating=pg-13&bundle=messaging_non_clips")
        }
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                if (connection.responseCode !in 200..299) {
                    Log.w("Giphy", "GIPHY answered ${connection.responseCode}")
                    return@withContext null
                }
                val gifs = parse(connection.inputStream.bufferedReader().use { it.readText() })
                synchronized(pages) { pages[pageKey] = SystemClock.elapsedRealtime() to gifs }
                gifs
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w("Giphy", "Couldn't load GIFs", e)
            null
        }
    }

    private fun parse(body: String): List<GiphyGif> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val gifs = ArrayList<GiphyGif>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val images = item.optJSONObject("images") ?: continue
            val fixed = images.optJSONObject("fixed_width") ?: continue
            val light = images.optJSONObject("fixed_width_downsampled")
            val preview = light?.optString("webp")?.takeIf { it.isNotBlank() }
                ?: fixed.optString("webp").takeIf { it.isNotBlank() }
                ?: fixed.optString("url")
            val send = images.optJSONObject("downsized")?.optString("url")?.takeIf { it.isNotBlank() }
                ?: fixed.optString("url")
            if (preview.isBlank() || send.isBlank()) continue
            gifs += GiphyGif(
                id = item.optString("id").ifBlank { send },
                previewUrl = preview,
                sendUrl = send,
                stillUrl = images.optJSONObject("fixed_width_still")?.optString("url")?.takeIf { it.isNotBlank() },
                width = fixed.optString("width").toIntOrNull()?.coerceAtLeast(1) ?: 200,
                height = fixed.optString("height").toIntOrNull()?.coerceAtLeast(1) ?: 200
            )
        }
        return gifs
    }
}

/** Searches a couple reaches for, offered under an empty search box. */
private val GifSuggestions = listOf(
    "❤️ Love", "😘 Kiss", "🤗 Hug", "🥺 Miss you", "🌙 Good night", "☀️ Good morning", "😂 Funny", "🥰 Cute"
)

/**
 * GIPHY's GIFs in a sheet, like WhatsApp's: trending ones first, a search box, and suggestions
 * for a couple; a tap sends one (see [onPick]). More load as the grid nears its end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerSheet(
    onPick: (GiphyGif) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val gifs = remember { mutableStateListOf<GiphyGif>() }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            gifs.isNotEmpty() && last >= gifs.size - 6
        }
    }

    // Trending with nothing typed, else a search a moment after the typing stops; then the next
    // page each time the grid nears its end. A new search starts this over.
    LaunchedEffect(query) {
        val search = query.trim()
        if (search.isNotEmpty()) delay(400)
        loading = true
        failed = false
        val first = if (search.isEmpty()) Giphy.trending(0) else Giphy.search(search, 0)
        gifs.clear()
        loading = false
        if (first == null) {
            failed = true
            return@LaunchedEffect
        }
        gifs.addAll(first.distinctBy { it.id })
        if (gifs.isNotEmpty()) gridState.scrollToItem(0)
        var offset = first.size
        var endReached = first.size < Giphy.PAGE_SIZE
        snapshotFlow { nearEnd }.filter { it }.collect {
            if (endReached) return@collect
            loading = true
            val page = if (search.isEmpty()) Giphy.trending(offset) else Giphy.search(search, offset)
            loading = false
            // A page that failed is asked for again the next time the grid nears its end
            if (page == null) return@collect
            offset += page.size
            endReached = page.size < Giphy.PAGE_SIZE
            val known = gifs.mapTo(HashSet()) { it.id }
            gifs.addAll(page.filter { it.id !in known }.distinctBy { it.id })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = com.example.ui.components.CherishDialogDefaults.sheetShape,
        containerColor = com.example.ui.components.CherishDialogDefaults.sheetContainerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search GIFs") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (query.isBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp)
                ) {
                    GifSuggestions.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { query = suggestion.substringAfter(' ') }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    verticalItemSpacing = 6.dp,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(gifs, key = { it.id }) { gif ->
                        AsyncImage(
                            model = gif.previewUrl,
                            contentDescription = "GIF",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(gif.width.toFloat() / gif.height.toFloat())
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(gif) }
                        )
                    }
                }
                when {
                    loading && gifs.isEmpty() -> CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                    !loading && gifs.isEmpty() -> Text(
                        text = when {
                            failed -> "Couldn't load GIFs. Check your connection."
                            query.isBlank() -> "No GIFs right now"
                            else -> "No GIFs for \"${query.trim()}\""
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

        }
    }
}
