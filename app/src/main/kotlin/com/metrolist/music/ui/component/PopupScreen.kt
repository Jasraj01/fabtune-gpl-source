package com.metrolist.music.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

@Composable
fun PopupScreen() {
    val showDialog = remember { mutableStateOf(false) }
    val title = remember { mutableStateOf("") }
    val description = remember { mutableStateOf("") }
    val buttonText = remember { mutableStateOf("") }
    val link = remember { mutableStateOf("") }

    val context = LocalContext.current

    // ✅ Optimized Firestore listener
    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val listener: ListenerRegistration = db.collection("popupScreen")
            .document("config")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val data = snapshot.data
                    showDialog.value = data?.get("show") as? Boolean ?: false
                    title.value = data?.get("title") as? String ?: ""
                    description.value = data?.get("description") as? String ?: ""
                    buttonText.value = data?.get("buttonText") as? String ?: "Open"
                    link.value = data?.get("link") as? String ?: ""
                }
            }

        onDispose {
            listener.remove() // ✅ avoids leaks
        }
    }

    if (showDialog.value) {
        Dialog(onDismissRequest = { showDialog.value = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = title.value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = description.value,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    Button(
                        onClick = {
                            if (link.value.isNotEmpty()) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.value))
                                context.startActivity(intent)
                            }
                            showDialog.value = false
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(buttonText.value)
                    }
                }
            }
        }
    }
}
