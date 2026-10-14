package com.example.englishvocabulary.learning;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;
import androidx.room.Room;
import com.example.englishvocabulary.R;
import com.example.englishvocabulary.data.database.AppDatabase;
import com.example.englishvocabulary.learning.Records.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RepositoryTest {
  private AppRepository repo;
  private AppDatabase db;
  private Context context;

  private interface Request {
    void call(AppRepository.Callback callback);
  }

  private static class Result {
    AppState state;
    int error;
    String id;

    Result(AppState s, int e, String i) {
      state = s;
      error = e;
      id = i;
    }
  }

  private Result await(Request request) throws Exception {
    AtomicReference<Result> result = new AtomicReference<>();
    request.call((s, e, id) -> result.set(new Result(s, e, id)));
    long end = System.nanoTime() + 20_000_000_000L;
    while (result.get() == null && System.nanoTime() < end) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(10);
    }
    assertNotNull("Repository callback must complete", result.get());
    return result.get();
  }

  @Before
  public void setup() {
    context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("login", 0).edit().clear().commit();
    context.getSharedPreferences("seed", 0).edit().clear().commit();
    context.getSharedPreferences("login_attempts", 0).edit().clear().commit();
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    repo = new AppRepository(context, db);
  }

  @After
  public void close() {
    repo.close();
    db.close();
  }

  private AppState signIn(String email) throws Exception {
    assertEquals(
        0, await(cb -> repo.register("Learner", email, "Password123", "Password123", cb)).error);
    Result r = await(cb -> repo.signIn(email, "Password123", cb));
    assertEquals(0, r.error);
    return r.state;
  }

  @Test
  public void accountsStarsAndCollectionsAreIsolatedAndPersisted() throws Exception {
    AppState alice = signIn("alice@example.com");
    int word = alice.words.get(0).getVocabularyId();
    long aliceId = alice.user.userId;
    assertEquals(0, await(cb -> repo.star(word, cb)).error);
    AppState state = await(cb -> repo.collection(0, " My words ", cb)).state;
    long collection = state.collections.get(0).collectionId;
    await(cb -> repo.member(collection, word, true, cb));
    await(cb -> repo.member(collection, word, true, cb));
    assertEquals(1, await(repo::refresh).state.members.size());
    assertEquals(
        R.string.error_duplicate_name, await(cb -> repo.collection(0, "MY WORDS", cb)).error);
    await(repo::logout);
    AppState bob = signIn("bob@example.com");
    assertFalse(bob.status(word).favorite);
    assertTrue(bob.collections.isEmpty());
    assertEquals(R.string.error_source, await(cb -> repo.member(collection, word, true, cb)).error);
    await(cb -> repo.deleteCollection(collection, cb));
    assertNotNull(db.learningDao().collection(aliceId, collection));
    await(repo::logout);
    state = await(cb -> repo.signIn("ALICE@EXAMPLE.COM", "Password123", cb)).state;
    assertTrue(state.status(word).favorite);
    assertEquals(1, state.collections.size());
  }

  @Test
  public void flashRatingRequiresFlipIsIdempotentAndResetKeepsFavorites() throws Exception {
    AppState state = signIn("flash@example.com");
    int word = state.words.get(0).getVocabularyId();
    await(cb -> repo.star(word, cb));
    await(cb -> repo.collection(0, "Retained", cb));
    String id = await(cb -> repo.start("FLASH", "ALL", 0, "All", "", 0, null, cb)).id;
    assertEquals(R.string.error_flip_first, await(cb -> repo.rate(id, 0, true, 100, cb)).error);
    await(cb -> repo.checkpoint(id, 0, true, null, 100, cb));
    state = await(cb -> repo.rate(id, 0, true, 100, cb)).state;
    assertEquals(1, state.today());
    assertEquals(2, state.status(word).state);
    await(cb -> repo.rate(id, 0, true, 100, cb));
    assertEquals(1, await(repo::refresh).state.interactions.size());
    state = await(cb -> repo.finish(id, 100, cb)).state;
    assertEquals(1, state.completed().size());
    assertEquals(1, state.achievements.size());
    state = await(repo::reset).state;
    assertEquals(0, state.learned());
    assertTrue(state.sessions.isEmpty());
    assertTrue(state.achievements.isEmpty());
    assertTrue(state.times.isEmpty());
    assertTrue(state.status(word).favorite);
    assertEquals(1, state.collections.size());
  }

  @Test
  public void quizCheckpointsSubmissionRetryAndBlankScoringAreDurable() throws Exception {
    signIn("quiz@example.com");
    String id = await(cb -> repo.start("QUIZ", "ALL", 0, "All", "FILL", 5, null, cb)).id;
    AppState state = await(repo::refresh).state;
    String answer = state.items.get(id).get(0).expected;
    await(
        cb ->
            repo.checkpoint(
                id, 0, false, " " + answer.toUpperCase(java.util.Locale.ROOT) + " ", 1250, cb));
    state = await(repo::refresh).state;
    assertEquals(0, state.quizCount());
    assertNotNull(state.items.get(id).get(0).answer);
    await(repo::logout);
    state = await(cb -> repo.signIn("quiz@example.com", "Password123", cb)).state;
    assertEquals(id, state.sessions.get(0).sessionId);
    assertNotNull(state.items.get(id).get(0).answer);
    state = await(cb -> repo.finish(id, 100, cb)).state;
    Session session = state.session(id);
    assertEquals(1, session.correctAnswers);
    assertEquals(20, state.average(), 0.001);
    assertEquals(1, state.learned());
    await(cb -> repo.finish(id, 100, cb));
    state = await(repo::refresh).state;
    assertEquals(1, state.quizCount());
    assertEquals(1, state.interactions.size());
    assertEquals(1350, state.session(id).activeDurationMs);
    String retry = await(cb -> repo.start("FLASH", "RETRY", 0, "Retry", "", 0, id, cb)).id;
    state = await(repo::refresh).state;
    assertEquals(4, state.items.get(retry).size());
    assertEquals(id, state.session(retry).parentSessionId);
  }

  @Test
  public void loginLocksAfterFiveFailuresAndPasswordCanChange() throws Exception {
    signIn("auth@example.com");
    await(repo::logout);
    for (int i = 0; i < 5; i++)
      assertEquals(
          R.string.error_credentials,
          await(cb -> repo.signIn("auth@example.com", "wrong", cb)).error);
    assertEquals(
        R.string.error_locked,
        await(cb -> repo.signIn("auth@example.com", "Password123", cb)).error);
    User u = db.learningDao().userByEmail("auth@example.com");
    u.lockedUntil = 0;
    db.learningDao().updateUser(u);
    assertEquals(0, await(cb -> repo.signIn("auth@example.com", "Password123", cb)).error);
    assertEquals(
        0, await(cb -> repo.password("Password123", "Changed123", "Changed123", cb)).error);
    await(repo::logout);
    assertEquals(
        R.string.error_credentials,
        await(cb -> repo.signIn("auth@example.com", "Password123", cb)).error);
    assertEquals(0, await(cb -> repo.signIn("auth@example.com", "Changed123", cb)).error);
  }

  @Test
  public void deletingCollectionInvalidatesDraftButKeepsCompletedHistory() throws Exception {
    AppState state = signIn("list@example.com");
    long user = state.user.userId;
    int word = state.words.get(0).getVocabularyId();
    long collection =
        await(cb -> repo.collection(0, "Source", cb)).state.collections.get(0).collectionId;
    await(cb -> repo.member(collection, word, true, cb));
    String done =
        await(cb -> repo.start("FLASH", "COLLECTION", collection, "Source", "", 0, null, cb)).id;
    await(cb -> repo.finish(done, 0, cb));
    String draft =
        await(cb -> repo.start("FLASH", "COLLECTION", collection, "Source", "", 0, null, cb)).id;
    await(cb -> repo.deleteCollection(collection, cb));
    state = await(repo::refresh).state;
    assertEquals("ABANDONED", state.session(draft).status);
    assertEquals("Source", state.session(done).sourceName);
    assertEquals(1, state.completed().size());
    assertNull(db.learningDao().collection(user, collection));
    assertTrue(state.members.isEmpty());
    assertNotNull(state.word(word));
  }

  @Test
  public void failedQuizWriteRollsBackAllAnswersInteractionsAndScore() throws Exception {
    signIn("rollback@example.com");
    String id = await(cb -> repo.start("QUIZ", "ALL", 0, "All", "FILL", 5, null, cb)).id;
    AppState s = await(repo::refresh).state;
    for (int n = 0; n < 2; n++) {
      final int index = n;
      String expected = s.items.get(id).get(n).expected;
      await(cb -> repo.checkpoint(id, index, false, expected, 0, cb));
    }
    db.getOpenHelper()
        .getWritableDatabase()
        .execSQL(
            "CREATE TRIGGER fail_quiz BEFORE INSERT ON learning_interactions WHEN NEW.itemIndex=1"
                + " BEGIN SELECT RAISE(ABORT,'simulated write failure'); END");
    Result failure = await(cb -> repo.finish(id, 100, cb));
    assertEquals(R.string.error_storage, failure.error);
    assertEquals("IN_PROGRESS", failure.state.session(id).status);
    assertEquals(0, failure.state.quizCount());
    assertTrue(failure.state.interactions.isEmpty());
    assertNull(failure.state.items.get(id).get(0).correct);
    assertEquals(0, failure.state.session(id).activeDurationMs);
    db.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER fail_quiz");
    s = await(cb -> repo.finish(id, 100, cb)).state;
    assertEquals(1, s.quizCount());
    assertEquals(2, s.interactions.size());
    assertEquals(2, s.session(id).correctAnswers);
  }

  @Test
  public void failedResetRollsBackCascadeDeletes() throws Exception {
    signIn("reset-rollback@example.com");
    String id = await(cb -> repo.start("FLASH", "ALL", 0, "All", "", 0, null, cb)).id;
    await(cb -> repo.checkpoint(id, 0, true, null, 0, cb));
    await(cb -> repo.rate(id, 0, true, 0, cb));
    await(cb -> repo.finish(id, 0, cb));
    db.getOpenHelper()
        .getWritableDatabase()
        .execSQL(
            "CREATE TRIGGER fail_reset BEFORE DELETE ON achievements BEGIN SELECT"
                + " RAISE(ABORT,'simulated write failure'); END");
    Result failure = await(repo::reset);
    assertEquals(R.string.error_storage, failure.error);
    assertEquals(1, failure.state.learned());
    assertEquals(1, failure.state.completed().size());
    assertEquals(1, failure.state.achievements.size());
    db.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER fail_reset");
    assertEquals(0, await(repo::reset).state.learned());
  }
}
