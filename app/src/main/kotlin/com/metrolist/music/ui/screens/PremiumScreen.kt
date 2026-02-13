package com.metrolist.music.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.metrolist.music.R
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import java.text.NumberFormat
import java.util.*
import androidx.core.net.toUri

// --- GRADIENTS AND MODIFIERS (UNCHANGED) ---
val bestDealGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFD4A017), Color(0xFFFDD835))
)

val screenGradient = Brush.radialGradient(
    colors = listOf(Color(0xFF070C1D), Color(0xFF030407)),
    center = Offset(0f, 0f),
    radius = 2000f
)

// OPTIMIZATION: Mark data classes as Immutable for stability guarantees.
@Immutable
data class UserReview(val username: String, val review: String)


// --- TOP-LEVEL COMPOSABLE ---
@Composable
fun PremiumScreen(
    navController: NavController,
) {
    // --- STATE MANAGEMENT ---


    val context = LocalContext.current
    val activity = context as? Activity
    var showRedeemDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var monthlyPackage by remember { mutableStateOf<Package?>(null) }
    var lifetimePackage by remember { mutableStateOf<Package?>(null) }
    var monthlyPrice by remember { mutableStateOf("Loading...") }
    var lifetimePrice by remember { mutableStateOf("Loading...") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedPlan by remember { mutableStateOf("Lifetime") }

    // --- LOGIC ---
    // Business logic is kept at the top level and passed as lambdas to UI components.
    val isConnected = remember {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    // Helper to format price. `remember` prevents recreating it on every recomposition.
    val formatPrice: (String, Long) -> String = remember {
        { currencyCode, micros ->
            val doublePrice = micros / 1_000_000.0
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                currency = Currency.getInstance(currencyCode)
            }
            format.format(doublePrice)
        }
    }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            snackbarHostState.showSnackbar("No internet connection!")
            monthlyPrice = "⚠"
            lifetimePrice = "⚠"
            return@LaunchedEffect
        }

        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                offerings.current?.let {
                    it.availablePackages.firstOrNull { pkg -> pkg.packageType == PackageType.MONTHLY }?.let { pkg ->
                        monthlyPackage = pkg
                        monthlyPrice = formatPrice(pkg.product.price.currencyCode, pkg.product.price.amountMicros)
                    }
                    it.availablePackages.firstOrNull { pkg -> pkg.packageType == PackageType.LIFETIME }?.let { pkg ->
                        lifetimePackage = pkg
                        lifetimePrice = formatPrice(pkg.product.price.currencyCode, pkg.product.price.amountMicros)
                    }
                }
            }

            override fun onError(error: PurchasesError) {
                Toast.makeText(context, "Error fetching offerings: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    val purchase: () -> Unit = {
        isLoading = true
        val selectedPkg = if (selectedPlan == "Monthly") monthlyPackage else lifetimePackage

        // MODIFIED: Replaced 'return' with an if-else block for proper control flow
        if (activity == null || selectedPkg == null) {
            // This is the "early exit" path
            Toast.makeText(context, "Plan not available, please try again.", Toast.LENGTH_SHORT).show()
            isLoading = false
        } else {

            val purchaseParams = PurchaseParams.Builder(activity, selectedPkg).build()
            // This is the main logic path, executed only if the condition is false
            Purchases.sharedInstance.purchase(purchaseParams, object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    isLoading = false
                    Toast.makeText(context, "Thank you for your purchase!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    isLoading = false
                    if (!userCancelled) {
                        Toast.makeText(context, "Purchase error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }

    val restore: () -> Unit = {
        // MODIFIED: Replaced 'return' with an if-else block
        if (!isConnected) {
            // This is the "early exit" path
            Toast.makeText(context, "No internet to restore purchases.", Toast.LENGTH_LONG).show()
        } else {
            // This is the main logic path
            isLoading = true
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    isLoading = false
                    if (customerInfo.entitlements.active.containsKey("premium")) {
                        Toast.makeText(context, "Purchase restored!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    } else {
                        Toast.makeText(context, "No purchases to restore.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onError(error: PurchasesError) {
                    isLoading = false
                    Toast.makeText(context, "Restore error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    // --- UI ---
    // The main UI is now a clean composition of smaller, specialized components.
    PremiumScreenContent(
        snackbarHostState = snackbarHostState,
        monthlyPrice = monthlyPrice,
        lifetimePrice = lifetimePrice,
        selectedPlan = selectedPlan,
        isLoading = isLoading,
        onPlanSelected = { selectedPlan = it },
        onPurchaseClick = purchase,
        onRestoreClick = restore,
        onCloseClick = { navController.popBackStack() },
        onRedeemClick = { showRedeemDialog = true }
    )

    if (showRedeemDialog) {
        RedeemInfoDialog(
            onDismissRequest = { showRedeemDialog = false },
            onConfirm = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, "https://play.google.com/redeem".toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Unable to open redeem page.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

}



// --- DECOMPOSED UI COMPONENTS ---

@Composable
private fun PremiumScreenContent(
    snackbarHostState: SnackbarHostState,
    monthlyPrice: String,
    lifetimePrice: String,
    selectedPlan: String,
    isLoading: Boolean,
    onPlanSelected: (String) -> Unit,
    onPurchaseClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onCloseClick: () -> Unit,
    onRedeemClick: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data ->
            Snackbar(snackbarData = data, containerColor = Color.DarkGray, contentColor = Color.White)
        }},
        containerColor = Color.Transparent,
        contentColor = Color.White
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenGradient)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 60.dp) // Space for the floating footer
            ) {
                PremiumHeader(
                    onRestoreClick = onRestoreClick,
                    onCloseClick = onCloseClick
                )
                MainContent(
                    monthlyPrice = monthlyPrice,
                    lifetimePrice = lifetimePrice,
                    selectedPlan = selectedPlan,
                    isPurchaseEnabled = !isLoading && (monthlyPrice != "Loading..." || lifetimePrice != "Loading..."),
                    onPlanSelected = onPlanSelected,
                    onPurchaseClick = onPurchaseClick,
                    onRedeemClick = onRedeemClick
                )
            }

            Footer(modifier = Modifier.align(Alignment.BottomCenter))

            if (isLoading) {
                LoadingOverlay()
            }
        }
    }
}

@Composable
private fun PremiumHeader(onRestoreClick: () -> Unit, onCloseClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.4f))
                .clickable(onClick = onRestoreClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(id = R.string.restore_p), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.4f))
                .clickable(onClick = onCloseClick)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.close),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_small_icon),
            contentDescription = "App Logo",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text("Premium", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MainContent(
    monthlyPrice: String,
    lifetimePrice: String,
    selectedPlan: String,
    isPurchaseEnabled: Boolean,
    onPlanSelected: (String) -> Unit,
    onPurchaseClick: () -> Unit,
    onRedeemClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlanSelector(
            lifetimePrice = lifetimePrice,
            monthlyPrice = monthlyPrice,
            selectedPlan = selectedPlan,
            onPlanSelected = onPlanSelected
        )


//        Text(
//            text = stringResource(id = R.string.improve_innertune),
//            color = Color(0xFFAAAAAA),
//            fontSize = 13.sp,
//            lineHeight = 18.sp,
//            textAlign = TextAlign.Center,
//            modifier = Modifier.padding(horizontal = 1.dp)
//        )

        Spacer(modifier = Modifier.height(12.dp))

        // OPTIMIZATION: This calculation only re-runs when `selectedPlan` changes.
        val buttonText by remember(selectedPlan) {
            derivedStateOf {
                when (selectedPlan) {
                    "Lifetime" -> "Unlock Lifetime Premium"
                    "Monthly" -> "Continue with Monthly"
                    else -> "Continue"
                }
            }
        }

        Button(
            onClick = onPurchaseClick,
            enabled = isPurchaseEnabled,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(buttonText, fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(11.dp))

        PromoSection(onRedeemClick = onRedeemClick)

        Spacer(modifier = Modifier.height(16.dp))

        FeaturesSection()

        Spacer(modifier = Modifier.height(15.dp))

        ReviewsSection()

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = stringResource(id = R.string.note_p),
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun PlanSelector(
    lifetimePrice: String,
    monthlyPrice: String,
    selectedPlan: String,
    onPlanSelected: (String) -> Unit
) {
    PlanCard(
        planType = "Lifetime",
        price = lifetimePrice,
        selected = (selectedPlan == "Lifetime"),
        onSelect = { onPlanSelected("Lifetime") }
    )
    Spacer(modifier = Modifier.height(16.dp))
    PlanCard(
        planType = "Monthly",
        price = monthlyPrice,
        selected = (selectedPlan == "Monthly"),
        onSelect = { onPlanSelected("Monthly") }
    )
}

@Composable
private fun PromoSection(onRedeemClick: () -> Unit) {
    // OPTIMIZATION: `buildAnnotatedString` is a calculation. Remembering it prevents
    // it from being rebuilt on every recomposition.
    val promoText = remember {
        buildAnnotatedString {
            append("Enter Promo Code\n")
            withStyle(style = SpanStyle(color = Color.Gray, fontSize = 12.sp)) {
                append("Previous Premium Users")
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = promoText,
            lineHeight = 18.sp,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 5.dp)
        )
        Button(
            onClick = onRedeemClick,
            modifier = Modifier.height(30.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E4E4E).copy(alpha = 0.38f)),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Text("Redeem", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun FeaturesSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF4E4E4E).copy(alpha = 0.38f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(
            stringResource(id = R.string.why_join_premium),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp, start = 2.dp)
        )
        BulletItem(iconResId = R.drawable.ic_ad_free, text = stringResource(id = R.string.ad_free))
        BulletItem(iconResId = R.drawable.ic_unlimited_downloads, text = stringResource(id = R.string.unl_downloads))
//        BulletItem(iconResId = R.drawable.application, text = stringResource(id = R.string.unlock_theme))
        BulletItem(iconResId = R.drawable.check_001, text = stringResource(id = R.string.support_developer))
    }
}

@Composable
private fun ReviewsSection() {
    // OPTIMIZATION: Use `remember` so this static list isn't recreated on every recomposition.
    val userReviews = remember {
        listOf(
            UserReview("Amao", "Literally I have just one word to define this app--- THE BEST OF THE BEST! It has everything you need. You can download most of the songs in literally just one or two seconds. Either way ARIGATOU GOJAIMASU!🙏"),
            UserReview("Punam Kumari", "Rarely I write reviews about anything...But the features of this app compelled me to write one..😊 U can listen to good quality 🎶 music with no adds unlike Spotify which sucks 😒.. Unlimited downloads..good suggestions..and a awesome 😎 built Equalizer what else u want in a music app free of cost..and the design are preety well...but can be improved (only if developer wants ;) )"),
            UserReview("Khadiji", "The BEST App to listen to music. It has all the features you'd want in a music app also the download songs feature is available!!!🤩 I really LOVE the 'enable dynamic theme' feature in which the colour of the interface changes as per the song we are listening to, it is so creative and awesome👌🏻💜 After a long search for a good music app I can now finally listen to music in peace with this app, thank you sooo much!!😇❤"),
            UserReview("DEVADATHAN", "I've been using fabtune for a while now, and I couldn't be happier with it! For a free music app, it offers an impressive selection of songs across all genres, from the latest hits to classic favorites. The audio quality is excellent, and the streaming experience is smooth with minimal ads. One of the best features is the ability to create and customize playlists easily. The app also has a great recommendation system that helps me"),
            UserReview("Irene Thomas", "Excellent App!! I love it, amazing sound quality. I was desperate about my previous music player, but this literally saved me- as a person who can't live without music!!"),
        )
    }
    ReviewCarousel(userReviews)
}

@Composable
private fun Footer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1D1B20), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.privacy_p),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW,
                        "https://fabtune-music.blogspot.com/p/privacy-policy.html".toUri())
                    context.startActivity(intent)
                }
            )
            Text("  •  ", color = Color.White, fontSize = 14.sp)
            Text(
                text = stringResource(R.string.terms_p),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://fabtune-music.blogspot.com/p/terms-and-conditions.html".toUri())
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false, onClick = {}), // Prevent clicks passing through
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}


