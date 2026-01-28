package com.metrolist.music.ui.reviewbox

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

@Composable
fun ReviewDialog(
    reviewDataStore: ReviewDataStore, // Pass in your DataStore instance
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    var selectedRating by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.elevatedCardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = "Enjoying the App?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Subtitle
                    Text(
                        text = "Keep the vibes flowing — rate us on Play Store.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    // Star Rating Row
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { rating ->
                            Icon(
                                imageVector = if (rating <= selectedRating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "Star $rating",
                                modifier = Modifier
                                    .size(50.dp)
                                    .clickable { selectedRating = rating }
                                    .padding(4.dp),
                                tint = if (rating <= selectedRating) Color(0xFFFFD700) else Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Not Now" button: call setDismissed() to store the current timestamp.
                        Button(
                            onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    reviewDataStore.setDismissed()
                                    withContext(Dispatchers.Main) {
                                        onDismiss()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Not Now", color = Color.Black, fontSize = 16.sp)
                        }
                        // "Submit" button: active only when a star is selected.
                        Button(
                            onClick = { onSubmit(selectedRating) },
                            shape = RoundedCornerShape(50),
                            enabled = selectedRating > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedRating > 0) Color(0xFF1E88E5) else Color(0xFFBDBDBD),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Submit", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}



fun handleReviewSubmission(context: Context, rating: Int, reviewDataStore: ReviewDataStore) {
    CoroutineScope(Dispatchers.IO).launch {
        reviewDataStore.setReviewed() // Store that user has submitted a review

        withContext(Dispatchers.Main) {
            if (rating >= 4) {
                // Open Play Store for 4 or 5 stars
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=com.fabtune.music.player".toUri()
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // Show thank you toast
                Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
