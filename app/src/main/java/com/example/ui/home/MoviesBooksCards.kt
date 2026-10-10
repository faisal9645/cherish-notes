package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookItem
import com.example.data.model.MovieItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The two of us, for naming and colouring who did what (her pink, him blue, as on the couple card). */
class CouplePeople(
    val myId: String,
    val partnerId: String,
    val myName: String,
    val partnerName: String,
    val herId: String
) {
    fun nameOf(userId: String): String = if (userId == myId) "You" else partnerName
    fun colorOf(userId: String): Color = if (userId == herId) HeartbeatPink else HeartbeatBlue
}

private fun shortDate(millis: Long): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

/** The two tabs at the top of the movies and books cards. */
@Composable
private fun TwoTabs(first: String, second: String, selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(first, second).forEachIndexed { index, label ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected == index) accent else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) }
            ) {
                Text(
                    text = label,
                    color = if (selected == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CardHeader(emoji: String, title: String, subtitle: String, addLabel: String, addTag: String, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(38.dp).testTag(addTag)) {
            Icon(Icons.Default.Add, contentDescription = addLabel)
        }
    }
}

/** A small "who / when" chip. */
@Composable
private fun PersonChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text = text,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ConfirmDelete(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove “$what”?") },
        text = { Text("It's removed for both of you.") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } }
    )
}

// ---------------------------------------------------------------- Movies