// --- REUSABLE AND UNCHANGED COMPONENTS ---

@Composable
fun PlanCard(
    planType: String,
    price: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val bgColor = if (planType == "Lifetime") Color(0xFF32303D) else Color(0xFF2D2D2F)
    val borderColor = if (selected) Color.White else Color.Transparent
    val cornerRadius = 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
    ) {

        // MODIFICATION: The badge is now placed here, so it is drawn behind the Row below.
        if (planType == "Lifetime") {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp)
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bestDealGradient)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Limited Time Offer",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.offset(y = (-3).dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
                .background(bgColor)
                .selectable(selected = selected, onClick = onSelect)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (planType == "Lifetime") {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(price, color = if (price == "⚠") Color.Red else Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(id = R.string.one_time_payment), color = Color(0xFFD3D3D3), fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(id = R.string.offer_ends_soon), color = Color(0xFFB0B0B0), fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else { // Monthly
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = R.string.monthly_plan), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(1.dp))
                    Text("$price / month", color = Color(0xFFB0B0B0), fontSize = 12.5.sp)
                }
                Text(price, color = if (price == "⚠") Color.Red else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

//        if (planType == "Lifetime") {
//            Box(
//                modifier = Modifier
//                    .align(Alignment.TopEnd)
//                    .padding(end = 20.dp)
//                    .offset(y = (-10).dp)
//                    .clip(RoundedCornerShape(8.dp))
//                    .background(bestDealGradient)
//                    .padding(horizontal = 12.dp, vertical = 4.dp),
//                contentAlignment = Alignment.Center
//            ) {
//                Text("Limited Time Offer", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
//            }
//        }
    }
}

