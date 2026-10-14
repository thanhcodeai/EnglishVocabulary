package com.example.englishvocabulary.learning;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Additive upgrade: retains all version-1 vocabulary, topics and quiz results. */
public final class SchemaMigration {
  private SchemaMigration() {}

  public static final Migration FROM_1_TO_2 =
      new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS users (userId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                  + " name TEXT NOT NULL, email TEXT NOT NULL, algorithm TEXT NOT NULL, salt BLOB"
                  + " NOT NULL, hash BLOB NOT NULL, iterations INTEGER NOT NULL, failedAttempts"
                  + " INTEGER NOT NULL, lockedUntil INTEGER NOT NULL, createdAt INTEGER NOT NULL)");
          db.execSQL("CREATE UNIQUE INDEX index_users_email ON users(email)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS user_settings (userId INTEGER NOT NULL PRIMARY KEY, theme"
                  + " TEXT NOT NULL, language TEXT NOT NULL, dailyGoal INTEGER NOT NULL, hour"
                  + " INTEGER NOT NULL, minute INTEGER NOT NULL, reminder INTEGER NOT NULL, FOREIGN"
                  + " KEY(userId) REFERENCES users(userId) ON DELETE CASCADE)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS user_words (userId INTEGER NOT NULL, vocabularyId INTEGER"
                  + " NOT NULL, state INTEGER NOT NULL, favorite INTEGER NOT NULL, needsReview"
                  + " INTEGER NOT NULL, firstStudiedAt INTEGER NOT NULL, lastInteractionAt INTEGER"
                  + " NOT NULL, reviewAt INTEGER NOT NULL, PRIMARY KEY(userId,vocabularyId),"
                  + " FOREIGN KEY(userId) REFERENCES users(userId) ON DELETE CASCADE, FOREIGN"
                  + " KEY(vocabularyId) REFERENCES vocabularies(vocabularyId) ON DELETE CASCADE)");
          db.execSQL("CREATE INDEX index_user_words_vocabularyId ON user_words(vocabularyId)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS collections (collectionId INTEGER PRIMARY KEY"
                  + " AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, createdAt INTEGER NOT NULL,"
                  + " updatedAt INTEGER NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL,"
                  + " FOREIGN KEY(userId) REFERENCES users(userId) ON DELETE CASCADE)");
          db.execSQL(
              "CREATE UNIQUE INDEX index_collections_userId_normalizedName ON"
                  + " collections(userId,normalizedName)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS collection_words (collectionId INTEGER NOT NULL,"
                  + " vocabularyId INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY"
                  + " KEY(collectionId,vocabularyId), FOREIGN KEY(collectionId) REFERENCES"
                  + " collections(collectionId) ON DELETE CASCADE, FOREIGN KEY(vocabularyId)"
                  + " REFERENCES vocabularies(vocabularyId) ON DELETE CASCADE)");
          db.execSQL(
              "CREATE INDEX index_collection_words_vocabularyId ON collection_words(vocabularyId)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS learning_sessions (sessionId TEXT NOT NULL PRIMARY KEY,"
                  + " userId INTEGER NOT NULL, sourceId INTEGER NOT NULL, startedAt INTEGER NOT"
                  + " NULL, completedAt INTEGER NOT NULL, activeDurationMs INTEGER NOT NULL, mode"
                  + " TEXT NOT NULL, sourceType TEXT NOT NULL, sourceName TEXT NOT NULL, status"
                  + " TEXT NOT NULL, parentSessionId TEXT, totalQuestions INTEGER NOT NULL,"
                  + " correctAnswers INTEGER NOT NULL, currentIndex INTEGER NOT NULL, cardFace"
                  + " INTEGER NOT NULL, FOREIGN KEY(userId) REFERENCES users(userId) ON DELETE"
                  + " CASCADE)");
          db.execSQL(
              "CREATE INDEX index_learning_sessions_userId_completedAt ON"
                  + " learning_sessions(userId,completedAt)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS session_items (sessionId TEXT NOT NULL, itemIndex INTEGER"
                  + " NOT NULL, vocabularyId INTEGER NOT NULL, word TEXT NOT NULL, meaning TEXT NOT"
                  + " NULL, pronunciation TEXT NOT NULL, wordType TEXT NOT NULL, example TEXT NOT"
                  + " NULL, questionType TEXT NOT NULL, options TEXT NOT NULL, expected TEXT NOT"
                  + " NULL, answer TEXT, memoryRating TEXT, correct INTEGER, PRIMARY"
                  + " KEY(sessionId,itemIndex), FOREIGN KEY(sessionId) REFERENCES"
                  + " learning_sessions(sessionId) ON DELETE CASCADE)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS learning_interactions (interactionId INTEGER PRIMARY KEY"
                  + " AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, occurredAt INTEGER NOT NULL,"
                  + " sessionId TEXT NOT NULL, event TEXT NOT NULL, localDate TEXT NOT NULL, zoneId"
                  + " TEXT NOT NULL, vocabularyId INTEGER NOT NULL, itemIndex INTEGER NOT NULL,"
                  + " FOREIGN KEY(sessionId) REFERENCES learning_sessions(sessionId) ON DELETE"
                  + " CASCADE)");
          db.execSQL(
              "CREATE INDEX index_learning_interactions_sessionId ON"
                  + " learning_interactions(sessionId)");
          db.execSQL(
              "CREATE INDEX index_learning_interactions_userId_localDate ON"
                  + " learning_interactions(userId,localDate)");
          db.execSQL(
              "CREATE UNIQUE INDEX index_learning_interactions_sessionId_itemIndex ON"
                  + " learning_interactions(sessionId,itemIndex)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS achievements (userId INTEGER NOT NULL, code TEXT NOT"
                  + " NULL, achievedAt INTEGER NOT NULL, PRIMARY KEY(userId,code), FOREIGN"
                  + " KEY(userId) REFERENCES users(userId) ON DELETE CASCADE)");
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS active_times (sessionId TEXT NOT NULL, localDate TEXT NOT"
                  + " NULL, userId INTEGER NOT NULL, durationMs INTEGER NOT NULL, PRIMARY"
                  + " KEY(sessionId,localDate), FOREIGN KEY(sessionId) REFERENCES"
                  + " learning_sessions(sessionId) ON DELETE CASCADE)");
          db.execSQL("CREATE INDEX index_active_times_userId ON active_times(userId)");
        }
      };
}
