package com.metrolist.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.R

@Composable
fun PromoBanner(navController: NavController) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 7.dp)
    ) {
        // 1. Determine if the screen is small based on a width threshold.
        // You can adjust this value to your liking.
        val isSmallScreen = maxWidth < 380.dp

        // 2. Define adaptive font sizes for the title and subtitle.
        val titleFontSize = if (isSmallScreen) 12.sp else 14.sp
        val subtitleFontSize = if (isSmallScreen) 10.sp else 11.sp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.crownpro),
                    contentDescription = "Banner Image",
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.enjoy_title),
                        // 3. Apply the dynamic font size to the title Text.
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(R.string.unl_downloads),
                        // 4. Apply the dynamic font size to the subtitle Text.
                        fontSize = subtitleFontSize,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(1.dp))

                Button(
                    onClick = { navController.navigate("premium") },
                    modifier = Modifier.height(35.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.upgrade),
                        color = Color.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}