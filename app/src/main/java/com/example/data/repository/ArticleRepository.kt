package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.ArticleDao
import com.example.data.model.Article
import com.example.data.remote.Content
import com.example.data.remote.GenerateContentRequest
import com.example.data.remote.GenerationConfig
import com.example.data.remote.Part
import com.example.data.remote.RetrofitClient
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@JsonClass(generateAdapter = true)
data class GeminiWatchResponse(
    val title: String,
    val category: String,
    val summary: String,
    val analogy: String,
    val whyItMatters: String,
    val videoQuery: String
)

class ArticleRepository(private val articleDao: ArticleDao) {

    val allArticles: Flow<List<Article>> = articleDao.getAllArticles()
    val savedArticles: Flow<List<Article>> = articleDao.getSavedArticles()

    suspend fun insertArticle(article: Article): Long = withContext(Dispatchers.IO) {
        articleDao.insertArticle(article)
    }

    suspend fun updateSavedStatus(id: Int, isSaved: Boolean) = withContext(Dispatchers.IO) {
        articleDao.updateSavedStatus(id, isSaved)
    }

    suspend fun deleteArticle(article: Article) = withContext(Dispatchers.IO) {
        articleDao.deleteArticle(article)
    }

    suspend fun queryGeminiWatchExpert(userPrompt: String): Article? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("ArticleRepository", "API Key is empty or placeholder!")
            return@withContext null
        }

        val systemPrompt = """
            Tu es l'expert de veille technologique spécialisé en Intelligence Artificielle (IA) de l'utilisateur. Ta mission est d'expliquer les outils, modèles, agents ou tendances d'IA de manière extrêmement vulgarisée, claire, imagée et structurée.
            
            Règles de fonctionnement cruciales :
            1. Vulgarisation absolue : Explique comme si l'utilisateur était un débutant complet. Utilise une analogie simple de la vie de tous les jours. Pas de jargon technique inutile. Concentre-toi sur le cas d'usage concret (à quoi ça sert concrètement) et pourquoi c'est important.
            2. Tu dois obligatoirement formater ta réponse sous la forme d'un objet JSON valide contenant exactement les clés suivantes :
               - "title": Le nom exact de l'outil ou du concept d'IA (ex: "Sora", "GPT-4o", etc.)
               - "category": La catégorie de cet outil (ex: "Génération de Vidéo", "Génération d'Image", "Agent de Code", "Modèle de Langage", "Productivité", etc.)
               - "summary": Une explication vulgarisée, simple, chaleureuse et captivante du concept ("Le quoi"). Explique à quoi ça sert concrètement de manière ultra-pédagogique.
               - "analogy": Une analogie marquante de la vie de tous les jours pour faire comprendre le fonctionnement de l'IA (ex: "C'est comme un peintre magique à qui...").
               - "whyItMatters": Pourquoi c'est important, révolutionnaire ou nouveau ("Pourquoi c'est important"), rédigé simplement pour un débutant.
               - "videoQuery": Une requête de recherche YouTube en anglais pertinente pour trouver une démo vidéo de cet outil (ex: "OpenAI Sora official video examples" ou "Claude 3.5 Sonnet coding agent demo review").
               
            3. Si le prompt de l'utilisateur est une salutation, un remerciement ou une discussion générale, réponds en JSON en adaptant les champs :
               - "title": "Bavardage" ou "Aide"
               - "category": "Assistance"
               - "summary": Ton message d'accueil ou ta réponse chaleureuse et conviviale.
               - "analogy": Une analogie amusante sur ton rôle d'expert en veille IA si pertinent, ou vide.
               - "whyItMatters": Vide ou un conseil rapide sur l'IA.
               - "videoQuery": "Artificial Intelligence future trends" (une requête générale de secours).
               
            Exprime-toi exclusivement en français pour le contenu (sauf pour la "videoQuery" qui doit être rédigée en anglais pour garantir d'excellents résultats de recherche sur YouTube).
            Rends uniquement l'objet JSON brut. Pas de texte explicatif avant ou après le JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                responseMimeType = "application/json"
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext null
            
            Log.d("ArticleRepository", "Raw Gemini Response: $rawText")
            val cleanedJson = cleanJsonResponse(rawText)
            
            val adapter = RetrofitClient.moshiInstance.adapter(GeminiWatchResponse::class.java)
            val parsed = adapter.fromJson(cleanedJson) ?: return@withContext null

            // Generate direct video URL from query
            val encodedQuery = URLEncoder.encode(parsed.videoQuery, "UTF-8")
            val videoUrl = "https://www.youtube.com/results?search_query=$encodedQuery"

            val article = Article(
                title = parsed.title,
                category = parsed.category,
                summary = parsed.summary,
                analogy = parsed.analogy,
                whyItMatters = parsed.whyItMatters,
                videoQuery = parsed.videoQuery,
                videoUrl = videoUrl,
                isUserCreated = true
            )

            // Save to database as a user-generated watch insight
            val id = articleDao.insertArticle(article)
            article.copy(id = id.toInt())
        } catch (e: Exception) {
            Log.e("ArticleRepository", "Error querying Gemini", e)
            null
        }
    }

    private fun cleanJsonResponse(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json").substringBeforeLast("```")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```").substringBeforeLast("```")
        }
        return cleaned.trim()
    }
}
