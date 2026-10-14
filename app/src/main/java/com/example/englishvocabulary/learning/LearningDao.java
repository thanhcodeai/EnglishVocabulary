package com.example.englishvocabulary.learning;

import androidx.room.*;
import com.example.englishvocabulary.data.entity.*;
import com.example.englishvocabulary.learning.Records.*;
import java.util.List;

@Dao
public interface LearningDao {
  @Query("SELECT * FROM users WHERE email=:email")
  User userByEmail(String email);

  @Query("SELECT * FROM users WHERE userId=:userId")
  User user(long userId);

  @Insert
  long insertUser(User user);

  @Update
  void updateUser(User user);

  @Insert
  void insertSettings(Settings settings);

  @Update
  void updateSettings(Settings settings);

  @Query("SELECT * FROM user_settings WHERE userId=:userId")
  Settings settings(long userId);

  @Query("SELECT * FROM topics ORDER BY topicName")
  List<Topic> topics();

  @Query("SELECT * FROM vocabularies ORDER BY englishWord COLLATE NOCASE")
  List<Vocabulary> words();

  @Query("SELECT * FROM user_words WHERE userId=:userId")
  List<UserWord> userWords(long userId);

  @Query("SELECT * FROM user_words WHERE userId=:userId AND vocabularyId=:wordId")
  UserWord userWord(long userId, int wordId);

  @Upsert
  void saveWord(UserWord word);

  @Query("SELECT * FROM collections WHERE userId=:userId ORDER BY name")
  List<Collection> collections(long userId);

  @Query("SELECT * FROM collections WHERE userId=:userId AND collectionId=:id")
  Collection collection(long userId, long id);

  @Insert
  long insertCollection(Collection collection);

  @Update
  void updateCollection(Collection collection);

  @Query("DELETE FROM collections WHERE userId=:userId AND collectionId=:id")
  void deleteCollection(long userId, long id);

  @Query(
      "SELECT m.* FROM collection_words m JOIN collections c ON c.collectionId=m.collectionId WHERE"
          + " c.userId=:userId")
  List<Membership> memberships(long userId);

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  void addMember(Membership member);

  @Query(
      "DELETE FROM collection_words WHERE collectionId=:id AND vocabularyId=:wordId AND"
          + " EXISTS(SELECT 1 FROM collections WHERE collectionId=:id AND userId=:userId)")
  void removeMember(long userId, long id, int wordId);

  @Query("SELECT * FROM learning_sessions WHERE userId=:userId ORDER BY startedAt DESC")
  List<Session> sessions(long userId);

  @Query("SELECT * FROM learning_sessions WHERE userId=:userId AND sessionId=:id")
  Session session(long userId, String id);

  @Insert
  void insertSession(Session session);

  @Update
  void updateSession(Session session);

  @Insert
  void insertItems(List<Item> items);

  @Query(
      "SELECT i.* FROM session_items i JOIN learning_sessions s ON s.sessionId=i.sessionId WHERE"
          + " s.userId=:userId AND s.sessionId=:id ORDER BY itemIndex")
  List<Item> items(long userId, String id);

  @Update
  void updateItem(Item item);

  @Query(
      "SELECT i.* FROM learning_interactions i WHERE userId=:userId ORDER BY occurredAt DESC,"
          + " interactionId DESC")
  List<Interaction> interactions(long userId);

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  long insertInteraction(Interaction interaction);

  @Query("SELECT * FROM achievements WHERE userId=:userId")
  List<Achievement> achievements(long userId);

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  void award(Achievement achievement);

  @Query("SELECT * FROM active_times WHERE userId=:userId")
  List<ActiveTime> times(long userId);

  @Query("SELECT * FROM active_times WHERE userId=:userId AND sessionId=:id AND localDate=:date")
  ActiveTime time(long userId, String id, String date);

  @Upsert
  void saveTime(ActiveTime time);

  @Query("DELETE FROM learning_sessions WHERE userId=:userId")
  void clearSessions(long userId);

  @Query("DELETE FROM achievements WHERE userId=:userId")
  void clearAchievements(long userId);

  @Query(
      "UPDATE user_words SET state=0,needsReview=0,firstStudiedAt=0,lastInteractionAt=0,reviewAt=0"
          + " WHERE userId=:userId")
  void resetWords(long userId);
}
