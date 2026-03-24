package com.metrolist.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.core.net.toUri
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val isAndroid12OrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
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

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Open supported links
        Text(
            text = stringResource(R.string.open_supported_links),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    if (isAndroid12OrLater) {
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
                    } else {
                        Toast.makeText(
                            context,
                            R.string.intent_supported_links_not_found,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. FAQ
        Text(
            text = stringResource(R.string.faq),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://fabtune-music.blogspot.com/p/faqs.html")
                    }
                    context.startActivity(intent)
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Open-source credits
        Text(
            text = "Open-source credits",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    navController.navigate("open_source_credits")
                }
        )
    }

    TopAppBar(
        title = { Text("About") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(com.metrolist.music.R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