@Composable
fun ReviewCarousel(reviews: List<UserReview>) {
    Column(modifier = Modifier.padding(top = 15.dp)) {
        Text(
            text = "What Our Users Say",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 5.dp , bottom = 14.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 9.dp)
        ) {
            // OPTIMIZATION: Adding a key helps Compose identify items efficiently,
            // preventing recomposition of items that haven't changed.
            items(reviews, key = { it.username }) { review ->
                ReviewCard(review)
            }
        }
    }
}

@Composable
fun ReviewCard(review: UserReview) {
    Column(
        modifier = Modifier
            .width(290.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF4E4E4E).copy(alpha = 0.38f))
            .padding(16.dp)
    ) {
        Text("⭐⭐⭐⭐⭐", color = Color(0xFFFFA500), fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(review.review, color = Color.White, fontSize = 12.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(8.dp))
        Text("- ${review.username}", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BulletItem(text: String, iconResId: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 10.dp)
            .padding(vertical = 10.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(25.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 17.sp)
    }
}

@Composable
fun RedeemInfoDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF2C2C2E)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Lifetime Premium", modifier = Modifier.padding(horizontal = 24.dp), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text("If you purchased Premium before, your one-time promo code unlocks lifetime Premium here too.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Left, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Make sure your correct Google account is active in the Play Store before redeeming.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Left, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("Don’t have a code?", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = "mailto:".toUri()
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf("fabtune.fabbl@gmail.com"))
                                        putExtra(Intent.EXTRA_SUBJECT, "Promo Code Request (Previous Premium User)")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No email app found.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(" Contact us", color = Color(0xFF0A84FF), fontSize = 13.sp)
                        }
                        Text(" for assistance", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFF545458), thickness = 1.dp)
                TextButton(
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Redeem Now", color = Color(0xFF0A84FF), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}