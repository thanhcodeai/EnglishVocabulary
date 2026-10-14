package com.example.englishvocabulary.learning;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import com.example.englishvocabulary.data.database.AppDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MigrationTest {
  @Test
  public void versionOneUpgradePreservesSharedDataAndValidatesRoomSchema() {
    Context c = RuntimeEnvironment.getApplication();
    String name = "migration-test.db";
    c.deleteDatabase(name);
    SQLiteDatabase old = c.openOrCreateDatabase(name, 0, null);
    old.execSQL(
        "CREATE TABLE topics (topicId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, topicName TEXT,"
            + " description TEXT, createdAt INTEGER NOT NULL)");
    old.execSQL(
        "CREATE TABLE vocabularies (vocabularyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
            + " englishWord TEXT NOT NULL, vietnameseMeaning TEXT NOT NULL, pronunciation TEXT,"
            + " wordType TEXT, exampleSentence TEXT, topicId INTEGER, isFavorite INTEGER NOT NULL"
            + " DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN"
            + " KEY(topicId) REFERENCES topics(topicId) ON DELETE SET NULL)");
    old.execSQL("CREATE INDEX index_vocabularies_topicId ON vocabularies(topicId)");
    old.execSQL(
        "CREATE TABLE quiz_results (resultId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
            + " completedAt INTEGER NOT NULL, totalQuestions INTEGER NOT NULL, correctAnswers"
            + " INTEGER NOT NULL, scorePercent REAL NOT NULL)");
    old.execSQL("INSERT INTO topics VALUES(1,'Existing','Preserved',1)");
    old.execSQL(
        "INSERT INTO"
            + " vocabularies(vocabularyId,englishWord,vietnameseMeaning,topicId,createdAt,updatedAt)"
            + " VALUES(1,'hello','xin chào',1,1,1)");
    old.setVersion(1);
    old.close();
    AppDatabase db =
        Room.databaseBuilder(c, AppDatabase.class, name)
            .addMigrations(SchemaMigration.FROM_1_TO_2)
            .allowMainThreadQueries()
            .build();
    try {
      assertEquals("hello", db.learningDao().words().get(0).getEnglishWord());
      assertNull(db.learningDao().user(999));
    } finally {
      db.close();
      c.deleteDatabase(name);
    }
  }
}
