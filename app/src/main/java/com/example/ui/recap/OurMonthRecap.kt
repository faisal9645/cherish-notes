package com.example.ui.recap

import android.content.Context
import android.icu.text.BreakIterator
import com.example.CherishApplication
import com.example.data.model.Message
import com.example.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** An emoji and how often it was used. */
data class EmojiCount(val emoji: String, val count: Int)

/**
 * One month of us in numbers, for the "Our month" story. "Mine" is this phone's user. Days run
 * from 6 AM, like the chat's days.
 */
data class MonthRecap(
    val year: Int,
    /** Calendar.MONTH: 0 is January. */
    val month: Int,
    val totalMessages: Int,
    val mine: Int,
    val theirs: Int,
    /** Day of the month with the most messages (0 when none). */
    val busiestDay: Int,
    val busiestDayCount: Int,
    /** The hour (0 to 23) we write most at, -1 when none. */
    val favouriteHour: Int,
    val loveYous: Int,
    val topEmojis: List<EmojiCount>,
    val myEmoji: String?,
    val partnerEmoji: String?,
    val photos: Int,
    val videos: Int,
    val voiceNotes: Int,
    val photoPreviews: List<String>,
    /** One per day of the month: did we both write that day. */
    val daysWeTalked: List<Boolean>,
    val longestStreak: Int,
    val cutestText: String?,
    val cutestIsMine: Boolean,
    val cutestAt: Long
) {
    val daysInMonth: Int get() = daysWeTalked.size
    val daysTalkedCount: Int get() = daysWeTalked.count { it }
}

/** Works out last month's recap (once, then kept on the phone) and when to offer it. */
object OurMonthRecap {
    private const val PREFS = "cherish_our_month"
    private const val VERSION = 1
    private const val DAY_START_MS = 6L * 60 * 60 * 1000
    /** The chat offers last month's story during the first days of a month. */
    private const val INVITE_DAYS = 7

    /** A month by our 6 AM days: from the 1st at 6 AM until the next month's 1st at 6 AM. */
    class Range(val year: Int, val month: Int, val start: Long, val end: Long, val days: Int) {
        val key: String get() = String.format(Locale.US, "%04d-%02d", year, month + 1)
    }

