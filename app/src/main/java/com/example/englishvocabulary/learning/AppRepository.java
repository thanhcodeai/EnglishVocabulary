package com.example.englishvocabulary.learning;

import android.content.*;
import android.os.*;
import android.util.Patterns;
import com.example.englishvocabulary.R;
import com.example.englishvocabulary.data.database.AppDatabase;
import com.example.englishvocabulary.data.entity.Vocabulary;
import com.example.englishvocabulary.domain.*;
import com.example.englishvocabulary.learning.Records.*;
import com.example.englishvocabulary.learning.Records.Collection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;

/** Every read/write executes on one worker; multi-record writes use Room transactions. */
public final class AppRepository {
  public interface Callback {
    void done(AppState state, int error, String openedSession);
  }

  interface Action {
    String run();
  }

  private static volatile AppRepository instance;
  private final Context context;
  private final AppDatabase db;
  private final LearningDao dao;
  private final SharedPreferences login;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());

  public static AppRepository get(Context context) {
    if (instance == null)
      synchronized (AppRepository.class) {
        if (instance == null) instance = new AppRepository(context.getApplicationContext());
      }
    return instance;
  }

  private AppRepository(Context context) {
    this(context, AppDatabase.getInstance(context));
  }

  AppRepository(Context context, AppDatabase database) {
    this.context = context;
    db = database;
    dao = db.learningDao();
    login = context.getSharedPreferences("login", 0);
  }

  void close() {
    worker.shutdown();
  }

  private static class Failure extends RuntimeException {
    final int message;

    Failure(int message) {
      this.message = message;
    }
  }

  private void require(boolean condition, int error) {
    if (!condition) throw new Failure(error);
  }

  private long uid() {
    long id = login.getLong("userId", 0);
    require(id > 0 && dao.user(id) != null, R.string.error_login_required);
    return id;
  }

  private void execute(Action action, Callback callback) {
    worker.execute(
        () -> {
          int error = 0;
          String opened = null;
          AppState state;
          try {
            SeedData.ensure(context, db);
            opened = action.run();
          } catch (Failure e) {
            error = e.message;
          } catch (Exception e) {
            error = R.string.error_storage;
          }
          try {
            state = load();
          } catch (Exception e) {
            state = new AppState();
            error = R.string.error_storage;
          }
          int result = error;
          String session = opened;
          AppState snapshot = state;
          main.post(() -> callback.done(snapshot, result, session));
        });
  }

  private AppState load() {
    AppState s = new AppState();
    long id = login.getLong("userId", 0);
    s.user = dao.user(id);
    if (s.user == null) return s;
    s.settings = dao.settings(id);
    s.topics = dao.topics();
    s.words = dao.words();
    for (UserWord w : dao.userWords(id)) s.personal.put(w.vocabularyId, w);
    s.collections = dao.collections(id);
    s.members = dao.memberships(id);
    s.sessions = dao.sessions(id);
    s.interactions = dao.interactions(id);
    s.achievements = dao.achievements(id);
    s.times = dao.times(id);
    for (Session session : s.sessions)
      s.items.put(session.sessionId, dao.items(id, session.sessionId));
    return s;
  }

  public void refresh(Callback cb) {
    execute(() -> null, cb);
  }

  public void register(String name, String email, String password, String confirm, Callback cb) {
    execute(
        () -> {
          require(LearningRules.validName(name), R.string.error_name);
          String normalized = LearningRules.normalize(email);
          require(
              normalized.length() <= 254 && Patterns.EMAIL_ADDRESS.matcher(normalized).matches(),
              R.string.error_email);
          require(LearningRules.validPassword(password), R.string.error_password);
          require(password.equals(confirm), R.string.error_confirmation);
          require(dao.userByEmail(normalized) == null, R.string.error_email_used);
          User u = new User();
          u.name = name.trim();
          u.email = normalized;
          u.salt = PasswordHasher.salt();
          u.algorithm = PasswordHasher.algorithm();
          u.iterations = PasswordHasher.ITERATIONS;
          u.hash = PasswordHasher.hash(password, u.salt, u.algorithm, u.iterations);
          u.createdAt = System.currentTimeMillis();
          db.runInTransaction(
              () -> {
                u.userId = dao.insertUser(u);
                Settings settings = new Settings();
                settings.userId = u.userId;
                dao.insertSettings(settings);
              });
          return null;
        },
        cb);
  }

  private User authenticate(String email, String password) {
    String normalized = LearningRules.normalize(email);
    User u = dao.userByEmail(normalized);
    long now = System.currentTimeMillis();
    if (u == null) { // Bound unknown-email failures too; store no password or hash in preferences.
      SharedPreferences attempts = context.getSharedPreferences("login_attempts", 0);
      String key = normalized;
      long lock = attempts.getLong(key + ".lock", 0);
      require(lock <= now, R.string.error_locked);
      int failures = attempts.getInt(key + ".count", 0) + 1;
      attempts
          .edit()
          .putInt(key + ".count", failures >= 5 ? 0 : failures)
          .putLong(key + ".lock", failures >= 5 ? now + 60_000 : 0)
          .commit();
      throw new Failure(R.string.error_credentials);
    }
    require(u.lockedUntil <= now, R.string.error_locked);
    if (!PasswordHasher.verify(password, u.salt, u.hash, u.algorithm, u.iterations)) {
      u.failedAttempts++;
      if (u.failedAttempts >= 5) {
        u.failedAttempts = 0;
        u.lockedUntil = now + 60_000;
      }
      dao.updateUser(u);
      throw new Failure(R.string.error_credentials);
    }
    u.failedAttempts = 0;
    u.lockedUntil = 0;
    dao.updateUser(u);
    return u;
  }

  public void signIn(String email, String password, Callback cb) {
    execute(
        () -> {
          User u = authenticate(email, password);
          require(login.edit().putLong("userId", u.userId).commit(), R.string.error_storage);
          return null;
        },
        cb);
  }

  public void logout(Callback cb) {
    execute(
        () -> {
          require(login.edit().remove("userId").commit(), R.string.error_storage);
          return null;
        },
        cb);
  }

  public void profile(String name, Callback cb) {
    execute(
        () -> {
          require(LearningRules.validName(name), R.string.error_name);
          User u = dao.user(uid());
          u.name = name.trim();
          dao.updateUser(u);
          return null;
        },
        cb);
  }

  public void password(String old, String next, String confirm, Callback cb) {
    execute(
        () -> {
          User u = dao.user(uid());
          authenticate(u.email, old);
          require(LearningRules.validPassword(next), R.string.error_password);
          require(next.equals(confirm), R.string.error_confirmation);
          require(!old.equals(next), R.string.error_same_password);
          u.salt = PasswordHasher.salt();
          u.algorithm = PasswordHasher.algorithm();
          u.hash = PasswordHasher.hash(next, u.salt, u.algorithm, u.iterations);
          u.failedAttempts = 0;
          u.lockedUntil = 0;
          dao.updateUser(u);
          return null;
        },
        cb);
  }

  public void settings(
      String theme,
      String language,
      int goal,
      boolean reminder,
      int hour,
      int minute,
      Callback cb) {
    execute(
        () -> {
          require(goal >= 1 && goal <= 100, R.string.error_goal);
          require(hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59, R.string.error_time);
          require(
              Arrays.asList("SYSTEM", "LIGHT", "DARK").contains(theme)
                  && Arrays.asList("vi", "en").contains(language),
              R.string.error_storage);
          Settings settings = dao.settings(uid());
          settings.theme = theme;
          settings.language = language;
          settings.dailyGoal = goal;
          settings.reminder = reminder;
          settings.hour = hour;
          settings.minute = minute;
          dao.updateSettings(settings);
          return null;
        },
        cb);
  }

  private UserWord userWord(long user, int word) {
    UserWord w = dao.userWord(user, word);
    if (w == null) {
      w = new UserWord();
      w.userId = user;
      w.vocabularyId = word;
    }
    return w;
  }

  public void star(int id, Callback cb) {
    execute(
        () -> {
          UserWord w = userWord(uid(), id);
          w.favorite = !w.favorite;
          dao.saveWord(w);
          return null;
        },
        cb);
  }

  public void collection(long id, String name, Callback cb) {
    execute(
        () -> {
          long user = uid();
          require(LearningRules.validName(name), R.string.error_name);
          String normalized = LearningRules.normalize(name);
          for (Collection c : dao.collections(user))
            require(
                c.collectionId == id || !c.normalizedName.equals(normalized),
                R.string.error_duplicate_name);
          Collection c = id == 0 ? new Collection() : dao.collection(user, id);
          require(c != null, R.string.error_source);
          c.userId = user;
          c.name = name.trim();
          c.normalizedName = normalized;
          c.updatedAt = System.currentTimeMillis();
          if (id == 0) {
            c.createdAt = c.updatedAt;
            dao.insertCollection(c);
          } else dao.updateCollection(c);
          return null;
        },
        cb);
  }

  public void deleteCollection(long id, Callback cb) {
    execute(
        () -> {
          long user = uid();
          db.runInTransaction(
              () -> {
                dao.deleteCollection(user, id);
                for (Session s : dao.sessions(user))
                  if ("IN_PROGRESS".equals(s.status)
                      && "COLLECTION".equals(s.sourceType)
                      && s.sourceId == id) {
                    s.status = "ABANDONED";
                    dao.updateSession(s);
                  }
              });
          return null;
        },
        cb);
  }

  public void member(long collection, int word, boolean add, Callback cb) {
    execute(
        () -> {
          long user = uid();
          require(dao.collection(user, collection) != null, R.string.error_source);
          if (add) {
            Membership m = new Membership();
            m.collectionId = collection;
            m.vocabularyId = word;
            m.addedAt = System.currentTimeMillis();
            dao.addMember(m);
          } else dao.removeMember(user, collection, word);
          return null;
        },
        cb);
  }

  private Session active(long user, String id) {
    Session s = dao.session(user, id);
    require(s != null && "IN_PROGRESS".equals(s.status), R.string.error_source);
    if ("COLLECTION".equals(s.sourceType))
      require(dao.collection(user, s.sourceId) != null, R.string.error_source);
    return s;
  }

  public void start(
      String mode,
      String sourceType,
      long sourceId,
      String sourceName,
      String type,
      int count,
      String parent,
      Callback cb) {
    execute(
        () -> {
          long user = uid();
          AppState state = load();
          List<Vocabulary> source = state.source(sourceType, sourceId);
          if (parent != null) {
            Session original = dao.session(user, parent);
            require(original != null && "COMPLETED".equals(original.status), R.string.error_source);
            Set<Integer> wrong = new HashSet<>();
            for (Item i : dao.items(user, parent))
              if (Boolean.FALSE.equals(i.correct)) wrong.add(i.vocabularyId);
            source = new ArrayList<>();
            for (Vocabulary w : state.words) if (wrong.contains(w.getVocabularyId())) source.add(w);
          }
          require(!source.isEmpty(), R.string.error_empty_source);
          Session s = new Session();
          s.sessionId = UUID.randomUUID().toString();
          s.userId = user;
          s.mode = mode;
          s.sourceType = sourceType;
          s.sourceId = sourceId;
          s.sourceName = sourceName;
          s.parentSessionId = parent;
          s.startedAt = System.currentTimeMillis();
          List<Item> items = new ArrayList<>();
          if ("QUIZ".equals(mode)) {
            List<LearningRules.Word> words = new ArrayList<>();
            for (Vocabulary w : source)
              words.add(
                  new LearningRules.Word(
                      w.getVocabularyId(), w.getEnglishWord(), w.getVietnameseMeaning()));
            List<LearningRules.Question> quiz;
            try {
              quiz = LearningRules.quiz(words, type, count, new Random());
            } catch (IllegalArgumentException e) {
              throw new Failure(R.string.error_quiz_source);
            }
            for (LearningRules.Question q : quiz) {
              Item item = item(s, items.size(), state.word(q.word.id));
              item.questionType = q.type;
              item.expected = q.expected;
              item.options = new JSONArray(q.options).toString();
              items.add(item);
            }
          } else for (Vocabulary w : source) items.add(item(s, items.size(), w));
          s.totalQuestions = items.size();
          db.runInTransaction(
              () -> {
                dao.insertSession(s);
                dao.insertItems(items);
              });
          return s.sessionId;
        },
        cb);
  }

  private Item item(Session s, int index, Vocabulary w) {
    Item item = new Item();
    item.sessionId = s.sessionId;
    item.itemIndex = index;
    item.vocabularyId = w.getVocabularyId();
    item.word = w.getEnglishWord();
    item.meaning = w.getVietnameseMeaning();
    item.pronunciation = optional(w.getPronunciation());
    item.wordType = optional(w.getWordType());
    item.example = optional(w.getExampleSentence());
    return item;
  }

  private String optional(String s) {
    return s == null ? "" : s;
  }

  public void checkpoint(
      String id, int index, boolean face, String answer, long elapsed, Callback cb) {
    execute(
        () -> {
          long user = uid();
          db.runInTransaction(
              () -> {
                Session s = active(user, id);
                List<Item> items = dao.items(user, id);
                require(index >= 0 && index < items.size(), R.string.error_source);
                if (answer != null && "QUIZ".equals(s.mode)) {
                  Item item = items.get(index);
                  item.answer = answer;
                  dao.updateItem(item);
                }
                s.currentIndex = index;
                s.cardFace = face;
                addTime(s, elapsed);
                dao.updateSession(s);
              });
          return null;
        },
        cb);
  }

  private void addTime(Session s, long elapsed) {
    if (elapsed <= 0) return;
    s.activeDurationMs += elapsed;
    long end = System.currentTimeMillis(), start = end - elapsed;
    ZoneId zone = ZoneId.systemDefault();
    while (start < end) {
      LocalDate day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate();
      long boundary = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
      long stop = Math.min(end, boundary);
      String date = day.toString();
      ActiveTime time = dao.time(s.userId, s.sessionId, date);
      if (time == null) {
        time = new ActiveTime();
        time.userId = s.userId;
        time.sessionId = s.sessionId;
        time.localDate = date;
      }
      time.durationMs += stop - start;
      dao.saveTime(time);
      start = stop;
    }
  }

  private void interact(Session s, Item item, String event) {
    Interaction i = new Interaction();
    i.userId = s.userId;
    i.sessionId = s.sessionId;
    i.itemIndex = item.itemIndex;
    i.vocabularyId = item.vocabularyId;
    i.event = event;
    i.occurredAt = System.currentTimeMillis();
    i.localDate = LocalDate.now().toString();
    i.zoneId = ZoneId.systemDefault().getId();
    if (dao.insertInteraction(i) == -1) return;
    UserWord w = userWord(s.userId, item.vocabularyId);
    w.state = LearningRules.stateAfter(w.state, event);
    if ("REMEMBER".equals(event)) w.needsReview = false;
    else if ("REVIEW".equals(event) || "WRONG".equals(event)) {
      w.needsReview = true;
      w.reviewAt = i.occurredAt;
    }
    if (w.firstStudiedAt == 0) w.firstStudiedAt = i.occurredAt;
    w.lastInteractionAt = i.occurredAt;
    dao.saveWord(w);
  }

  private void award(long user) {
    AppState s = load();
    if (s.learned() >= 1) badge(user, "START");
    if (s.learned() >= 50) badge(user, "WORDS_50");
    if (s.longest() >= 7) badge(user, "STREAK_7");
    for (Session session : s.completed())
      if ("QUIZ".equals(session.mode)
          && session.totalQuestions >= 10
          && session.correctAnswers == session.totalQuestions) badge(user, "PERFECT");
  }

  private void badge(long user, String code) {
    Achievement a = new Achievement();
    a.userId = user;
    a.code = code;
    a.achievedAt = System.currentTimeMillis();
    dao.award(a);
  }

  public void rate(String id, int index, boolean remembered, long elapsed, Callback cb) {
    execute(
        () -> {
          long user = uid();
          db.runInTransaction(
              () -> {
                Session s = active(user, id);
                require(
                    "FLASH".equals(s.mode) && s.cardFace && s.currentIndex == index,
                    R.string.error_flip_first);
                Item i = dao.items(user, id).get(index);
                if (i.memoryRating != null) return;
                i.memoryRating = remembered ? "REMEMBER" : "REVIEW";
                dao.updateItem(i);
                interact(s, i, i.memoryRating);
                addTime(s, elapsed);
                dao.updateSession(s);
                award(user);
              });
          return null;
        },
        cb);
  }

  public void finish(String id, long elapsed, Callback cb) {
    execute(
        () -> {
          long user = uid();
          db.runInTransaction(
              () -> {
                Session s = dao.session(user, id);
                require(s != null, R.string.error_source);
                if ("COMPLETED".equals(s.status)) return;
                active(user, id);
                List<Item> items = dao.items(user, id);
                addTime(s, elapsed);
                int correct = 0;
                if ("QUIZ".equals(s.mode))
                  for (Item i : items) {
                    i.correct = LearningRules.correct(i.answer, i.expected);
                    if (i.correct) correct++;
                    dao.updateItem(i);
                    if (i.answer != null && !i.answer.trim().isEmpty())
                      interact(s, i, i.correct ? "CORRECT" : "WRONG");
                    else {
                      UserWord w = userWord(user, i.vocabularyId);
                      w.state = LearningRules.stateAfter(w.state, "WRONG");
                      w.needsReview = true;
                      w.lastInteractionAt = System.currentTimeMillis();
                      w.reviewAt = w.lastInteractionAt;
                      dao.saveWord(w);
                    }
                  }
                s.correctAnswers = correct;
                s.status = "COMPLETED";
                s.completedAt = System.currentTimeMillis();
                dao.updateSession(s);
                award(user);
              });
          return id;
        },
        cb);
  }

  public void reset(Callback cb) {
    execute(
        () -> {
          long user = uid();
          db.runInTransaction(
              () -> {
                dao.clearSessions(user);
                dao.clearAchievements(user);
                dao.resetWords(user);
              });
          return null;
        },
        cb);
  }
}
