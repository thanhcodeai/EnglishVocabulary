package com.example.englishvocabulary.domain;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

/** Pure business rules: no Android or database dependency. */
public final class LearningRules {
  private LearningRules() {}

  public static String normalize(String value) {
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }

  public static boolean validName(String name) {
    int length = name.trim().codePointCount(0, name.trim().length());
    return length >= 1 && length <= 50;
  }

  public static boolean validPassword(String password) {
    return password.length() >= 8
        && password.length() <= 64
        && password.codePoints().anyMatch(Character::isLetter)
        && password.codePoints().anyMatch(Character::isDigit);
  }

  public static boolean correct(String answer, String expected) {
    return answer != null && normalize(answer).equals(normalize(expected));
  }

  public static double percent(int correct, int total) {
    return total == 0 ? 0 : 100.0 * correct / total;
  }

  public static int stateAfter(int previous, String event) {
    if ("REMEMBER".equals(event)) return 2;
    if ("REVIEW".equals(event) || "WRONG".equals(event)) return 1;
    return previous == 0 ? 1 : previous;
  }

  public static int currentStreak(Set<LocalDate> days, LocalDate today) {
    LocalDate end = days.contains(today) ? today : today.minusDays(1);
    int count = 0;
    while (days.contains(end)) {
      count++;
      end = end.minusDays(1);
    }
    return count;
  }

  public static int longestStreak(Set<LocalDate> days) {
    List<LocalDate> ordered = new ArrayList<>(days);
    Collections.sort(ordered);
    int longest = 0, run = 0;
    LocalDate previous = null;
    for (LocalDate day : ordered) {
      run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
      longest = Math.max(longest, run);
      previous = day;
    }
    return longest;
  }

  public static final class Word {
    public final int id;
    public final String english, meaning;

    public Word(int id, String english, String meaning) {
      this.id = id;
      this.english = english;
      this.meaning = meaning;
    }
  }

  public static final class Question {
    public final Word word;
    public final String type, prompt, expected;
    public final List<String> options;

    public Question(Word word, String type, String prompt, String expected, List<String> options) {
      this.word = word;
      this.type = type;
      this.prompt = prompt;
      this.expected = expected;
      this.options = options;
    }
  }

  public static List<Question> quiz(List<Word> source, String type, int requested, Random random) {
    if (!Arrays.asList("EN_VI", "VI_EN", "FILL").contains(type) || requested <= 0)
      throw new IllegalArgumentException("invalid_quiz");
    Map<String, Integer> meaningCounts = new HashMap<>();
    Map<String, Integer> englishCounts = new HashMap<>();
    for (Word w : source) {
      meaningCounts.merge(normalize(w.meaning), 1, Integer::sum);
      englishCounts.merge(normalize(w.english), 1, Integer::sum);
    }
    List<Word> eligible = new ArrayList<>();
    Set<Integer> ids = new HashSet<>();
    for (Word w : source) {
      if (w.english.trim().isEmpty() || w.meaning.trim().isEmpty() || !ids.add(w.id)) continue;
      if (!"FILL".equals(type)
          && (meaningCounts.get(normalize(w.meaning)) > 1
              || englishCounts.get(normalize(w.english)) > 1)) continue;
      eligible.add(w);
    }
    Set<String> unique = new HashSet<>();
    List<String> pool = new ArrayList<>();
    for (Word w : eligible) {
      String option = "EN_VI".equals(type) ? w.meaning : w.english;
      if (unique.add(normalize(option))) pool.add(option);
    }
    if (eligible.isEmpty() || (!"FILL".equals(type) && pool.size() < 4))
      throw new IllegalArgumentException("insufficient_source");
    Collections.shuffle(eligible, random);
    List<Question> result = new ArrayList<>();
    for (Word w : eligible.subList(0, Math.min(requested, eligible.size()))) {
      String expected = "EN_VI".equals(type) ? w.meaning : w.english;
      List<String> options = new ArrayList<>();
      if (!"FILL".equals(type)) {
        List<String> distractors = new ArrayList<>();
        for (String option : pool)
          if (!normalize(option).equals(normalize(expected))) distractors.add(option);
        Collections.shuffle(distractors, random);
        options.add(expected);
        options.addAll(distractors.subList(0, 3));
        Collections.shuffle(options, random);
      }
      result.add(
          new Question(w, type, "EN_VI".equals(type) ? w.english : w.meaning, expected, options));
    }
    return result;
  }
}
