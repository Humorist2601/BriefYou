package me.ash.reader.brief.feeds

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material icons a feed or group can wear instead of its website icon. Stored as `icon:<name>`
 * (the name, not the drawing, so the choice survives app updates and backups).
 */
object SymbolIcons {
    const val PREFIX = "icon:"

    val all: List<Pair<String, ImageVector>> =
        listOf(
            "newspaper" to Icons.Rounded.Newspaper,
            "article" to Icons.AutoMirrored.Rounded.Article,
            "public" to Icons.Rounded.Public,
            "language" to Icons.Rounded.Language,
            "campaign" to Icons.Rounded.Campaign,
            "forum" to Icons.Rounded.Forum,
            "computer" to Icons.Rounded.Computer,
            "phone" to Icons.Rounded.PhoneAndroid,
            "code" to Icons.Rounded.Code,
            "security" to Icons.Rounded.Security,
            "science" to Icons.Rounded.Science,
            "psychology" to Icons.Rounded.Psychology,
            "rocket" to Icons.Rounded.RocketLaunch,
            "lightbulb" to Icons.Rounded.Lightbulb,
            "bolt" to Icons.Rounded.Bolt,
            "cloud" to Icons.Rounded.Cloud,
            "money" to Icons.Rounded.AttachMoney,
            "chart" to Icons.AutoMirrored.Rounded.ShowChart,
            "bank" to Icons.Rounded.AccountBalance,
            "law" to Icons.Rounded.Gavel,
            "work" to Icons.Rounded.Work,
            "school" to Icons.Rounded.School,
            "book" to Icons.AutoMirrored.Rounded.MenuBook,
            "stories" to Icons.Rounded.AutoStories,
            "games" to Icons.Rounded.SportsEsports,
            "movie" to Icons.Rounded.Movie,
            "video" to Icons.Rounded.Videocam,
            "music" to Icons.Rounded.MusicNote,
            "podcast" to Icons.Rounded.Podcasts,
            "camera" to Icons.Rounded.PhotoCamera,
            "palette" to Icons.Rounded.Palette,
            "sport" to Icons.Rounded.SportsSoccer,
            "fitness" to Icons.Rounded.FitnessCenter,
            "health" to Icons.Rounded.HealthAndSafety,
            "food" to Icons.Rounded.Restaurant,
            "travel" to Icons.Rounded.Flight,
            "explore" to Icons.Rounded.TravelExplore,
            "car" to Icons.Rounded.DirectionsCar,
            "nature" to Icons.Rounded.Park,
            "eco" to Icons.Rounded.Eco,
            "pets" to Icons.Rounded.Pets,
            "home" to Icons.Rounded.Home,
            "shopping" to Icons.Rounded.ShoppingBag,
            "deals" to Icons.Rounded.LocalOffer,
            "mail" to Icons.Rounded.Mail,
            "mosque" to Icons.Rounded.Mosque,
            "favorite" to Icons.Rounded.Favorite,
            "star" to Icons.Rounded.Star,
            "bookmark" to Icons.Rounded.Bookmark,
            "folder" to Icons.Rounded.Folder,
        )

    private val byName = all.toMap()

    /** The drawing for a stored `icon:<name>` value, or null if it isn't one. */
    fun of(value: String?): ImageVector? =
        if (value != null && value.startsWith(PREFIX)) byName[value.removePrefix(PREFIX)] else null

    /** Whether [value] is a stored icon this app knows (for imports). */
    fun isKnown(value: String): Boolean = of(value) != null
}
