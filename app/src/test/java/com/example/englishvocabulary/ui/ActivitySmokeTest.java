package com.example.englishvocabulary.ui;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.example.englishvocabulary.MainActivity;
import com.example.englishvocabulary.R;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "vi")
public class ActivitySmokeTest {
  @Before
  public void signedOutFixture() throws Exception {
    java.util.concurrent.atomic.AtomicBoolean done =
        new java.util.concurrent.atomic.AtomicBoolean();
    com.example.englishvocabulary.learning.AppRepository.get(RuntimeEnvironment.getApplication())
        .logout(
            (state, error, id) -> {
              assertEquals(0, error);
              done.set(true);
            });
    idleUntil(done::get);
  }

  private void idleUntil(java.util.function.BooleanSupplier ready) throws Exception {
    long end = System.nanoTime() + 20_000_000_000L;
    while (!ready.getAsBoolean() && System.nanoTime() < end) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(10);
    }
    assertTrue(ready.getAsBoolean());
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  private List<EditText> fields(View view) {
    List<EditText> list = new ArrayList<>();
    if (view instanceof EditText) list.add((EditText) view);
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        list.addAll(fields(((ViewGroup) view).getChildAt(i)));
    return list;
  }

  @Test
  public void authHomeTopicsAndSettingsRenderWithoutAccessingPrivateScreensBeforeLogin()
      throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = controller.get();
      AppViewModel vm = new ViewModelProvider(activity).get(AppViewModel.class);
      idleUntil(() -> vm.data() != null && !Boolean.TRUE.equals(vm.busy.getValue()));
      assertEquals("LOGIN", vm.current().getString("screen"));
      assertEquals(View.GONE, activity.findViewById(R.id.navigation).getVisibility());
      assertEquals(2, fields(activity.findViewById(R.id.content)).size());
      vm.register("Demo", "demo@example.com", "Password123", "Password123");
      idleUntil(() -> !Boolean.TRUE.equals(vm.busy.getValue()));
      assertEquals(Integer.valueOf(R.string.registered), vm.message.getValue());
      vm.login("demo@example.com", "Password123");
      idleUntil(() -> !Boolean.TRUE.equals(vm.busy.getValue()) && vm.data().user != null);
      assertEquals("HOME", vm.current().getString("screen"));
      assertEquals(View.VISIBLE, activity.findViewById(R.id.navigation).getVisibility());
      vm.root("TOPICS");
      assertEquals(View.VISIBLE, activity.findViewById(R.id.list).getVisibility());
      assertEquals(
          6,
          ((androidx.recyclerview.widget.RecyclerView) activity.findViewById(R.id.list))
              .getAdapter()
              .getItemCount());
      vm.root("SETTINGS");
      assertEquals(
          activity.getString(R.string.settings),
          ((MaterialToolbar) activity.findViewById(R.id.toolbar)).getTitle().toString());
      assertFalse(fields(activity.findViewById(R.id.content)).isEmpty());
      vm.logout();
      idleUntil(() -> !Boolean.TRUE.equals(vm.busy.getValue()) && vm.data().user == null);
      assertEquals("LOGIN", vm.current().getString("screen"));
      assertFalse(vm.back());
    }
  }

  private Button button(View view, String label) {
    if (view instanceof Button && ((Button) view).getText().toString().equals(label))
      return (Button) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        Button b = button(((ViewGroup) view).getChildAt(i), label);
        if (b != null) return b;
      }
    return null;
  }

  @Test
  public void flashControlsRespectFlipAndCancelKeepsSession() throws Exception {
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = controller.get();
      AppViewModel vm = new ViewModelProvider(activity).get(AppViewModel.class);
      idleUntil(() -> vm.data() != null && !Boolean.TRUE.equals(vm.busy.getValue()));
      vm.register("Flash learner", "flash-ui@example.com", "Password123", "Password123");
      idleUntil(() -> !Boolean.TRUE.equals(vm.busy.getValue()));
      vm.login("flash-ui@example.com", "Password123");
      idleUntil(() -> !Boolean.TRUE.equals(vm.busy.getValue()) && vm.data().user != null);
      vm.start("FLASH", "ALL", 0, "All", "", 0, null);
      idleUntil(
          () ->
              !Boolean.TRUE.equals(vm.busy.getValue())
                  && "SESSION".equals(vm.current().getString("screen")));
      View content = activity.findViewById(R.id.content);
      assertFalse(button(content, activity.getString(R.string.remember)).isEnabled());
      assertFalse(button(content, activity.getString(R.string.previous)).isEnabled());
      String id = vm.current().getString("sessionId");
      vm.move(id, 0, true, null, 0);
      idleUntil(() -> !Boolean.TRUE.equals(vm.busy.getValue()));
      assertTrue(
          button(activity.findViewById(R.id.content), activity.getString(R.string.remember))
              .isEnabled());
      vm.rate(id, 0, true, 0);
      idleUntil(() -> !Boolean.TRUE.equals(vm.busy.getValue()));
      assertFalse(
          button(activity.findViewById(R.id.content), activity.getString(R.string.remember))
              .isEnabled());
      assertEquals(1, vm.data().learned());
      ((com.google.android.material.bottomnavigation.BottomNavigationView)
              activity.findViewById(R.id.navigation))
          .setSelectedItemId(R.id.nav_home);
      androidx.appcompat.app.AlertDialog dialog =
          (androidx.appcompat.app.AlertDialog)
              org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
      assertEquals("SESSION", vm.current().getString("screen"));
      controller.recreate();
      MainActivity rotated = controller.get();
      AppViewModel restored = new ViewModelProvider(rotated).get(AppViewModel.class);
      idleUntil(() -> restored.data() != null && !Boolean.TRUE.equals(restored.busy.getValue()));
      assertEquals(id, restored.current().getString("sessionId"));
      assertEquals(1, restored.data().interactions.size());
    }
  }
}
