package com.example.ui.journey

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.YearlyJourneyEntry
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifetimeJourneyScreen(
    viewModel: LifetimeJourneyViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<YearlyJourneyEntry?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Our Lifetime Love",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Age by Age • Year by Year • Entire Life",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("journey_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("journey_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Add Year Memory",
                            tint = RoseGoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets.statusBars
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
        ) {
            // 1. Lifetime Age & Connection Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appGradientShadow(RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(appHorizontalGradient())
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "❤️",
                                    fontSize = 24.sp,
                                    modifier = Modifier.scale(pulseScale)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Our Lifetime Sanctuary",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Year ${uiState.totalYearsTogether} of Our Eternal Bond",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Ages",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Age breakdown chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Your Age Today",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "${uiState.myCurrentAge} Years",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Partner's Age Today",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    )
                                    Text(
                                        text = "${uiState.partnerCurrentAge} Years",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Our Sacred Secret Vow (Not Real Husband & Wife, But Soulmates For Entire Life)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditProfileDialog = true }
                        .testTag("secret_vow_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftPinkSurfaceVariant),
                    border = BorderStroke(1.dp, SoftBorderOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = RoseGoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Our Secret Lifelong Promise",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkOnBackground
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Vow",
                                tint = DarkOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "“${uiState.profile.secretVow}”",
                            fontSize = 13.sp,
                            color = DarkOnBackground,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Normal,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Section Header: Year-by-Year Story
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "How Our Love Evolves Each Year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkOnBackground
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SoftPinkSurfaceVariant,
                        modifier = Modifier.clickable { showAddDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Year",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoseGoldPrimary
                            )
                        }
                    }
                }
            }

            // 3. Year-by-Year Entries
            items(uiState.journeys, key = { it.id }) { journey ->
                YearlyJourneyCard(
                    entry = journey,
                    onEdit = { entryToEdit = journey },
                    onDelete = { viewModel.deleteYearlyJourney(journey.id) }
                )
            }

            // 4. Growing Old in Love — Lifetime Age Horizons
            item {
                LifetimeMilestonesCard()
            }
        }
    }

    // Dialog to Add a new year
    if (showAddDialog) {
        YearlyJourneyEditorDialog(
            entry = null,
            defaultYear = uiState.currentYear,
            defaultMyAge = uiState.myCurrentAge,
            defaultPartnerAge = uiState.partnerCurrentAge,
            onDismiss = { showAddDialog = false },
            onSave = { year, myAge, partnerAge, theme, places, enjoyed, memory, song, passion ->
                viewModel.addYearlyJourney(
                    year = year,
                    myAge = myAge,
                    partnerAge = partnerAge,
                    yearTheme = theme,
                    placesWent = places,
                    howWeEnjoyed = enjoyed,
                    specialMemory = memory,
                    songOrQuote = song,
                    passionRating = passion
                )
                showAddDialog = false
            }
        )
    }

    // Dialog to Edit existing year
    if (entryToEdit != null) {
        YearlyJourneyEditorDialog(
            entry = entryToEdit,
            defaultYear = entryToEdit!!.year,
            defaultMyAge = entryToEdit!!.myAge,
            defaultPartnerAge = entryToEdit!!.partnerAge,
            onDismiss = { entryToEdit = null },
            onSave = { year, myAge, partnerAge, theme, places, enjoyed, memory, song, passion ->
                val updated = entryToEdit!!.copy(
                    year = year,
                    myAge = myAge,
                    partnerAge = partnerAge,
                    yearTheme = theme,
                    placesWent = places,
                    howWeEnjoyed = enjoyed,
                    specialMemory = memory,
                    songOrQuote = song,
                    passionRating = passion
                )
                viewModel.updateYearlyJourney(updated)
                entryToEdit = null
            }
        )
    }

    // Dialog to Edit Profile & Birth Years
    if (showEditProfileDialog) {
        EditLifetimeProfileDialog(
            currentMyBirthYear = uiState.profile.myBirthYear,
            currentPartnerBirthYear = uiState.profile.partnerBirthYear,
            currentStartYear = uiState.profile.relationshipStartYear,
            currentVow = uiState.profile.secretVow,
            onDismiss = { showEditProfileDialog = false },
            onSave = { myBirth, partnerBirth, startYear, vow ->
                viewModel.updateProfile(myBirth, partnerBirth, startYear, vow)
                showEditProfileDialog = false
            }
        )
    }
}