    /** The month that has just finished. */
    fun lastMonth(now: Long = System.currentTimeMillis()): Range {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now - DAY_START_MS
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = cal.timeInMillis
        cal.add(Calendar.MONTH, -1)
        return Range(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH),
            start = cal.timeInMillis,
            end = end,
            days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dayOfMonthNow(now: Long = System.currentTimeMillis()): Int =
        Calendar.getInstance().apply { timeInMillis = now - DAY_START_MS }.get(Calendar.DAY_OF_MONTH)

    /** The first days of a new month, and last month's story hasn't been offered in the chat yet. */
    fun isInviteDue(context: Context): Boolean =
        dayOfMonthNow() <= INVITE_DAYS && !prefs(context).getBoolean("invited_${lastMonth().key}", false)

    fun markInvited(context: Context) {
        prefs(context).edit().putBoolean("invited_${lastMonth().key}", true).apply()
    }

    /** Shows "New" on the Love & Us card until the story has been watched (in the first days). */
    fun isNew(context: Context): Boolean =
        dayOfMonthNow() <= INVITE_DAYS && !prefs(context).getBoolean("watched_${lastMonth().key}", false)

    fun markWatched(context: Context, recap: MonthRecap) {
        val key = String.format(Locale.US, "%04d-%02d", recap.year, recap.month + 1)
        prefs(context).edit().putBoolean("watched_$key", true).apply()
    }

    /**
     * Last month's recap: worked out once from the server, then kept on the phone. Null when it
     * can't be read now (offline, not signed in).
     */
    suspend fun load(context: Context, myId: String, range: Range = lastMonth()): MonthRecap? {
        if (myId.isBlank() || myId == "user_me") return null
        val app = context.applicationContext as? CherishApplication ?: return null
        val repository = app.chatRepository
        val cacheKey = "recap_${repository.getConversationId()}_${myId}_${range.key}"
        val prefs = prefs(context)
        prefs.getString(cacheKey, null)?.let { cached -> fromJson(cached)?.let { return it } }
        val messages = repository.loadMessagesBetween(range.start, range.end) ?: return null
        val recap = withContext(Dispatchers.Default) { compute(messages, myId, range) }
        prefs.edit().putString(cacheKey, toJson(recap)).apply()
        return recap
    }

    fun compute(all: List<Message>, myId: String, range: Range): MonthRecap {
        val messages = all.filter { !it.isDeleted && it.timestamp >= range.start && it.timestamp < range.end }
        val days = range.days
        val perDay = IntArray(days)
        val iWrote = BooleanArray(days)
        val theyWrote = BooleanArray(days)
        val perHour = IntArray(24)
        val emojis = HashMap<String, Int>()
        val myEmojis = HashMap<String, Int>()
        val theirEmojis = HashMap<String, Int>()
        val photoPreviews = mutableListOf<Pair<String, Int>>()
        var mine = 0
        var theirs = 0
        var loveYous = 0
        var photos = 0
        var videos = 0
        var voiceNotes = 0
        var cutest: Message? = null
        var cutestScore = 0.0
        val cal = Calendar.getInstance()

        for (message in messages) {
            val isMine = message.senderId == myId
            if (isMine) mine++ else theirs++
            cal.timeInMillis = message.timestamp - DAY_START_MS
            val day = (cal.get(Calendar.DAY_OF_MONTH) - 1).coerceIn(0, days - 1)
            perDay[day]++
            if (isMine) iWrote[day] = true else theyWrote[day] = true
            cal.timeInMillis = message.timestamp
            perHour[cal.get(Calendar.HOUR_OF_DAY)]++

            val type = message.getTypedType()
            when {
                type == MessageType.IMAGE -> {
                    val urls = message.getAllMediaUrls()
                    photos += urls.size
                    val liked = (if (message.isStarred) 3 else 0) + message.reactions.size
                    urls.forEach { photoPreviews += message.thumbnailFor(it) to liked }
                }
                type == MessageType.VIDEO || message.isCircularVideoNote() -> videos++
                type == MessageType.AUDIO -> voiceNotes++
                type == MessageType.TEXT && message.text.isNotBlank() -> {
                    loveYous += LOVE_YOU.findAll(message.text).count()
                    forEachEmoji(message.text) { emoji ->
                        emojis.bump(emoji)
                        (if (isMine) myEmojis else theirEmojis).bump(emoji)
                    }
                    val score = cuteScore(message)
                    if (score > 0.0 && score >= cutestScore) {
                        cutest = message
                        cutestScore = score
                    }
                }
            }
            // Reactions are emoji we used too
            message.reactions.forEach { (userId, reaction) ->
                forEachEmoji(reaction) { emoji ->
                    emojis.bump(emoji)
                    (if (userId == myId) myEmojis else theirEmojis).bump(emoji)
                }
            }
        }

        val busiest = perDay.indices.maxByOrNull { perDay[it] }?.takeIf { perDay[it] > 0 }
        val favouriteHour = perHour.indices.maxByOrNull { perHour[it] }?.takeIf { perHour[it] > 0 } ?: -1
        val talked = List(days) { iWrote[it] && theyWrote[it] }
        var longest = 0
        var run = 0
        talked.forEach { both ->
            run = if (both) run + 1 else 0
            longest = maxOf(longest, run)
        }
        val chosen = cutest
        return MonthRecap(
            year = range.year,
            month = range.month,
            totalMessages = messages.size,
            mine = mine,
            theirs = theirs,
            busiestDay = busiest?.plus(1) ?: 0,
            busiestDayCount = busiest?.let { perDay[it] } ?: 0,
            favouriteHour = favouriteHour,
            loveYous = loveYous,
            topEmojis = emojis.entries.sortedByDescending { it.value }.take(3).map { EmojiCount(display(it.key), it.value) },
            myEmoji = myEmojis.maxByOrNull { it.value }?.key?.let(::display),
            partnerEmoji = theirEmojis.maxByOrNull { it.value }?.key?.let(::display),
            photos = photos,
            videos = videos,
            voiceNotes = voiceNotes,
            photoPreviews = pickPreviews(photoPreviews),
            daysWeTalked = talked,
            longestStreak = longest,
            cutestText = chosen?.text?.trim(),
            cutestIsMine = chosen?.senderId == myId,
            cutestAt = chosen?.timestamp ?: 0L
        )
    }

    private fun HashMap<String, Int>.bump(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    // "I love you", "love u", "luv you", "ily"
    private val LOVE_YOU = Regex("""\b(?:i\s*)?(?:lo+ve?|luv)\s*(?:yo+u+|u+|ya)\b|\bily\b""", RegexOption.IGNORE_CASE)
    private val LINK = Regex("""https?://|www\.""", RegexOption.IGNORE_CASE)
    private val CUTE_WORDS = listOf(
        "love", "miss you", "miss u", "my heart", "forever", "cute", "sweet", "baby", "babe", "darling",
        "honey", "beautiful", "handsome", "hug", "kiss", "together", "dream", "proud of you", "lucky",
        "precious", "my world", "soulmate", "thank you for", "always yours", "only you"
    )
    private val CUTE_EMOJI = setOf(
        "❤", "♥", "💕", "💖", "💗", "💓", "💞",
        "💘", "💝", "😘", "🥰", "😍", "🤗",
        "😚", "💋", "🫶", "💌", "🌹", "😊", "☺"
    )

    /** How sweet a message is: love words, hearts, a star, the other one's reactions. 0 = not at all. */
    private fun cuteScore(message: Message): Double {
        val text = message.text.trim()
        if (text.length < 6 || text.length > 400 || LINK.containsMatchIn(text)) return 0.0
        val lower = text.lowercase(Locale.getDefault())
        var score = 0.0
        CUTE_WORDS.forEach { if (lower.contains(it)) score += 2.0 }
        score += LOVE_YOU.findAll(text).count() * 3.0
        var hearts = 0
        forEachEmoji(text) { if (it in CUTE_EMOJI) hearts++ }
        score += minOf(hearts, 4) * 1.5
        if (score == 0.0 && !message.isStarred) return 0.0
        if (message.isStarred) score += 4.0
        if (message.reactions.keys.any { it != message.senderId }) score += 2.0
        message.reactions.values.forEach { reaction ->
            forEachEmoji(reaction) { if (it in CUTE_EMOJI) score += 1.5 }
        }
        score += minOf(text.length, 160) / 60.0
        return score
    }

    /** Each emoji in [text] (whole sequences: skin tones, couples, flags), without the variation selector. */
    private inline fun forEachEmoji(text: String, action: (String) -> Unit) {
        if (text.isEmpty()) return
        val breaks = BreakIterator.getCharacterInstance()
        breaks.setText(text)
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            val cluster = text.substring(start, end)
            if (isEmoji(cluster.codePointAt(0))) action(cluster.replace("️", ""))
            start = end
            end = breaks.next()
        }
    }

    private fun isEmoji(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF || codePoint in 0x2300..0x23FF ||
            codePoint == 0x2B50 || codePoint == 0x2B55 || codePoint == 0x2B06 || codePoint == 0x2B07

    /** Single-character symbols like the heart need the emoji style asked for. */
    private fun display(emoji: String): String = if (emoji.length == 1) emoji + "️" else emoji

    /** Up to four photos: the starred and reacted ones first, the rest spread over the month. */
    private fun pickPreviews(items: List<Pair<String, Int>>): List<String> {
        val usable = items.filter { (url, _) ->
            url.isNotBlank() && !url.startsWith("content://") && !url.startsWith("file:") && !url.startsWith("/") &&
                (!url.startsWith("data:") || url.length < 60_000)
        }
        if (usable.size <= 4) return usable.map { it.first }
        val picked = sortedSetOf<Int>()
        usable.indices.filter { usable[it].second > 0 }.sortedByDescending { usable[it].second }.take(4).forEach { picked += it }
        val step = usable.size / 4.0
        var i = 0
        while (picked.size < 4 && i < 4) {
            picked += (i * step).toInt()
            i++
        }
        var j = 0
        while (picked.size < 4) picked += j++
        return picked.map { usable[it].first }
    }

    private fun toJson(recap: MonthRecap): String = JSONObject().apply {
        put("v", VERSION)
        put("year", recap.year)
        put("month", recap.month)
        put("total", recap.totalMessages)
        put("mine", recap.mine)
        put("theirs", recap.theirs)
        put("busiestDay", recap.busiestDay)
        put("busiestDayCount", recap.busiestDayCount)
        put("favouriteHour", recap.favouriteHour)
        put("loveYous", recap.loveYous)
        put("topEmojis", JSONArray().apply {
            recap.topEmojis.forEach { put(JSONObject().put("e", it.emoji).put("n", it.count)) }
        })
        recap.myEmoji?.let { put("myEmoji", it) }
        recap.partnerEmoji?.let { put("partnerEmoji", it) }
        put("photos", recap.photos)
        put("videos", recap.videos)
        put("voiceNotes", recap.voiceNotes)
        put("previews", JSONArray(recap.photoPreviews))
        put("talked", JSONArray(recap.daysWeTalked))
        put("longestStreak", recap.longestStreak)
        recap.cutestText?.let { put("cutestText", it) }
        put("cutestIsMine", recap.cutestIsMine)
        put("cutestAt", recap.cutestAt)
    }.toString()

    private fun fromJson(json: String): MonthRecap? = try {
        val o = JSONObject(json)
        if (o.optInt("v") != VERSION) {
            null
        } else {
            val emojis = o.getJSONArray("topEmojis")
            val previews = o.getJSONArray("previews")
            val talked = o.getJSONArray("talked")
            MonthRecap(
                year = o.getInt("year"),
                month = o.getInt("month"),
                totalMessages = o.getInt("total"),
                mine = o.getInt("mine"),
                theirs = o.getInt("theirs"),
                busiestDay = o.getInt("busiestDay"),
                busiestDayCount = o.getInt("busiestDayCount"),
                favouriteHour = o.getInt("favouriteHour"),
                loveYous = o.getInt("loveYous"),
                topEmojis = List(emojis.length()) { i ->
                    emojis.getJSONObject(i).let { EmojiCount(it.getString("e"), it.getInt("n")) }
                },
                myEmoji = o.optString("myEmoji").ifBlank { null },
                partnerEmoji = o.optString("partnerEmoji").ifBlank { null },
                photos = o.getInt("photos"),
                videos = o.getInt("videos"),
                voiceNotes = o.getInt("voiceNotes"),
                photoPreviews = List(previews.length()) { previews.getString(it) },
                daysWeTalked = List(talked.length()) { talked.getBoolean(it) },
                longestStreak = o.getInt("longestStreak"),
                cutestText = o.optString("cutestText").ifBlank { null },
                cutestIsMine = o.getBoolean("cutestIsMine"),
                cutestAt = o.getLong("cutestAt")
            )
        }
    } catch (_: Exception) {
        null
    }
}
