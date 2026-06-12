package com.example.sitruyenaudio.data

data class Chapter(
    val title: String,
    val paragraphs: List<String>,
    val nextChapterUrl: String?,
    val prevChapterUrl: String? = null
)
