package com.livetv.app

class Playlist(var name: String, val items: MutableList<Channel>)

object RData {
    val tags: List<Item> = listOf(
        "news", "pop", "rock", "music", "talk", "religion", "islamic", "quran",
        "classical", "jazz", "hits", "dance", "oldies", "sports", "hiphop",
        "electronic", "folk", "bollywood", "kids"
    ).map { Item(it.replaceFirstChar { c -> c.uppercase() }, it) }
}

object Lang {
    // Pair(native name, language tag)
    val list: List<Pair<String, String>> = listOf(
        Pair("English", "en"),
        Pair("اردو", "ur"),
        Pair("العربية", "ar"),
        Pair("हिन्दी", "hi"),
        Pair("বাংলা", "bn"),
        Pair("فارسی", "fa"),
        Pair("Türkçe", "tr"),
        Pair("Français", "fr"),
        Pair("Español", "es"),
        Pair("Русский", "ru"),
        Pair("Bahasa Indonesia", "id")
    )
}
