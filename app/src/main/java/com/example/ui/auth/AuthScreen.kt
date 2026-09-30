package com.example.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var myUsername by remember { mutableStateOf("faisal") }
    var password by remember { mutableStateOf("cherish123") }
    var partnerUsername by remember { mutableStateOf("shali") }
    var couplePasscode by remember { mutableStateOf("faisal-shali") }
    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    fun triggerGoogleSignIn() {
        coroutineScope.launch {
            try {
                isGoogleSigningIn = true
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("589800064404-tggb6b8jqfrm6vo9p0skl6f2fr23hle4.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    viewModel.loginWithGoogle(
                        idToken = idToken,
                        partnerUsernameOrEmail = partnerUsername,
                        coupleKey = couplePasscode
                    )
                } else {
                    Toast.makeText(context, "Unexpected credential returned", Toast.LENGTH_SHORT).show()
                }
            } catch (e: GetCredentialCancellationException) {
                // User cancelled the prompt
            } catch (e: Exception) {
                Toast.makeText(context, "Google Sign-In: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isGoogleSigningIn = false
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onAuthSuccess()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.White
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Centered container optimized for all mobile screens
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo & Brand Header
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .appGradientShadow(CircleShape)
                            .clip(CircleShape)
                            .background(appHorizontalGradient()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Cherish Heart Logo",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Cherish",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Strictly 2-Person Private Couple Sanctuary",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Card Container
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SoftPinkSurfaceVariant)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = RoseGoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Account Sign-In",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = DarkOnBackground
                                    )
                                    Text(
                                        text = "Private couple chat with your partner",
                                        fontSize = 11.sp,
                                        color = DarkOnSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))



                            // Your Username
                            OutlinedTextField(
                                value = myUsername,
                                onValueChange = { myUsername = it },
                                label = { Text("Your Username") },
                                placeholder = { Text("e.g. faisal") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = RoseGoldPrimary)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_username")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Password
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = RoseGoldPrimary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle password"
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_password")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Quick Swap Button for 2nd Phone
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Connecting 2 phones?",
                                    fontSize = 12.sp,
                                    color = DarkOnSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        val temp = myUsername
                                        myUsername = partnerUsername
                                        partnerUsername = temp
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = null,
                                        tint = RoseGoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "⇄ Swap for 2nd Phone",
                                        fontSize = 12.sp,
                                        color = RoseGoldPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Partner's Username
                            OutlinedTextField(
                                value = partnerUsername,
                                onValueChange = { partnerUsername = it },
                                label = { Text("Partner's Username") },
                                placeholder = { Text("e.g. karthik") },
                                supportingText = { Text("Only this partner connects to your space") },
                                leadingIcon = {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = HeartRed)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_partner_username")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Couple Passcode / Room Code
                            OutlinedTextField(
                                value = couplePasscode,
                                onValueChange = { couplePasscode = it },
                                label = { Text("Couple Secret Passcode") },
                                placeholder = { Text("cherish-love") },
                                supportingText = { Text("Shared passcode between you two") },
                                leadingIcon = {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = RoseGoldPrimary)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_couple_passcode")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 2-Phone Pairing Info Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF9F0F3))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "💡 How to connect both phones:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkOnBackground
                                    )
                                    Text(
                                        text = "• Phone 1: You: ${myUsername.ifBlank { "User A" }} | Partner: ${partnerUsername.ifBlank { "User B" }}\n• Phone 2: You: ${partnerUsername.ifBlank { "User B" }} | Partner: ${myUsername.ifBlank { "User A" }}",
                                        fontSize = 11.sp,
                                        color = DarkOnSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            if (uiState is AuthUiState.Error) {
                                val errorMsg = (uiState as AuthUiState.Error).message
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .background(Color(0xFFFFF0F2), RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = errorMsg,
                                        color = HeartRed,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            viewModel.continueOffline(
                                                username = myUsername,
                                                partnerUsername = partnerUsername,
                                                coupleKey = couplePasscode
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Enter in Direct / Offline Mode",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            // Enter / Sign In Native Button
                            Button(
                                onClick = {
                                    viewModel.login(
                                        username = myUsername,
                                        pass = password,
                                        partnerUsername = partnerUsername,
                                        coupleKey = couplePasscode
                                    )
                                },
                                enabled = uiState !is AuthUiState.Loading,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoseGoldPrimary,
                                    disabledContainerColor = RoseGoldPrimary.copy(alpha = 0.6f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("auth_submit_button")
                            ) {
                                if (uiState is AuthUiState.Loading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Enter Our Space",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = {
                                    viewModel.continueOffline(
                                        username = myUsername,
                                        partnerUsername = partnerUsername,
                                        coupleKey = couplePasscode
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Or Skip & Enter in Direct / Offline Mode",
                                    color = RoseGoldPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Strict 2-Person Security Guarantee Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SoftPinkSurfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = RoseGoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Strictly 2-Person Chat: No third party can ever enter",
                            fontSize = 12.sp,
                            color = DarkOnBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun GoogleBrandIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFE2E4E9)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = Color(0xFF4285F4)
        )
    }
}
