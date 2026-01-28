package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

//@Serializable
//data class SubscriptionButton(
//    val subscribeButtonRenderer: SubscribeButtonRenderer,
//) {
//    @Serializable
//    data class SubscribeButtonRenderer(
//        val subscribed: Boolean,
//        val channelId: String,
//    )
//}

//added sub number showing
@Serializable
data class SubscriptionButton(
    val subscribeButtonRenderer: SubscribeButtonRenderer,
) {
    @Serializable
    data class SubscribeButtonRenderer(
        val subscribed: Boolean,
        val channelId: String,
        val subscriberCountText: Runs? = null,  // ADD THIS LINE
        val longSubscriberCountText: Runs? = null,
        val shortSubscriberCountText: Runs? = null,
    )
}