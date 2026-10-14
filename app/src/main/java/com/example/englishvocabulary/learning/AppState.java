package com.example.englishvocabulary.learning;

import com.example.englishvocabulary.data.entity.*;
import com.example.englishvocabulary.domain.LearningRules;
import com.example.englishvocabulary.learning.Records.*;
import com.example.englishvocabulary.learning.Records.Collection;
import java.time.*;
import java.util.*;

public final class AppState {
  public User user;
  public Settings settings;
  public List<Topic> topics = new ArrayList<>();
  public List<Vocabulary> words = new ArrayList<>();
  public List<Collection> collections = new ArrayList<>();
  public List<Membership> members = new ArrayList<>();
  public List<Session> sessions = new ArrayList<>();
  public List<Interaction> interactions = new ArrayList<>();
  public List<Achievement> achievements = new ArrayList<>();
  public List<ActiveTime> times = new ArrayList<>();
  public Map<Integer, UserWord> personal = new HashMap<>();
  public Map<String, List<Item>> items = new HashMap<>();

  public UserWord status(int id) {
    UserWord w = personal.get(id);
    return w == null ? new UserWord() : w;
  }

  public Vocabulary word(int id) {
    for (Vocabulary w : words) if (w.getVocabularyId() == id) return w;
    return null;
  }

  public Session session(String id) {
    for (Session s : sessions) if (s.sessionId.equals(id)) return s;
    return null;
  }

  public String topicName(Integer id) {
    for (Topic t : topics) if (Objects.equals(t.getTopicId(), id)) return t.getTopicName();
    return "";
  }

  public List<Vocabulary> source(String type, long id) {
    List<Vocabulary> result = new ArrayList<>();
    Set<Integer> memberIds = new HashSet<>();
    if ("COLLECTION".equals(type))
      for (Membership m : members) if (m.collectionId == id) memberIds.add(m.vocabularyId);
    for (Vocabulary w : words) {
      UserWord personal = status(w.getVocabularyId());
      if ("ALL".equals(type)
          || ("TOPIC".equals(type) && Objects.equals(w.getTopicId(), (int) id))
          || ("FAVORITES".equals(type) && personal.favorite)
          || ("REVIEW".equals(type) && personal.needsReview)
          || ("COLLECTION".equals(type) && memberIds.contains(w.getVocabularyId()))) result.add(w);
    }
    if ("REVIEW".equals(type))
      result.sort(
          (a, b) ->
              Long.compare(
                  status(b.getVocabularyId()).reviewAt, status(a.getVocabularyId()).reviewAt));
    return result;
  }

  public Set<LocalDate> days() {
    Set<LocalDate> d = new HashSet<>();
    for (Interaction i : interactions) d.add(LocalDate.parse(i.localDate));
    return d;
  }

  public int today() {
    Set<Integer> ids = new HashSet<>();
    String today = LocalDate.now().toString();
    for (Interaction i : interactions) if (i.localDate.equals(today)) ids.add(i.vocabularyId);
    return ids.size();
  }

  public int learned() {
    Set<Integer> ids = new HashSet<>();
    for (Interaction i : interactions) ids.add(i.vocabularyId);
    return ids.size();
  }

  public int mastered() {
    int n = 0;
    for (UserWord w : personal.values()) if (w.state == 2) n++;
    return n;
  }

  public int streak() {
    return LearningRules.currentStreak(days(), LocalDate.now());
  }

  public int longest() {
    return LearningRules.longestStreak(days());
  }

  public List<Session> completed() {
    List<Session> r = new ArrayList<>();
    for (Session s : sessions) if ("COMPLETED".equals(s.status)) r.add(s);
    r.sort((a, b) -> Long.compare(b.completedAt, a.completedAt));
    return r;
  }

  public int quizCount() {
    int n = 0;
    for (Session s : completed()) if ("QUIZ".equals(s.mode)) n++;
    return n;
  }

  public double average() {
    double sum = 0;
    for (Session s : completed())
      if ("QUIZ".equals(s.mode)) sum += LearningRules.percent(s.correctAnswers, s.totalQuestions);
    return quizCount() == 0 ? 0 : sum / quizCount();
  }

  public double accuracy() {
    int correct = 0, total = 0;
    for (Session s : completed())
      if ("QUIZ".equals(s.mode)) {
        correct += s.correctAnswers;
        total += s.totalQuestions;
      }
    return LearningRules.percent(correct, total);
  }

  public long duration(LocalDate from, LocalDate to) {
    long total = 0;
    for (ActiveTime t : times) {
      Session s = session(t.sessionId);
      LocalDate d = LocalDate.parse(t.localDate);
      if (s != null
          && (("QUIZ".equals(s.mode) && ("COMPLETED".equals(s.status) || answered(s.sessionId)))
              || ("FLASH".equals(s.mode) && rated(s.sessionId) > 0))
          && !d.isBefore(from)
          && !d.isAfter(to)) total += t.durationMs;
    }
    return total;
  }

  private boolean answered(String id) {
    for (Item item : items.getOrDefault(id, Collections.emptyList()))
      if (item.answer != null && !item.answer.trim().isEmpty()) return true;
    return false;
  }

  public int rated(String id) {
    int n = 0;
    for (Item i : items.getOrDefault(id, Collections.emptyList())) if (i.memoryRating != null) n++;
    return n;
  }
}
