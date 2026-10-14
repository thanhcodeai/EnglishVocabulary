package com.example.englishvocabulary.learning;

import static org.junit.Assert.*;

import com.example.englishvocabulary.learning.Records.*;
import java.time.*;
import org.junit.Test;

public class AppStateTest {
  @Test
  public void averageAndOverallAccuracyDifferAsSpecified() {
    AppState state = new AppState();
    Session a = new Session();
    a.mode = "QUIZ";
    a.status = "COMPLETED";
    a.correctAnswers = 4;
    a.totalQuestions = 5;
    Session b = new Session();
    b.mode = "QUIZ";
    b.status = "COMPLETED";
    b.correctAnswers = 5;
    b.totalQuestions = 10;
    state.sessions.add(a);
    state.sessions.add(b);
    assertEquals(65, state.average(), 0.001);
    assertEquals(60, state.accuracy(), 0.001);
  }

  @Test
  public void repeatInteractionsCountDistinctWordsPerDay() {
    AppState state = new AppState();
    for (int i = 0; i < 3; i++) {
      Interaction event = new Interaction();
      event.vocabularyId = 7;
      event.localDate = LocalDate.now().toString();
      state.interactions.add(event);
    }
    Interaction old = new Interaction();
    old.vocabularyId = 8;
    old.localDate = LocalDate.now().minusDays(1).toString();
    state.interactions.add(old);
    assertEquals(1, state.today());
    assertEquals(2, state.learned());
  }

  @Test
  public void draftQuizDoesNotContributeOfficialScore() {
    AppState state = new AppState();
    Session s = new Session();
    s.mode = "QUIZ";
    s.totalQuestions = 5;
    s.correctAnswers = 5;
    state.sessions.add(s);
    assertEquals(0, state.quizCount());
    assertEquals(0, state.average(), 0);
  }

  @Test
  public void unratedFlashHasNoLearningTimeButValidDraftQuizCheckpointDoes() {
    AppState s = new AppState();
    Session flash = new Session();
    flash.sessionId = "flash";
    flash.mode = "FLASH";
    flash.status = "COMPLETED";
    s.sessions.add(flash);
    ActiveTime t = new ActiveTime();
    t.sessionId = "flash";
    t.localDate = LocalDate.now().toString();
    t.durationMs = 1000;
    s.times.add(t);
    assertEquals(0, s.duration(LocalDate.now(), LocalDate.now()));
    Item rating = new Item();
    rating.memoryRating = "REMEMBER";
    s.items.put("flash", java.util.Collections.singletonList(rating));
    assertEquals(1000, s.duration(LocalDate.now(), LocalDate.now()));
    Session quiz = new Session();
    quiz.sessionId = "quiz";
    quiz.mode = "QUIZ";
    s.sessions.add(quiz);
    ActiveTime q = new ActiveTime();
    q.sessionId = "quiz";
    q.localDate = t.localDate;
    q.durationMs = 2000;
    s.times.add(q);
    Item answer = new Item();
    answer.answer = "word";
    s.items.put("quiz", java.util.Collections.singletonList(answer));
    assertEquals(3000, s.duration(LocalDate.now(), LocalDate.now()));
    assertEquals(0, s.quizCount());
  }
}
