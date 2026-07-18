package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Article
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Article::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "veille_ia_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.articleDao())
                }
            }
        }

        suspend fun populateDatabase(articleDao: ArticleDao) {
            val seedArticles = listOf(
                Article(
                    title = "Gemini 3.5 Flash",
                    category = "Modèle d'IA",
                    summary = "Le nouveau champion mondial de la vitesse et de l'intelligence contextuelle. Il est capable de traiter instantanément des millions de mots, d'images et des heures de vidéo.",
                    analogy = "C'est comme un lecteur ultra-rapide doté d'une mémoire photographique absolue : il peut lire, résumer et comprendre un livre entier de 1500 pages en une fraction de seconde tout en discutant chaleureusement avec vous.",
                    whyItMatters = "Il démocratise l'accès aux IA ultra-intelligentes grâce à des coûts de fonctionnement divisés par 10 et un temps de réponse presque instantané, rendant les assistants virtuels incroyablement fluides.",
                    videoQuery = "Gemini 3.5 Flash capabilities preview",
                    videoUrl = "https://www.youtube.com/results?search_query=Gemini+3.5+Flash+capabilities+preview"
                ),
                Article(
                    title = "OpenAI Sora",
                    category = "Génération de Vidéo",
                    summary = "Une intelligence artificielle capable de créer des vidéos hyper-réalistes d'une minute entière à partir d'une simple description textuelle.",
                    analogy = "Imaginez un réalisateur de cinéma de génie à qui vous donnez une simple phrase d'idées. En quelques secondes, il crée une scène entière de film en 3D avec des mouvements de caméra complexes et un réalisme bluffant.",
                    whyItMatters = "Sora ne fait pas que générer des images animées ; il comprend la physique élémentaire du monde réel (comme les reflets, la gravité et la consistance des objets), révolutionnant la création de contenu vidéo.",
                    videoQuery = "OpenAI Sora cinematic AI video generation",
                    videoUrl = "https://www.youtube.com/results?search_query=OpenAI+Sora+cinematic+AI+video+generation"
                ),
                Article(
                    title = "Claude 3.5 Sonnet",
                    category = "Agent de Code",
                    summary = "L'un des modèles d'IA les plus avancés au monde pour le codage et la résolution de problèmes logiques complexes, agissant comme un ingénieur logiciel de niveau senior.",
                    analogy = "Imaginez un traducteur universel qui, au lieu de traduire le français vers l'anglais, traduit vos idées humaines imprécises en un code informatique parfaitement structuré, fonctionnel et exempt de bugs en un éclair.",
                    whyItMatters = "Il permet aux développeurs de démultiplier leur productivité et aux débutants de créer des applications entières en décrivant simplement ce qu'ils souhaitent obtenir en langage naturel.",
                    videoQuery = "Claude 3.5 Sonnet coding review",
                    videoUrl = "https://www.youtube.com/results?search_query=Claude+3.5+Sonnet+coding+review"
                ),
                Article(
                    title = "NotebookLM",
                    category = "Productivité",
                    summary = "Un carnet de notes dopé à l'IA capable d'analyser vos propres documents et de générer un podcast audio ultra-réaliste entre deux présentateurs IA qui en débattent.",
                    analogy = "Imaginez donner tous vos cours ou documents de travail ennuyeux à un duo d'animateurs de radio professionnels. Ils lisent tout instantanément et enregistrent une émission captivante et pleine d'humour pour tout vous expliquer.",
                    whyItMatters = "C'est une avancée majeure dans la vulgarisation d'informations denses. Elle transforme l'apprentissage passif et rébarbatif en une expérience audio interactive et agréable.",
                    videoQuery = "Google NotebookLM audio overview features",
                    videoUrl = "https://www.youtube.com/results?search_query=Google+NotebookLM+audio+overview+features"
                )
            )
            articleDao.insertArticles(seedArticles)
        }
    }
}
