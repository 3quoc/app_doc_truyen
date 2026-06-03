package com.example.sitruyenaudio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import android.util.Log

class ScraperRepository {
    companion object {
        private const val TAG = "ScraperRepository"
    }

    suspend fun fetchChapter(url: String): Result<Chapter> = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(10000)
                .get()

            // Lấy tiêu đề chương
            val titleElement = document.select("h2.current-chapter").first()
            val title = titleElement?.text() ?: "Không tìm thấy tiêu đề"

            // Lấy nội dung chữ
            // <div class="truyen"> có chứa nhiều đoạn <p> hoặc phân cách bằng <br>
            // Dựa trên file HTML đã phân tích, nội dung nằm trong <div class="truyen">
            // Đoạn văn được cách nhau bằng <br><br> hoặc các thẻ <p>.
            // Jsoup html() lấy HTML gốc, sau đó ta phân tích từng câu.
            val contentElement = document.select("div.truyen").first()
            
            val paragraphs = mutableListOf<String>()
            if (contentElement != null) {
                // Thay thế <br> và <p> bằng dấu phân cách an toàn
                contentElement.select("br").append("|||")
                contentElement.select("p").prepend("|||")
                val cleanText = contentElement.text()
                
                val lines = cleanText.split("|||")
                for (line in lines) {
                    val trim = line.trim()
                    if (trim.isNotEmpty()) {
                        paragraphs.add(trim)
                    }
                }
            }

            // Lấy link chương tiếp
            // <a href='...' class='next'>Chương tiếp 》</a>
            val nextElement = document.select("a.next").first()
            var nextUrl = nextElement?.attr("href")
            
            // Xử lý nextUrl có thể là đường dẫn tương đối
            if (!nextUrl.isNullOrEmpty() && !nextUrl.startsWith("http")) {
                if (nextUrl.startsWith("/")) {
                    nextUrl = "https://metruyenchuvn.com$nextUrl"
                } else {
                    // fall-back
                    val baseUri = document.baseUri()
                    val lastSlash = baseUri.lastIndexOf('/')
                    nextUrl = if (lastSlash != -1) {
                        baseUri.substring(0, lastSlash + 1) + nextUrl
                    } else {
                        "https://metruyenchuvn.com/$nextUrl"
                    }
                }
            }

            // Xoá các câu vô nghĩa cuối file (như khoảng trắng hoặc rác nếu có)
            
            Result.success(Chapter(title, paragraphs, nextUrl))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapter", e)
            Result.failure(e)
        }
    }
}