@Composable
fun YearlyJourneyCard(
    entry: YearlyJourneyEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Year and Ages That Year
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${entry.year}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Ages ${entry.myAge} & ${entry.partnerAge}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkOnSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DarkOnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = HeartRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Year Theme
            Text(
                text = entry.yearTheme,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkOnBackground
            )

            // Passion rating hearts
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                repeat(entry.passionRating) {
                    Text(text = "❤️", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Where We Went
            if (entry.placesWent.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftPinkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Where we went",
                            tint = RoseGoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Where We Went & Rendezvous:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = entry.placesWent,
                                fontSize = 12.sp,
                                color = DarkOnSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // How We Enjoyed Everything
            if (entry.howWeEnjoyed.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "✨", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "How We Enjoyed Our Love:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOnBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.howWeEnjoyed,
                            fontSize = 12.sp,
                            color = DarkOnSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Special Memory Quote
            if (entry.specialMemory.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF9F9FB),
                    border = BorderStroke(1.dp, Color(0xFFEAEAEC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Unforgettable Moment:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseGoldPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "“${entry.specialMemory}”",
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = DarkOnBackground,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Song or Quote
            if (entry.songOrQuote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = RoseGoldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.songOrQuote,
                        fontSize = 11.sp,
                        color = DarkOnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun LifetimeMilestonesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = null,
                    tint = RoseGoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Growing Old Together in Secret Love",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkOnBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Our vision across every decade of our lives:",
                fontSize = 12.sp,
                color = DarkOnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            val decades = listOf(
                "In Our 30s" to "Fierce passion, stolen midnight escapes, breathless intimacy that defies the outside world.",
                "In Our 40s" to "Maturity, profound emotional shelter, knowing each other's soul without a single word.",
                "In Our 50s & Beyond" to "Silver hair, tender glances, quiet holding of hands with a lifetime of sacred memories that belong only to us."
            )

            decades.forEach { (ageSpan, description) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "🌟", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = ageSpan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOnBackground
                        )
                        Text(
                            text = description,
                            fontSize = 11.sp,
                            color = DarkOnSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YearlyJourneyEditorDialog(
    entry: YearlyJourneyEntry?,
    defaultYear: Int,
    defaultMyAge: Int,
    defaultPartnerAge: Int,
    onDismiss: () -> Unit,
    onSave: (
        year: Int,
        myAge: Int,
        partnerAge: Int,
        theme: String,
        places: String,
        enjoyed: String,
        memory: String,
        song: String,
        passion: Int
    ) -> Unit
) {
    var yearText by remember { mutableStateOf((entry?.year ?: defaultYear).toString()) }
    var myAgeText by remember { mutableStateOf((entry?.myAge ?: defaultMyAge).toString()) }
    var partnerAgeText by remember { mutableStateOf((entry?.partnerAge ?: defaultPartnerAge).toString()) }
    var theme by remember { mutableStateOf(entry?.yearTheme ?: "") }
    var places by remember { mutableStateOf(entry?.placesWent ?: "") }
    var enjoyed by remember { mutableStateOf(entry?.howWeEnjoyed ?: "") }
    var memory by remember { mutableStateOf(entry?.specialMemory ?: "") }
    var song by remember { mutableStateOf(entry?.songOrQuote ?: "") }
    var passionRating by remember { mutableIntStateOf(entry?.passionRating ?: 5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (entry == null) "Add Year Memory" else "Edit Year ${entry.year}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = yearText,
                                onValueChange = { yearText = it },
                                label = { Text("Year") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = myAgeText,
                                onValueChange = { myAgeText = it },
                                label = { Text("Your Age") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = partnerAgeText,
                                onValueChange = { partnerAgeText = it },
                                label = { Text("Partner Age") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = theme,
                            onValueChange = { theme = it },
                            label = { Text("Theme of the Year") },
                            placeholder = { Text("e.g. Soulmates Bound for Life") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = places,
                            onValueChange = { places = it },
                            label = { Text("Where We Went (Secret Escapes)") },
                            placeholder = { Text("e.g. Secret Hilltop Hotel, Lake Cabin") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = enjoyed,
                            onValueChange = { enjoyed = it },
                            label = { Text("How We Enjoyed Every Moment") },
                            placeholder = { Text("e.g. Laughing in secret, midnight drives, holding each other without fear") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = memory,
                            onValueChange = { memory = it },
                            label = { Text("Most Special Unforgettable Memory") },
                            placeholder = { Text("e.g. The night we made our secret promise under the stars") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = song,
                            onValueChange = { song = it },
                            label = { Text("Song or Secret Words of That Year") },
                            placeholder = { Text("e.g. Until I Found You • 'Always & Forever'") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Love Rating: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            (1..5).forEach { r ->
                                Text(
                                    text = if (r <= passionRating) "❤️" else "🤍",
                                    fontSize = 20.sp,
                                    modifier = Modifier
                                        .clickable { passionRating = r }
                                        .padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val year = yearText.toIntOrNull() ?: defaultYear
                    val myAge = myAgeText.toIntOrNull() ?: defaultMyAge
                    val partnerAge = partnerAgeText.toIntOrNull() ?: defaultPartnerAge
                    onSave(year, myAge, partnerAge, theme, places, enjoyed, memory, song, passionRating)
                }
            ) {
                Text("Save Year")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditLifetimeProfileDialog(
    currentMyBirthYear: Int,
    currentPartnerBirthYear: Int,
    currentStartYear: Int,
    currentVow: String,
    onDismiss: () -> Unit,
    onSave: (myBirthYear: Int, partnerBirthYear: Int, startYear: Int, vow: String) -> Unit
) {
    var myBirthText by remember { mutableStateOf(currentMyBirthYear.toString()) }
    var partnerBirthText by remember { mutableStateOf(currentPartnerBirthYear.toString()) }
    var startYearText by remember { mutableStateOf(currentStartYear.toString()) }
    var vowText by remember { mutableStateOf(currentVow) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Our Lifetime Profile & Vow", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = myBirthText,
                        onValueChange = { myBirthText = it },
                        label = { Text("Your Birth Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = partnerBirthText,
                        onValueChange = { partnerBirthText = it },
                        label = { Text("Partner Birth Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = startYearText,
                    onValueChange = { startYearText = it },
                    label = { Text("Year Our Secret Love Began") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vowText,
                    onValueChange = { vowText = it },
                    label = { Text("Our Sacred Secret Vow") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val myBirth = myBirthText.toIntOrNull() ?: currentMyBirthYear
                    val partnerBirth = partnerBirthText.toIntOrNull() ?: currentPartnerBirthYear
                    val start = startYearText.toIntOrNull() ?: currentStartYear
                    onSave(myBirth, partnerBirth, start, vowText)
                }
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


