package me.ash.reader.brief.feeds

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material icons (outlined) a feed or group can wear instead of its website icon. Stored as `icon:<name>`
 * (the name, not the drawing, so the choice survives app updates and backups).
 */
object SymbolIcons {
    const val PREFIX = "icon:"

    val all: List<Pair<String, ImageVector>> =
        listOf(
            "newspaper" to Icons.Outlined.Newspaper,
            "article" to Icons.AutoMirrored.Outlined.Article,
            "public" to Icons.Outlined.Public,
            "language" to Icons.Outlined.Language,
            "campaign" to Icons.Outlined.Campaign,
            "forum" to Icons.Outlined.Forum,
            "community" to Icons.Outlined.Groups,
            "computer" to Icons.Outlined.Computer,
            "phone" to Icons.Outlined.PhoneAndroid,
            "code" to Icons.Outlined.Code,
            "security" to Icons.Outlined.Security,
            "science" to Icons.Outlined.Science,
            "psychology" to Icons.Outlined.Psychology,
            "rocket" to Icons.Outlined.RocketLaunch,
            "lightbulb" to Icons.Outlined.Lightbulb,
            "bolt" to Icons.Outlined.Bolt,
            "cloud" to Icons.Outlined.Cloud,
            "weather" to Icons.Outlined.WbSunny,
            "money" to Icons.Outlined.AttachMoney,
            "chart" to Icons.AutoMirrored.Outlined.ShowChart,
            "bank" to Icons.Outlined.AccountBalance,
            "law" to Icons.Outlined.Gavel,
            "work" to Icons.Outlined.Work,
            "school" to Icons.Outlined.School,
            "book" to Icons.AutoMirrored.Outlined.MenuBook,
            "stories" to Icons.Outlined.AutoStories,
            "games" to Icons.Outlined.SportsEsports,
            "movie" to Icons.Outlined.Movie,
            "video" to Icons.Outlined.Videocam,
            "music" to Icons.Outlined.MusicNote,
            "podcast" to Icons.Outlined.Podcasts,
            "camera" to Icons.Outlined.PhotoCamera,
            "palette" to Icons.Outlined.Palette,
            "sport" to Icons.Outlined.SportsSoccer,
            "fitness" to Icons.Outlined.FitnessCenter,
            "health" to Icons.Outlined.HealthAndSafety,
            "food" to Icons.Outlined.Restaurant,
            "travel" to Icons.Outlined.Flight,
            "explore" to Icons.Outlined.TravelExplore,
            "car" to Icons.Outlined.DirectionsCar,
            "nature" to Icons.Outlined.Park,
            "eco" to Icons.Outlined.Eco,
            "pets" to Icons.Outlined.Pets,
            "home" to Icons.Outlined.Home,
            "diy" to Icons.Outlined.Handyman,
            "fashion" to Icons.Outlined.Checkroom,
            "shopping" to Icons.Outlined.ShoppingBag,
            "deals" to Icons.Outlined.LocalOffer,
            "mail" to Icons.Outlined.Mail,
            "mosque" to Icons.Outlined.Mosque,
            "favorite" to Icons.Outlined.FavoriteBorder,
            "star" to Icons.Outlined.StarOutline,
            "bookmark" to Icons.Outlined.BookmarkBorder,
            "folder" to Icons.Outlined.Folder,
        )

    private val byName = all.toMap()

    /** The drawing for a stored `icon:<name>` value, or null if it isn't one. */
    fun of(value: String?): ImageVector? =
        if (value != null && value.startsWith(PREFIX)) byName[value.removePrefix(PREFIX)] else null

    /** Whether [value] is a stored icon this app knows (for imports). */
    fun isKnown(value: String): Boolean = of(value) != null
}
