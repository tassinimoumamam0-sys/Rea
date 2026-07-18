package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Article
import com.example.data.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val article: Article? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ArticleRepository
    
    val feedState: StateFlow<List<Article>>
    val savedArticlesState: StateFlow<List<Article>>

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _selectedArticleDetail = MutableStateFlow<Article?>(null)
    val selectedArticleDetail: StateFlow<Article?> = _selectedArticleDetail.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = ArticleRepository(database.articleDao())

        feedState = repository.allArticles
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        savedArticlesState = repository.savedArticles
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Seed initial greeting message
        _chatHistory.value = listOf(
            ChatMessage(
                text = "Bonjour ! Je suis votre expert en veille technologique spécialisé dans l'Intelligence Artificielle. Mon but est de vous expliquer de manière simple et vulgarisée, avec des analogies concrètes, toutes les innovations et les nouveaux outils d'IA.\n\nPosez-moi n'importe quelle question ! Par exemple : \"Explique-moi ce qu'est Sora\", ou \"Parle-moi des dernières avancées d'agents autonomes\".",
                isFromUser = false
            )
        )
    }

    fun selectArticle(article: Article?) {
        _selectedArticleDetail.value = article
    }

    fun toggleSaveArticle(article: Article) {
        viewModelScope.launch {
            repository.updateSavedStatus(article.id, !article.isSaved)
            
            // Sync status inside chat history if any chat message holds this article
            _chatHistory.value = _chatHistory.value.map { msg ->
                if (msg.article?.title == article.title) {
                    msg.copy(article = msg.article.copy(isSaved = !article.isSaved))
                } else {
                    msg
                }
            }
        }
    }

    fun deleteArticle(article: Article) {
        viewModelScope.launch {
            repository.deleteArticle(article)
        }
    }

    fun sendChatMessage(prompt: String) {
        if (prompt.trim().isEmpty()) return

        // Add user message to history
        val userMessage = ChatMessage(text = prompt, isFromUser = true)
        _chatHistory.value = _chatHistory.value + userMessage
        _isChatLoading.value = true

        viewModelScope.launch {
            try {
                val generatedArticle = repository.queryGeminiWatchExpert(prompt)
                
                if (generatedArticle != null) {
                    // Check if it's a casual conversation or a structured tool card
                    if (generatedArticle.title == "Bavardage" || generatedArticle.title == "Aide") {
                        val aiMessage = ChatMessage(
                            text = generatedArticle.summary,
                            isFromUser = false,
                            article = null
                        )
                        _chatHistory.value = _chatHistory.value + aiMessage
                    } else {
                        // It's a real tool report! Let's display a nice custom card
                        val aiMessage = ChatMessage(
                            text = "Voici mon rapport de veille technologique vulgarisé sur **${generatedArticle.title}** :",
                            isFromUser = false,
                            article = generatedArticle
                        )
                        _chatHistory.value = _chatHistory.value + aiMessage
                    }
                } else {
                    // Error state or placeholder
                    val errorMessage = ChatMessage(
                        text = "Désolé, je n'ai pas pu générer un rapport de veille pour le moment. Veuillez vérifier que votre clé d'API Gemini est correctement configurée dans le panneau de secrets.",
                        isFromUser = false
                    )
                    _chatHistory.value = _chatHistory.value + errorMessage
                }
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    text = "Une erreur s'est produite lors de l'appel au service de veille : ${e.localizedMessage}",
                    isFromUser = false
                )
                _chatHistory.value = _chatHistory.value + errorMessage
            } finally {
                _isChatLoading.value = false
            }
        }
    }
}
