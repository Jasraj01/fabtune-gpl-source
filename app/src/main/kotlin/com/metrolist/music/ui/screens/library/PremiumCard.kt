package com.metrolist.music.ui.screens.library


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.R

@Composable
fun PremiumCard(
    navController: NavController,
    price: String
) {
    Column(
        modifier = Modifier
            // Outer horizontal padding
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .background(Color(0xFFEDE9E6), RoundedCornerShape(20.dp))
            // Inner padding for content
            .padding(16.dp)
    ) {
        // Image section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Gray), // fallback color
            contentAlignment = Alignment.Center
        ) {
//            Image(
//                painter = painterResource(id = R.drawable.group8),
//                contentDescription = "Premium Image",
//                contentScale = ContentScale.Crop,
//                modifier = Modifier.fillMaxSize()
//            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FeatureRow(iconResId = R.drawable.ic_ad_free, stringResource(id = R.string.ad_free),)
        FeatureRow(iconResId = R.drawable.ic_unlimited_downloads, stringResource(id = R.string.unl_downloads))
        FeatureRow(iconResId = R.drawable.ic_dynamic_theme, stringResource(id = R.string.unlock_theme))

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { navController.navigate("premium") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id =
                R.string.get_premium,
                price), color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = R.string.supp_dev),
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun FeatureRow(
    iconResId: Int,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 16.dp)
            .padding(vertical = 6.dp)
    ) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            modifier = Modifier.size(25.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = Color.Black, fontSize = 16.sp)
    }
}