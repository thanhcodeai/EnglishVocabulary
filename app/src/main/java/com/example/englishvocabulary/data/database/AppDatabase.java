
package com.example.englishvocabulary.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.englishvocabulary.data.dao.QuizResultDao;
import com.example.englishvocabulary.data.dao.TopicDao;
import com.example.englishvocabulary.data.dao.VocabularyDao;

import com.example.englishvocabulary.data.entity.QuizResult;
import com.example.englishvocabulary.data.entity.Topic;
import com.example.englishvocabulary.data.entity.Vocabulary;

@Database(
        entities = {
                Topic.class,
                Vocabulary.class,
                QuizResult.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    // Cung cấp các DAO cho Repository
    public abstract VocabularyDao vocabularyDao();

    public abstract TopicDao topicDao();

    public abstract QuizResultDao quizResultDao();

    // Khởi tạo database theo Singleton
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "english_vocabulary_database"
                            )
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}
