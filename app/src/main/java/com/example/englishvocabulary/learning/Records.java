package com.example.englishvocabulary.learning;

import androidx.annotation.NonNull;
import androidx.room.*;

/** Account-scoped records. Shared Topic/Vocabulary remain read-only in the UI. */
public final class Records {
  private Records() {}

  @Entity(tableName = "users", indices = @Index(value = "email", unique = true))
  public static class User {
    @PrimaryKey(autoGenerate = true)
    public long userId;

    @NonNull public String name = "", email = "", algorithm = "";
    @NonNull public byte[] salt = new byte[0], hash = new byte[0];
    public int iterations, failedAttempts;
    public long lockedUntil, createdAt;
  }

  @Entity(
      tableName = "user_settings",
      foreignKeys =
          @ForeignKey(
              entity = User.class,
              parentColumns = "userId",
              childColumns = "userId",
              onDelete = ForeignKey.CASCADE))
  public static class Settings {
    @PrimaryKey public long userId;
    @NonNull public String theme = "SYSTEM", language = "vi";
    public int dailyGoal = 10, hour = 20, minute = 0;
    public boolean reminder;
  }

  @Entity(
      tableName = "user_words",
      primaryKeys = {"userId", "vocabularyId"},
      foreignKeys = {
        @ForeignKey(
            entity = User.class,
            parentColumns = "userId",
            childColumns = "userId",
            onDelete = ForeignKey.CASCADE),
        @ForeignKey(
            entity = com.example.englishvocabulary.data.entity.Vocabulary.class,
            parentColumns = "vocabularyId",
            childColumns = "vocabularyId",
            onDelete = ForeignKey.CASCADE)
      },
      indices = @Index("vocabularyId"))
  public static class UserWord {
    public long userId;
    public int vocabularyId, state;
    public boolean favorite, needsReview;
    public long firstStudiedAt, lastInteractionAt, reviewAt;
  }

  @Entity(
      tableName = "collections",
      foreignKeys =
          @ForeignKey(
              entity = User.class,
              parentColumns = "userId",
              childColumns = "userId",
              onDelete = ForeignKey.CASCADE),
      indices =
          @Index(
              value = {"userId", "normalizedName"},
              unique = true))
  public static class Collection {
    @PrimaryKey(autoGenerate = true)
    public long collectionId;

    public long userId, createdAt, updatedAt;
    @NonNull public String name = "", normalizedName = "";
  }

  @Entity(
      tableName = "collection_words",
      primaryKeys = {"collectionId", "vocabularyId"},
      foreignKeys = {
        @ForeignKey(
            entity = Collection.class,
            parentColumns = "collectionId",
            childColumns = "collectionId",
            onDelete = ForeignKey.CASCADE),
        @ForeignKey(
            entity = com.example.englishvocabulary.data.entity.Vocabulary.class,
            parentColumns = "vocabularyId",
            childColumns = "vocabularyId",
            onDelete = ForeignKey.CASCADE)
      },
      indices = @Index("vocabularyId"))
  public static class Membership {
    public long collectionId, addedAt;
    public int vocabularyId;
  }

  @Entity(
      tableName = "learning_sessions",
      foreignKeys =
          @ForeignKey(
              entity = User.class,
              parentColumns = "userId",
              childColumns = "userId",
              onDelete = ForeignKey.CASCADE),
      indices = @Index(value = {"userId", "completedAt"}))
  public static class Session {
    @PrimaryKey @NonNull public String sessionId = "";
    public long userId, sourceId, startedAt, completedAt, activeDurationMs;
    @NonNull public String mode = "", sourceType = "", sourceName = "", status = "IN_PROGRESS";
    public String parentSessionId;
    public int totalQuestions, correctAnswers, currentIndex;
    public boolean cardFace;
  }

  @Entity(
      tableName = "session_items",
      primaryKeys = {"sessionId", "itemIndex"},
      foreignKeys =
          @ForeignKey(
              entity = Session.class,
              parentColumns = "sessionId",
              childColumns = "sessionId",
              onDelete = ForeignKey.CASCADE))
  public static class Item {
    @NonNull public String sessionId = "";
    public int itemIndex, vocabularyId;
    @NonNull
    public String word = "",
        meaning = "",
        pronunciation = "",
        wordType = "",
        example = "",
        questionType = "",
        options = "[]",
        expected = "";
    public String answer, memoryRating;
    public Boolean correct;
  }

  @Entity(
      tableName = "learning_interactions",
      foreignKeys =
          @ForeignKey(
              entity = Session.class,
              parentColumns = "sessionId",
              childColumns = "sessionId",
              onDelete = ForeignKey.CASCADE),
      indices = {
        @Index("sessionId"),
        @Index(value = {"userId", "localDate"}),
        @Index(
            value = {"sessionId", "itemIndex"},
            unique = true)
      })
  public static class Interaction {
    @PrimaryKey(autoGenerate = true)
    public long interactionId;

    public long userId, occurredAt;
    @NonNull public String sessionId = "", event = "", localDate = "", zoneId = "";
    public int vocabularyId, itemIndex;
  }

  @Entity(
      tableName = "achievements",
      primaryKeys = {"userId", "code"},
      foreignKeys =
          @ForeignKey(
              entity = User.class,
              parentColumns = "userId",
              childColumns = "userId",
              onDelete = ForeignKey.CASCADE))
  public static class Achievement {
    public long userId, achievedAt;
    @NonNull public String code = "";
  }

  @Entity(
      tableName = "active_times",
      primaryKeys = {"sessionId", "localDate"},
      foreignKeys =
          @ForeignKey(
              entity = Session.class,
              parentColumns = "sessionId",
              childColumns = "sessionId",
              onDelete = ForeignKey.CASCADE),
      indices = @Index("userId"))
  public static class ActiveTime {
    @NonNull public String sessionId = "", localDate = "";
    public long userId, durationMs;
  }
}
