package com.example.englishvocabulary.ui;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.*;
import android.view.*;
import androidx.lifecycle.ViewModelProvider;
import com.example.englishvocabulary.MainActivity;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Renders actual Android views with native graphics, not HTML mockups. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "vi-w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DesignRenderTest {
  private void ready(AppViewModel vm) throws Exception {
    long end = System.nanoTime() + 20_000_000_000L;
    while ((vm.data() == null || Boolean.TRUE.equals(vm.busy.getValue()))
        && System.nanoTime() < end) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(10);
    }
    assertNotNull(vm.data());
    assertFalse(Boolean.TRUE.equals(vm.busy.getValue()));
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  private void capture(MainActivity a, String name) throws Exception {
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    View view = a.getWindow().getDecorView();
    view.measure(
        View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, 360, 780);
    Bitmap bitmap = Bitmap.createBitmap(360, 780, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
    File dir = new File(System.getProperty("ui.screenshot.dir"));
    assertTrue(dir.isDirectory() || dir.mkdirs());
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
    }
    bitmap.recycle();
  }

  @Test
  public void renderAllScreenFamilies() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = controller.get();
      AppViewModel vm = new ViewModelProvider(a).get(AppViewModel.class);
      ready(vm);
      vm.logout();
      ready(vm);
      capture(a, "01-login");
      vm.root("REGISTER");
      capture(a, "02-register");
      vm.register("Minh Anh", "design@example.com", "Password123", "Password123");
      ready(vm);
      vm.login("design@example.com", "Password123");
      ready(vm);
      capture(a, "03-home");
      for (String page :
          new String[] {
            "TOPICS",
            "LEARNING",
            "FAVORITES",
            "PROFILE",
            "HISTORY",
            "ACHIEVEMENTS",
            "STATS",
            "SETTINGS",
            "ABOUT",
            "PASSWORD",
            "EDIT_PROFILE",
            "COLLECTION_FORM"
          }) {
        vm.root(page);
        capture(a, page.toLowerCase());
      }
      Bundle words = AppViewModel.screen("WORDS");
      words.putString("sourceType", "TOPIC");
      words.putLong("sourceId", vm.data().topics.get(0).getTopicId());
      words.putString("sourceName", vm.data().topics.get(0).getTopicName());
      vm.open(words);
      capture(a, "words");
      Bundle detail = AppViewModel.screen("DETAIL");
      detail.putInt("wordId", vm.data().words.get(0).getVocabularyId());
      vm.open(detail);
      capture(a, "detail");
      Bundle quiz = new Bundle(words);
      quiz.putString("screen", "QUIZ_CONFIG");
      vm.open(quiz);
      capture(a, "quiz-config");
      vm.start(
          "FLASH",
          "ALL",
          0,
          a.getString(com.example.englishvocabulary.R.string.all_words),
          "",
          0,
          null);
      ready(vm);
      capture(a, "flashcard");
      String id = vm.current().getString("sessionId");
      vm.move(id, 0, true, null, 0);
      ready(vm);
      capture(a, "flashcard-back");
      vm.rate(id, 0, true, 0);
      ready(vm);
      vm.finish(id, 0);
      ready(vm);
      capture(a, "flashcard-result");
      vm.start(
          "QUIZ",
          "ALL",
          0,
          a.getString(com.example.englishvocabulary.R.string.all_words),
          "EN_VI",
          5,
          null);
      ready(vm);
      capture(a, "quiz");
      String quizId = vm.current().getString("sessionId");
      vm.move(quizId, 0, false, vm.data().items.get(quizId).get(0).expected, 0);
      ready(vm);
      capture(a, "quiz-selected");
      vm.finish(quizId, 0);
      ready(vm);
      capture(a, "quiz-result");
      vm.collection(0, "Từ mỗi ngày");
      ready(vm);
      long collection = vm.data().collections.get(0).collectionId;
      vm.member(collection, vm.data().words.get(0).getVocabularyId(), true);
      ready(vm);
      capture(a, "favorites-populated");
      Bundle members = AppViewModel.screen("MEMBERS");
      members.putString("sourceType", "COLLECTION");
      members.putLong("sourceId", collection);
      members.putString("sourceName", "Từ mỗi ngày");
      vm.open(members);
      capture(a, "collection-members");
      Bundle source = AppViewModel.screen("SOURCE");
      source.putString("mode", "FLASH");
      vm.open(source);
      capture(a, "source-picker");
      vm.root("HISTORY");
      capture(a, "history-populated");
      vm.root("STATS");
      capture(a, "stats-populated");
    }
  }

  @Test
  @Config(sdk = 28, qualifiers = "vi-w360dp-h780dp-night-mdpi")
  public void renderDarkThemeWithLargeText() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = controller.get();
      AppViewModel vm = new ViewModelProvider(a).get(AppViewModel.class);
      ready(vm);
      vm.logout();
      ready(vm);
      vm.register("Minh Anh", "dark-design@example.com", "Password123", "Password123");
      ready(vm);
      vm.login("dark-design@example.com", "Password123");
      ready(vm);
      assertEquals(
          0xff121f24,
          androidx.core.content.ContextCompat.getColor(
              a, com.example.englishvocabulary.R.color.ui_page));
      capture(a, "dark-home");
      android.content.res.Configuration config =
          new android.content.res.Configuration(a.getResources().getConfiguration());
      config.fontScale = 1.3f;
      controller.configurationChange(config);
      a = controller.get();
      vm = new ViewModelProvider(a).get(AppViewModel.class);
      ready(vm);
      assertEquals(1.3f, a.getResources().getConfiguration().fontScale, .01f);
      for (String page : new String[] {"HOME", "SETTINGS", "PROFILE", "LEARNING", "TOPICS"}) {
        vm.root(page);
        capture(a, "dark-large-" + page.toLowerCase());
      }
      vm.start(
          "QUIZ",
          "ALL",
          0,
          a.getString(com.example.englishvocabulary.R.string.all_words),
          "EN_VI",
          5,
          null);
      ready(vm);
      capture(a, "dark-large-quiz");
    }
  }
}
