package com.example.englishvocabulary.ui;

import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.example.englishvocabulary.R;
import com.example.englishvocabulary.learning.*;
import java.util.*;

/** Screen/form state survives rotations; learning state is additionally checkpointed in Room. */
public final class AppViewModel extends AndroidViewModel {
  private final AppRepository repository;
  private final SavedStateHandle saved;
  public final MutableLiveData<AppState> state = new MutableLiveData<>();
  public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
  public final MutableLiveData<Integer> message = new MutableLiveData<>(0);
  public final MutableLiveData<Bundle> route = new MutableLiveData<>();
  private AppState latest;
  private final Map<String, String> privateInputs = new HashMap<>();

  public AppViewModel(@NonNull Application app, SavedStateHandle saved) {
    super(app);
    this.saved = saved;
    repository = AppRepository.get(app);
    Bundle current = saved.get("route");
    route.setValue(current == null ? screen("HOME") : current);
    refresh();
  }

  public static Bundle screen(String screen) {
    Bundle b = new Bundle();
    b.putString("screen", screen);
    return b;
  }

  public Bundle current() {
    return route.getValue();
  }

  public AppState data() {
    return latest;
  }

  private interface Request {
    void call(AppRepository.Callback cb);
  }

  private void run(Request request, int success, String destination) {
    if (Boolean.TRUE.equals(busy.getValue())) return;
    busy.setValue(true);
    message.setValue(0);
    request.call(
        (s, error, session) -> {
          latest = s;
          busy.setValue(false);
          if (error == 0) {
            if (destination != null && Arrays.asList("LOGIN", "HOME").contains(destination))
              clearForms();
            if (success == R.string.password_changed) privateInputs.clear();
            if ("SESSION".equals(destination) || "RESULT".equals(destination)) {
              Bundle b = screen(destination);
              b.putString("sessionId", session);
              if ("RESULT".equals(destination)) setRoute(b);
              else open(b);
            } else if (destination != null) root(destination);
          }
          if (s.user == null
              && !Arrays.asList("LOGIN", "REGISTER").contains(current().getString("screen")))
            root("LOGIN");
          state.setValue(s);
          message.setValue(error == 0 ? success : error);
        });
  }

  public void refresh() {
    run(repository::refresh, 0, null);
  }

  public void open(Bundle b) {
    ArrayList<Bundle> stack = saved.get("stack");
    if (stack == null) stack = new ArrayList<>();
    stack.add(new Bundle(current()));
    saved.set("stack", stack);
    setRoute(b);
  }

  public void root(String screen) {
    saved.set("stack", new ArrayList<Bundle>());
    setRoute(screen(screen));
  }

  private void setRoute(Bundle b) {
    saved.set("route", b);
    route.setValue(b);
  }

  public boolean back() {
    ArrayList<Bundle> stack = saved.get("stack");
    if (stack == null || stack.isEmpty()) return false;
    Bundle b = stack.remove(stack.size() - 1);
    saved.set("stack", stack);
    setRoute(b);
    return true;
  }

  private boolean sensitive(String key) {
    return key.endsWith(".password") || key.endsWith(".confirm") || key.endsWith(".old");
  }

  public String input(String key, String fallback) {
    String v = sensitive(key) ? privateInputs.get(key) : saved.get("input." + key);
    return v == null ? fallback : v;
  }

  public void setInput(String key, String value) {
    if (sensitive(key)) privateInputs.put(key, value);
    else saved.set("input." + key, value);
  }

  public void clearForms() {
    privateInputs.clear();
    for (String key : new HashSet<>(saved.keys())) if (key.startsWith("input.")) saved.remove(key);
  }

  public void register(String name, String email, String password, String confirm) {
    run(
        cb -> repository.register(name, email, password, confirm, cb),
        R.string.registered,
        "LOGIN");
  }

  public void login(String email, String password) {
    run(cb -> repository.signIn(email, password, cb), 0, "HOME");
  }

  public void logout() {
    clearForms();
    run(repository::logout, 0, "LOGIN");
  }

  public void profile(String name) {
    run(cb -> repository.profile(name, cb), R.string.saved, null);
  }

  public void password(String old, String next, String confirm) {
    run(cb -> repository.password(old, next, confirm, cb), R.string.password_changed, "PROFILE");
  }

  public void settings(
      String theme, String language, int goal, boolean reminder, int hour, int minute) {
    run(
        cb -> repository.settings(theme, language, goal, reminder, hour, minute, cb),
        R.string.settings_saved,
        null);
  }

  public void star(int id) {
    run(cb -> repository.star(id, cb), 0, null);
  }

  public void collection(long id, String name) {
    run(cb -> repository.collection(id, name, cb), R.string.saved, "FAVORITES");
  }

  public void deleteCollection(long id) {
    run(cb -> repository.deleteCollection(id, cb), R.string.saved, "FAVORITES");
  }

  public void member(long id, int word, boolean add) {
    run(cb -> repository.member(id, word, add, cb), R.string.saved, null);
  }

  public void start(
      String mode, String type, long id, String name, String question, int count, String parent) {
    run(cb -> repository.start(mode, type, id, name, question, count, parent, cb), 0, "SESSION");
  }

  public void rate(String id, int index, boolean remember, long elapsed) {
    run(cb -> repository.rate(id, index, remember, elapsed, cb), 0, null);
  }

  public void finish(String id, long elapsed) {
    run(cb -> repository.finish(id, elapsed, cb), 0, "RESULT");
  }

  public void reset() {
    run(repository::reset, R.string.saved, "HOME");
  }

  public void checkpoint(String id, int index, boolean face, String answer, long elapsed) {
    repository.checkpoint(
        id,
        index,
        face,
        answer,
        elapsed,
        (s, error, opened) -> {
          latest = s;
          if (error != 0) message.setValue(error);
        });
  }

  public void move(String id, int index, boolean face, String answer, long elapsed) {
    run(cb -> repository.checkpoint(id, index, face, answer, elapsed, cb), 0, null);
  }
}