/** Our movies: what we watched (and who watched it first and second) and what's still to watch. */
@Composable
fun CoupleMoviesCard(
    movies: List<MovieItem>,
    people: CouplePeople,
    onToggleWatched: (String) -> Unit,
    onDeleteMovie: (String) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MovieItem?>(null) }
    val watched = remember(movies) { movies.filter { it.isWatched }.sortedByDescending { it.watchedAt.values.maxOrNull() ?: 0L } }
    val toWatch = remember(movies) { movies.filter { !it.isWatched } }
    val shown = if (tab == 0) watched else toWatch

    Card(
        modifier = modifier.fillMaxWidth().testTag("couple_movies_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            CardHeader(
                emoji = "🍿",
                title = "Our Movies",
                subtitle = "${watched.size} watched • ${toWatch.size} to watch",
                addLabel = "Add a movie",
                addTag = "add_movie_button",
                onAdd = onAddNew
            )
            Spacer(modifier = Modifier.height(14.dp))
            TwoTabs("Watched (${watched.size})", "To watch (${toWatch.size})", tab) {
                tab = it
                showAll = false
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (shown.isEmpty()) {
                Text(
                    text = if (tab == 0) "Nothing watched yet. Mark one as watched from “To watch”." else "Add a movie you'd both like to see 🎬",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    (if (showAll) shown else shown.take(5)).forEach { movie ->
                        MovieRow(
                            movie = movie,
                            people = people,
                            onToggleWatched = { onToggleWatched(movie.id) },
                            onDelete = { deleting = movie }
                        )
                    }
                }
                if (shown.size > 5) {
                    TextButton(onClick = { showAll = !showAll }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (showAll) "Show less" else "Show all ${shown.size}")
                    }
                }
            }
        }
    }

    deleting?.let { movie ->
        ConfirmDelete(movie.title, onConfirm = { onDeleteMovie(movie.id) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun MovieRow(movie: MovieItem, people: CouplePeople, onToggleWatched: () -> Unit, onDelete: () -> Unit) {
    val iWatched = people.myId in movie.watchedAt
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().testTag("movie_${movie.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(movie.emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        movie.genre.ifBlank { null },
                        if (movie.addedBy.isNotBlank()) "added by ${people.nameOf(movie.addedBy)}" else null
                    ).joinToString(" • "),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Who watched it first and second
                if (movie.isWatched) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (movie.watchedTogether) {
                            PersonChip("💞 Watched together • ${shortDate(movie.watchedAt.values.min())}", MaterialTheme.colorScheme.primary)
                        } else {
                            movie.watchOrder.take(2).forEachIndexed { index, userId ->
                                val medal = if (index == 0) "🥇 1st" else "🥈 2nd"
                                PersonChip("$medal ${people.nameOf(userId)} • ${shortDate(movie.watchedAt.getValue(userId))}", people.colorOf(userId))
                            }
                        }
                    }
                }
                if (movie.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "“${movie.notes}”",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            // My own watched mark
            FilledTonalIconButton(
                onClick = onToggleWatched,
                modifier = Modifier.size(36.dp).testTag("movie_watched_${movie.id}"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (iWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = if (iWatched) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = if (iWatched) "I watched it (tap to undo)" else "I watched it", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp).testTag("movie_delete_${movie.id}")) {
                Icon(Icons.Default.Close, contentDescription = "Remove movie", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A few well-loved movies to start the list with. */
private val MovieIdeas = listOf(
    Triple("About Time", "Romance", "⏳"),
    Triple("Before Sunrise", "Romance", "🚂"),
    Triple("La La Land", "Musical", "🌆"),
    Triple("The Notebook", "Drama", "💌"),
    Triple("Your Name", "Anime", "✨"),
    Triple("Pride & Prejudice", "Romance", "🌿"),
    Triple("The Holiday", "RomCom", "🏡"),
    Triple("Crazy Rich Asians", "RomCom", "💍")
)

@Composable
fun AddMovieDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, genre: String, emoji: String, watchedTogether: Boolean, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Romance") }
    var emoji by remember { mutableStateOf("🍿") }
    var watchedTogether by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    val genres = listOf("Romance", "RomCom", "Drama", "Musical", "Anime", "Comedy", "Thriller", "Adventure")
    val emojis = listOf("🍿", "🎬", "🌆", "⏳", "💌", "✨", "🚂", "💍", "☕")
    val accent = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a movie 🍿", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ideas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MovieIdeas) { (ideaTitle, ideaGenre, ideaEmoji) ->
                        AssistChip(
                            onClick = {
                                title = ideaTitle
                                genre = ideaGenre
                                emoji = ideaEmoji
                            },
                            label = { Text("$ideaEmoji $ideaTitle", fontSize = 12.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Movie title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_movie_title")
                )
                Text("Genre", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(genres) { g ->
                        FilterChip(
                            selected = genre == g,
                            onClick = { genre = g },
                            label = { Text(g, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(emojis) { e ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (emoji == e) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { watchedTogether = !watchedTogether }
                ) {
                    Checkbox(checked = watchedTogether, onCheckedChange = { watchedTogether = it })
                    Text("We already watched it together", fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(140) },
                    label = { Text("Note (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(title, genre, emoji, watchedTogether, notes)
                    onDismiss()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("add_movie_save")
            ) { Text("Add movie") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- Books

/** Our books: who's reading what right now, each one's page, who finished first, and what's next. */
@Composable
fun CoupleBooksCard(
    books: List<BookItem>,
    people: CouplePeople,
    onUpdateMyPage: (id: String, page: Int) -> Unit,
    onDeleteBook: (String) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BookItem?>(null) }
    var deleting by remember { mutableStateOf<BookItem?>(null) }
    val started = remember(books) { books.filter { it.isStarted } }
    val toRead = remember(books) { books.filter { !it.isStarted } }
    val shown = if (tab == 0) started else toRead
    val myFinished = books.count { it.isFinishedBy(people.myId) }
    val partnerFinished = if (people.partnerId.isBlank()) 0 else books.count { it.isFinishedBy(people.partnerId) }
    val myReading = books.firstOrNull { it.isReadingBy(people.myId) }
    val partnerReading = if (people.partnerId.isBlank()) null else books.firstOrNull { it.isReadingBy(people.partnerId) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("couple_books_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            CardHeader(
                emoji = "📚",
                title = "Our Books",
                subtitle = "${books.size} books • you finished $myFinished • ${people.partnerName} finished $partnerFinished",
                addLabel = "Add a book",
                addTag = "add_book_button",
                onAdd = onAddNew
            )

            // Who's reading what right now
            if (myReading != null || partnerReading != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Reading now", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    listOfNotNull(
                        myReading?.let { people.myId to it },
                        partnerReading?.let { people.partnerId to it }
                    ).forEach { (userId, book) ->
                        Text(
                            text = "${people.nameOf(userId)}: ${book.emoji} ${book.title} • p. ${book.pageOf(userId)} of ${book.totalPages}",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            TwoTabs("Reading & read (${started.size})", "To read (${toRead.size})", tab) {
                tab = it
                showAll = false
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (shown.isEmpty()) {
                Text(
                    text = if (tab == 0) "No one has started a book yet. Pick one from “To read” and set your page." else "Add a book you'd like to read 📚",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    (if (showAll) shown else shown.take(5)).forEach { book ->
                        BookRow(book = book, people = people, onEditPage = { editing = book }, onDelete = { deleting = book })
                    }
                }
                if (shown.size > 5) {
                    TextButton(onClick = { showAll = !showAll }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (showAll) "Show less" else "Show all ${shown.size}")
                    }
                }
            }
        }
    }

    editing?.let { book ->
        MyPageDialog(
            book = book,
            people = people,
            onDismiss = { editing = null },
            onSave = { page ->
                onUpdateMyPage(book.id, page)
                editing = null
            }
        )
    }
    deleting?.let { book ->
        ConfirmDelete(book.title, onConfirm = { onDeleteBook(book.id) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun BookRow(book: BookItem, people: CouplePeople, onEditPage: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().testTag("book_${book.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) { Text(book.emoji, fontSize = 22.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(book.author.ifBlank { null }, "${book.totalPages} pages", book.genre.ifBlank { null }).joinToString(" • "),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalButton(
                    onClick = onEditPage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("book_my_page_${book.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("My page", fontSize = 12.sp)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp).testTag("book_delete_${book.id}")) {
                    Icon(Icons.Default.Close, contentDescription = "Remove book", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            // Each of us: page and progress (only shown once someone has started)
            if (book.isStarted) {
                Spacer(modifier = Modifier.height(8.dp))
                listOfNotNull(people.myId, people.partnerId.ifBlank { null }).forEach { userId ->
                    val page = book.pageOf(userId)
                    val color = people.colorOf(userId)
                    val order = book.finishOrder.indexOf(userId)
                    val status = when {
                        order == 0 -> "🥇 Finished first • ${shortDate(book.finishedAt.getValue(userId))}"
                        order == 1 -> "🥈 Finished second • ${shortDate(book.finishedAt.getValue(userId))}"
                        page > 0 -> "Reading • p. $page of ${book.totalPages}"
                        else -> "Not started"
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(people.nameOf(userId), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.width(64.dp), maxLines = 1)
                        Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                    LinearProgressIndicator(
                        progress = { (page.toFloat() / book.totalPages.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        color = color,
                        trackColor = color.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp, bottom = 6.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
            if (book.notes.isNotBlank()) {
                Text(
                    text = "“${book.notes}”",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Sets the page I'm on (the partner sets theirs on their own phone). */
@Composable
private fun MyPageDialog(book: BookItem, people: CouplePeople, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var page by remember { mutableIntStateOf(book.pageOf(people.myId)) }
    var pageText by remember { mutableStateOf(page.toString()) }
    fun setPage(value: Int) {
        page = value.coerceIn(0, book.totalPages)
        pageText = page.toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${book.emoji} ${book.title}", fontWeight = FontWeight.Bold, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("The page you're on (of ${book.totalPages})", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { setPage(page - 10) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("−10") }
                    OutlinedButton(onClick = { setPage(page - 1) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("−1") }
                    OutlinedTextField(
                        value = pageText,
                        onValueChange = { text ->
                            pageText = text.filter { it.isDigit() }.take(5)
                            pageText.toIntOrNull()?.let { page = it.coerceIn(0, book.totalPages) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f).testTag("book_page_input")
                    )
                    OutlinedButton(onClick = { setPage(page + 1) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+1") }
                    OutlinedButton(onClick = { setPage(page + 10) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+10") }
                }
                Slider(
                    value = page.toFloat(),
                    onValueChange = { setPage(it.toInt()) },
                    valueRange = 0f..book.totalPages.toFloat().coerceAtLeast(1f)
                )
                TextButton(onClick = { setPage(book.totalPages) }) { Text("I finished it 🎉") }
                if (people.partnerId.isNotBlank()) {
                    val theirs = book.pageOf(people.partnerId)
                    Text(
                        text = if (book.isFinishedBy(people.partnerId)) "${people.partnerName} finished it" else "${people.partnerName} is on page $theirs",
                        fontSize = 12.sp,
                        color = people.colorOf(people.partnerId)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(page) }, modifier = Modifier.testTag("book_page_save")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Some loved books to start with (page counts are for common editions, change them to yours). */
private class BookIdea(val title: String, val author: String, val pages: Int, val emoji: String)

private val BookIdeas = listOf(
    BookIdea("The Little Prince", "Antoine de Saint-Exupéry", 96, "🌹"),
    BookIdea("Pride and Prejudice", "Jane Austen", 432, "🌿"),
    BookIdea("The Alchemist", "Paulo Coelho", 208, "✨"),
    BookIdea("Normal People", "Sally Rooney", 273, "📖"),
    BookIdea("Before the Coffee Gets Cold", "Toshikazu Kawaguchi", 213, "☕"),
    BookIdea("Atomic Habits", "James Clear", 320, "⚡"),
    BookIdea("The Seven Husbands of Evelyn Hugo", "Taylor Jenkins Reid", 400, "💌"),
    BookIdea("It Ends with Us", "Colleen Hoover", 384, "🌸")
)

@Composable
fun AddBookDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, author: String, totalPages: Int, genre: String, emoji: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Fiction") }
    var emoji by remember { mutableStateOf("📖") }
    var notes by remember { mutableStateOf("") }
    val genres = listOf("Fiction", "Romance", "Classic", "Self-growth", "Mystery", "Fantasy", "Poetry")
    val accent = MaterialTheme.colorScheme.primary
    val pages = pagesText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a book 📚", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Suggestions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BookIdeas) { idea ->
                        AssistChip(
                            onClick = {
                                title = idea.title
                                author = idea.author
                                pagesText = idea.pages.toString()
                                emoji = idea.emoji
                            },
                            label = { Text("${idea.emoji} ${idea.title}", fontSize = 12.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Book title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_book_title")
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it.take(80) },
                    label = { Text("Author (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pagesText,
                    onValueChange = { pagesText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Number of pages") },
                    supportingText = { Text("From your copy (editions differ)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_book_pages")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(genres) { g ->
                        FilterChip(
                            selected = genre == g,
                            onClick = { genre = g },
                            label = { Text(g, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(140) },
                    label = { Text("Why read it? (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(title, author, pages, genre, emoji, notes)
                    onDismiss()
                },
                enabled = title.isNotBlank() && pages > 0,
                modifier = Modifier.testTag("add_book_save")
            ) { Text("Add book") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
