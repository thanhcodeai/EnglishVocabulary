package com.example.englishvocabulary.domain;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.Test;

public class LearningRulesTest {
  @Test
  public void fillAnswersNormalizeUnicodeAndCaseButRejectTypos() {
    assertTrue(LearningRules.correct("  TECHNOLOGY  ", "technology"));
    assertTrue(LearningRules.correct("cafe\u0301", "CAFÉ"));
    assertFalse(LearningRules.correct("technlogy", "technology"));
    assertFalse(LearningRules.correct(null, "apple"));
  }

  @Test
  public void quizCorrectDoesNotPromoteToMasteredAndWrongDemotes() {
    assertEquals(1, LearningRules.stateAfter(0, "CORRECT"));
    assertEquals(1, LearningRules.stateAfter(1, "CORRECT"));
    assertEquals(2, LearningRules.stateAfter(2, "CORRECT"));
    assertEquals(1, LearningRules.stateAfter(2, "WRONG"));
    assertEquals(2, LearningRules.stateAfter(1, "REMEMBER"));
  }

  @Test
  public void goalsAndPasswordBoundaries() {
    assertFalse(LearningRules.validName(" "));
    assertTrue(LearningRules.validName(" x "));
    assertTrue(LearningRules.validName(String.join("", Collections.nCopies(50, "x"))));
    assertFalse(LearningRules.validName(String.join("", Collections.nCopies(51, "x"))));
    assertFalse(LearningRules.validPassword("abcdef1"));
    assertTrue(LearningRules.validPassword("abcdefg1"));
    assertFalse(LearningRules.validPassword("abcdefgh"));
    assertFalse(LearningRules.validPassword("12345678"));
    assertTrue(LearningRules.validPassword(String.join("", Collections.nCopies(63, "x")) + "1"));
    assertFalse(LearningRules.validPassword(String.join("", Collections.nCopies(64, "x")) + "1"));
  }

  @Test
  public void streakUsesYesterdayAndRespectsGaps() {
    LocalDate today = LocalDate.of(2026, 10, 14);
    Set<LocalDate> days =
        new HashSet<>(Arrays.asList(today.minusDays(1), today.minusDays(2), today.minusDays(4)));
    assertEquals(2, LearningRules.currentStreak(days, today));
    assertEquals(2, LearningRules.longestStreak(days));
    days.add(today);
    assertEquals(3, LearningRules.currentStreak(days, today));
    assertEquals(0, LearningRules.currentStreak(days, today.plusDays(2)));
  }

  private List<LearningRules.Word> source(int count) {
    List<LearningRules.Word> words = new ArrayList<>();
    for (int i = 0; i < count; i++) words.add(new LearningRules.Word(i, "word" + i, "meaning" + i));
    return words;
  }

  @Test
  public void quizHasFourUniqueOptionsOneAnswerAndNoRepeatedWords() {
    for (String type : Arrays.asList("EN_VI", "VI_EN")) {
      List<LearningRules.Question> questions =
          LearningRules.quiz(source(10), type, 20, new Random(7));
      assertEquals(10, questions.size());
      Set<Integer> ids = new HashSet<>();
      for (LearningRules.Question q : questions) {
        assertTrue(ids.add(q.word.id));
        assertEquals(4, q.options.size());
        assertEquals(4, new HashSet<>(q.options).size());
        assertEquals(1, Collections.frequency(q.options, q.expected));
      }
    }
  }

  @Test
  public void smallSourcesSupportFillButRejectMultipleChoice() {
    assertEquals(1, LearningRules.quiz(source(1), "FILL", 5, new Random(1)).size());
    for (int count : new int[] {0, 1, 3})
      try {
        LearningRules.quiz(source(count), "EN_VI", 5, new Random());
        fail();
      } catch (IllegalArgumentException expected) {
      }
  }

  @Test
  public void duplicateMeaningsAreExcludedFromBothChoiceTypes() {
    List<LearningRules.Word> words = source(6);
    words.add(new LearningRules.Word(9, "another", "MEANING0"));
    for (String type : Arrays.asList("EN_VI", "VI_EN"))
      for (LearningRules.Question q : LearningRules.quiz(words, type, 20, new Random(1)))
        assertFalse(q.word.id == 0 || q.word.id == 9);
  }

  @Test
  public void scoringHandlesBlankAndEmpty() {
    assertEquals(60, LearningRules.percent(3, 5), 0.001);
    assertEquals(0, LearningRules.percent(0, 0), 0);
  }

  @Test
  public void passwordUsesRandomSaltAndConstantVerification() {
    byte[] a = PasswordHasher.salt(), b = PasswordHasher.salt();
    assertEquals(16, a.length);
    assertFalse(Arrays.equals(a, b));
    String algorithm = PasswordHasher.algorithm();
    byte[] hash = PasswordHasher.hash("Sample123", a, algorithm, PasswordHasher.ITERATIONS);
    assertEquals(32, hash.length);
    assertTrue(PasswordHasher.verify("Sample123", a, hash, algorithm, PasswordHasher.ITERATIONS));
    assertFalse(PasswordHasher.verify("sample123", a, hash, algorithm, PasswordHasher.ITERATIONS));
  }
}
