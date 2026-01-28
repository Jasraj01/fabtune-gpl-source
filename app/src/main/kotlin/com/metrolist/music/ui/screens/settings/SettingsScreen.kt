package com.metrolist.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.screens.PromoBanner
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {

    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var isSubscribed by remember { mutableStateOf(false) }
    var subscriptionPlan by remember { mutableStateOf<String?>(null) }

    // In your SettingsScreen callback:
    LaunchedEffect(Unit) {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo) {
                if (customerInfo.entitlements.active.containsKey("premium")) {
                    isSubscribed = true
                    val activeSubs = customerInfo.activeSubscriptions
                    println("Active subscriptions: $activeSubs") // For debugging
                    // Mapping logic: Convert the identifier to lowercase and check for substrings
                    subscriptionPlan = when {
                        activeSubs.any { it.contains("lifetime") } -> "lifetime"
                        activeSubs.any { it.contains("monthly") } -> "monthly"
                        else -> {
                            println("Unexpected subscription identifier: $activeSubs")
                            "subscribed"
                        }
                    }
                } else {
                    isSubscribed = false
                    subscriptionPlan = null
                }
            }

            override fun onError(error: PurchasesError) {
                isSubscribed = false
                subscriptionPlan = null
            }
        })
    }



    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

//            Spacer(Modifier.height(5.dp))

        if (isSubscribed && subscriptionPlan != null) {
            PremiumCardSettings(subscriptionPlan = subscriptionPlan!!)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!isSubscribed) {
            Material3SettingsGroup(
                title = stringResource(R.string.settings_section_premium),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.crownpro), // Note: You need to add this icon
                        title = { Text(stringResource(R.string.upgrade_to_premium)) },
                        onClick = { navController.navigate("premium") } // Change "premium" to your actual route
                    ),
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // User Interface Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_ui),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.appearance)) },
                    onClick = { navController.navigate("settings/appearance") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Player & Content Section (moved up and combined with content)
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text(stringResource(R.string.player_and_audio)) },
                    onClick = { navController.navigate("settings/player") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content)) },
                    onClick = { navController.navigate("settings/content") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy & Security Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_privacy),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text(stringResource(R.string.privacy)) },
                    onClick = { navController.navigate("settings/privacy") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Storage & Data Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_storage),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text(stringResource(R.string.storage)) },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.restore),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    onClick = { navController.navigate("settings/backup_restore") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System & About Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_system),
            items = buildList {
                if (isAndroid12OrLater) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.link),
                            title = { Text(stringResource(R.string.default_links)) },
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    when (e) {
                                        is ActivityNotFoundException -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        is SecurityException -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        else -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        )
                    )
                }
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.baseline_mail_outline_24),
                        title = { Text(stringResource(R.string.contact)) },
                        onClick = {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:fabtune.fabbl@gmail.com")
                            }
                            context.startActivity(emailIntent)
                        }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.telegram),
                        title = { Text(stringResource(R.string.Telegramchanel)) },
                        onClick = {
                            // Build and launch an ACTION_VIEW intent for your FAQ URL
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data =
                                    Uri.parse("https://t.me/fabtune")
                            }
                            context.startActivity(intent)
                        }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text(stringResource(R.string.faq)) },
                        onClick = {
                            // Build and launch an ACTION_VIEW intent for your FAQ URL
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data =
                                    Uri.parse("https://fabtune-music.blogspot.com/p/faqs.html")
                            }
                            context.startActivity(intent)
                        }
                    )
                )
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun PremiumCardSettings(subscriptionPlan: String) {
    // Determine if current theme is dark by checking luminance.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Card background is chosen based on theme.
    val cardBackground = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        Color(0xFFEFEDF1)
    }
    val titleTextColor = if (isDark) Color.White else Color.Black
    val subtitleTextColor = if (isDark) Color(0xFFA8A8A8) else Color.DarkGray

    // Logic for the pill text (e.g., "LIFETIME")
    val pillText = when (subscriptionPlan.lowercase()) {
        "lifetime" -> "LIFETIME"
        "monthly" -> "MONTHLY"
        else -> subscriptionPlan.uppercase()
    }

    // NEW: Dynamic subtitle text based on the subscription plan
    val subtitleText = when (subscriptionPlan.lowercase()) {
        "lifetime" -> stringResource(R.string.premium_subtitle_lifetime)
        "monthly" -> stringResource(R.string.premium_subtitle_monthly)
        else -> stringResource(R.string.premium_subtitle_generic)
    }

    // Set pill colors dynamically
    val pillBackground = if (isDark) Color.White else Color.Black
    val pillTextColor = if (isDark) Color.Black else Color.White

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Top row with "Premium" (bold) and the pill button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Premium",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = titleTextColor
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pillBackground)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = pillText,
                        color = pillTextColor,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // UPDATED: Subtitle now uses the new dynamic text
            Text(
                text = subtitleText,
                color = subtitleTextColor,
                fontSize = 14.sp
            )
        }
    }
}