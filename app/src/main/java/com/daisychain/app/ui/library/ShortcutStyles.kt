package com.daisychain.app.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.daisychain.app.ui.theme.Amber
import com.daisychain.app.ui.theme.Blue
import com.daisychain.app.ui.theme.Indigo
import com.daisychain.app.ui.theme.Plum
import com.daisychain.app.ui.theme.Slate
import com.daisychain.app.ui.theme.Teal

/** Icons a shortcut can use, keyed by the stable string stored in the database. */
val shortcutIcons: Map<String, ImageVector> = linkedMapOf(
    "bolt" to Icons.Outlined.Bolt,
    "work" to Icons.Outlined.BusinessCenter,
    "night" to Icons.Outlined.Bedtime,
    "home" to Icons.Outlined.Home,
    "clock" to Icons.Outlined.Schedule,
    "location" to Icons.Outlined.LocationOn,
    "cloud" to Icons.Outlined.CloudUpload,
    "bell" to Icons.Outlined.Notifications,
    "mail" to Icons.Outlined.Mail,
    "call" to Icons.Outlined.Call,
    "music" to Icons.Outlined.MusicNote,
    "camera" to Icons.Outlined.PhotoCamera,
    "cart" to Icons.Outlined.ShoppingCart,
    "car" to Icons.Outlined.DirectionsCar,
    "fitness" to Icons.Outlined.FitnessCenter,
    "light" to Icons.Outlined.Lightbulb,
    "wifi" to Icons.Outlined.Wifi,
    "alarm" to Icons.Outlined.Alarm,
    "star" to Icons.Outlined.Star,
    "heart" to Icons.Outlined.Favorite,
)

val shortcutColors: Map<String, Color> = linkedMapOf(
    "teal" to Teal,
    "indigo" to Indigo,
    "amber" to Amber,
    "slate" to Slate,
    "blue" to Blue,
    "plum" to Plum,
    "rose" to Color(0xFFB3263E),
    "green" to Color(0xFF3F7D20),
)

const val DEFAULT_ICON = "bolt"
const val DEFAULT_COLOR = "teal"

fun iconFor(key: String): ImageVector = shortcutIcons[key] ?: Icons.Outlined.Bolt
fun colorFor(key: String): Color = shortcutColors[key] ?: Teal
