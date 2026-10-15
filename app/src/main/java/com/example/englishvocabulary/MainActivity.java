package com.example.englishvocabulary;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.*;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.example.englishvocabulary.data.entity.*;
import com.example.englishvocabulary.domain.LearningRules;
import com.example.englishvocabulary.learning.*;
import com.example.englishvocabulary.learning.Records.*;
import com.example.englishvocabulary.learning.Records.Collection;
import com.example.englishvocabulary.ui.AppViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.*;
import java.text.DateFormat;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.json.JSONArray;

/** Views render state only; all persistence and business rules go through the ViewModel. */
public class MainActivity extends AppCompatActivity {
  private AppViewModel vm;
  private LinearLayout content;
  private ScrollView scroll;
  private RecyclerView list;
  private MaterialToolbar toolbar;
  private BottomNavigationView navigation;
  private TextView message;
  private View loading;
  private boolean selecting;
  private EditText answerField;
  private String selectedAnswer;
  private int displayedIndex;
  private boolean displayedFace;
  private String displayedSession;
  private long activeSince = -1;
  private boolean resumed;
  private final Handler checkpointHandler = new Handler(Looper.getMainLooper());
  private final Runnable heartbeat =
      new Runnable() {
        public void run() {
          if (resumed) {
            checkpoint();
            checkpointHandler.postDelayed(this, 15000);
          }
        }
      };
  private final Map<String, TextInputLayout> fields = new HashMap<>();
  private final Map<View, Boolean> temporarilyDisabled = new IdentityHashMap<>();
  private final ActivityResultLauncher<String> notificationPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            if (!granted) showMessage(R.string.permission_denied);
            else render(); // User can now explicitly save the reminder setting.
          });

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    vm = new ViewModelProvider(this).get(AppViewModel.class);
    content = findViewById(R.id.content);
    scroll = findViewById(R.id.scroll);
    list = findViewById(R.id.list);
    toolbar = findViewById(R.id.toolbar);
    navigation = findViewById(R.id.navigation);
    message = findViewById(R.id.message);
    loading = findViewById(R.id.loading);
    list.setLayoutManager(new LinearLayoutManager(this));
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    toolbar.setNavigationOnClickListener(v -> leave(this::goBack));
    navigation.setOnItemSelectedListener(
        item -> {
          if (selecting) return true;
          String destination =
              item.getItemId() == R.id.nav_home
                  ? "HOME"
                  : item.getItemId() == R.id.nav_topics
                      ? "TOPICS"
                      : item.getItemId() == R.id.nav_learning
                          ? "LEARNING"
                          : item.getItemId() == R.id.nav_favorites ? "FAVORITES" : "PROFILE";
          if (destination.equals(vm.current().getString("screen"))) return true;
          leave(() -> vm.root(destination));
          return false;
        });
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                leave(MainActivity.this::goBack);
              }
            });
    vm.state.observe(
        this,
        state -> {
          applyAppearance(state);
          ReminderScheduler.sync(MainActivity.this, state);
          render();
        });
    vm.route.observe(
        this,
        route -> {
          message.setVisibility(View.GONE);
          render();
        });
    vm.busy.observe(
        this,
        busy -> {
          loading.setVisibility(busy ? View.VISIBLE : View.GONE);
          enableButtons(content, !busy);
        });
    vm.message.observe(this, this::showMessage);
  }

  private void applyAppearance(AppState state) {
    String theme = state.user == null ? "SYSTEM" : state.settings.theme;
    int mode =
        "DARK".equals(theme)
            ? AppCompatDelegate.MODE_NIGHT_YES
            : "LIGHT".equals(theme)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    if (getDelegate().getLocalNightMode() != mode) getDelegate().setLocalNightMode(mode);
    String language = state.user == null ? "vi" : state.settings.language;
    if (!AppCompatDelegate.getApplicationLocales().toLanguageTags().equals(language))
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
  }

  private void goBack() {
    if (!vm.back()) {
      if (vm.data() != null && vm.data().user != null && !"HOME".equals(screen())) vm.root("HOME");
      else finish();
    }
  }

  private String screen() {
    return vm.current().getString("screen", "HOME");
  }

  private void leave(Runnable action) {
    if (Boolean.TRUE.equals(vm.busy.getValue())) return;
    if ("SESSION".equals(screen()))
      new MaterialAlertDialogBuilder(this)
          .setTitle(R.string.leave_session)
          .setMessage(R.string.leave_message)
          .setNegativeButton(R.string.cancel, null)
          .setPositiveButton(
              R.string.confirm,
              (d, w) -> {
                checkpoint();
                displayedSession = null;
                activeSince = -1;
                action.run();
              })
          .show();
    else action.run();
  }

  private void enableButtons(View view, boolean enabled) {
    if (view instanceof Button || view instanceof EditText || view instanceof Spinner) {
      View button = view;
      if (!enabled) {
        temporarilyDisabled.putIfAbsent(button, button.isEnabled());
        button.setEnabled(false);
      } else if (temporarilyDisabled.containsKey(button)) {
        button.setEnabled(temporarilyDisabled.remove(button));
      }
    }
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
        enableButtons(((ViewGroup) view).getChildAt(i), enabled);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent.getBooleanExtra("notificationHome", false)) leave(() -> vm.root("HOME"));
  }

  private void showMessage(int resource) {
    message.setVisibility(resource == 0 ? View.GONE : View.VISIBLE);
    if (resource == 0) return;
    message.setText(resource);
    String field =
        resource == R.string.error_email || resource == R.string.error_email_used
            ? "email"
            : resource == R.string.error_name || resource == R.string.error_duplicate_name
                ? "name"
                : resource == R.string.error_confirmation
                    ? "confirm"
                    : resource == R.string.error_goal
                        ? "goal"
                        : resource == R.string.error_password ? "password" : null;
    if (field != null && fields.containsKey(field)) fields.get(field).setError(getString(resource));
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(CharSequence value, boolean title) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextAppearance(
        title
            ? com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall
            : com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
    v.setTextColor(color(title ? R.color.ui_ink : R.color.ui_muted));
    v.setLineSpacing(dp(3), 1f);
    if (title) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    v.setPadding(0, dp(10), 0, dp(10));
    content.addView(v, new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  private TextView text(int resource) {
    return text(getString(resource), false);
  }

  private MaterialButton button(CharSequence label, Runnable action) {
    MaterialButton b = new MaterialButton(this);
    b.setText(label);
    b.setMinHeight(dp(54));
    b.setCornerRadius(dp(16));
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setInsetTop(0);
    b.setInsetBottom(0);
    b.setStrokeWidth(dp(1));
    b.setStrokeColor(ColorStateList.valueOf(color(R.color.ui_line)));
    b.setBackgroundTintList(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {color(R.color.ui_line), color(R.color.ui_card)}));
    b.setTextColor(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {color(R.color.ui_muted), color(R.color.ui_brand)}));
    b.setOnClickListener(
        v -> {
          if (!Boolean.TRUE.equals(vm.busy.getValue())) action.run();
        });
    LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
    buttonParams.topMargin = dp(8);
    buttonParams.bottomMargin = dp(4);
    content.addView(b, buttonParams);
    return b;
  }

  private MaterialButton button(int resource, Runnable action) {
    MaterialButton b = button(getString(resource), action);
    if (Arrays.asList(
            R.string.save,
            R.string.start,
            R.string.sign_in,
            R.string.register,
            R.string.submit,
            R.string.finish,
            R.string.remember)
        .contains(resource)) {
      b.setStrokeWidth(0);
      b.setBackgroundTintList(
          new ColorStateList(
              new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
              new int[] {color(R.color.ui_line), color(R.color.ui_brand)}));
      int onPrimary =
          com.google.android.material.color.MaterialColors.getColor(
              b, com.google.android.material.R.attr.colorOnPrimary);
      b.setTextColor(
          new ColorStateList(
              new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
              new int[] {color(R.color.ui_muted), onPrimary}));
    }
    if (resource == R.string.delete_collection
        || resource == R.string.reset_learning
        || resource == R.string.logout)
      b.setTextColor(
          com.google.android.material.color.MaterialColors.getColor(
              b, androidx.appcompat.R.attr.colorError));
    return b;
  }

  private EditText field(String key, int label, int inputType, String initial) {
    TextInputLayout wrapper = new TextInputLayout(this);
    wrapper.setHint(getString(label));
    wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    wrapper.setBoxCornerRadii(dp(16), dp(16), dp(16), dp(16));
    wrapper.setBoxBackgroundColor(color(R.color.ui_card));
    if ((inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD)
        == InputType.TYPE_TEXT_VARIATION_PASSWORD)
      wrapper.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
    TextInputEditText edit = new TextInputEditText(wrapper.getContext());
    edit.setInputType(inputType);
    edit.setMinHeight(dp(56));
    edit.setText(vm.input(screen() + "." + key, initial));
    wrapper.addView(edit, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
    fieldParams.topMargin = dp(10);
    fieldParams.bottomMargin = dp(6);
    content.addView(wrapper, fieldParams);
    fields.put(key, wrapper);
    final String inputKey = screen() + "." + key;
    edit.addTextChangedListener(
        new Watcher() {
          @Override
          public void afterTextChanged(Editable value) {
            vm.setInput(inputKey, value.toString());
            wrapper.setError(null);
          }
        });
    return edit;
  }

  private Spinner spinner(int title, List<String> labels, int selected) {
    text(title);
    Spinner spinner = new Spinner(this);
    spinner.setMinimumHeight(dp(48));
    spinner.setContentDescription(getString(title));
    spinner.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
    String spinnerKey = screen() + ".spinner." + getResources().getResourceEntryName(title);
    int position = Integer.parseInt(vm.input(spinnerKey, String.valueOf(selected)));
    spinner.setSelection(Math.max(0, Math.min(position, labels.size() - 1)));
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            vm.setInput(spinnerKey, String.valueOf(position));
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
    content.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
    return spinner;
  }

  private String value(EditText field) {
    return field.getText().toString();
  }

  private void render() {
    if (content == null || vm.data() == null) return;
    AppState s = vm.data();
    Bundle route = vm.current();
    String page = screen();
    content.removeAllViews();
    temporarilyDisabled.clear();
    fields.clear();
    answerField = null;
    selectedAnswer = null;
    list.setVisibility(View.GONE);
    scroll.setVisibility(View.VISIBLE);
    scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
    navigation.setVisibility(s.user == null ? View.GONE : View.VISIBLE);
    toolbar.setNavigationIcon(
        Arrays.asList("HOME", "TOPICS", "LEARNING", "FAVORITES", "PROFILE", "LOGIN").contains(page)
            ? null
            : androidx.appcompat.content.res.AppCompatResources.getDrawable(
                this, R.drawable.ic_back));
    if (toolbar.getNavigationIcon() != null) {
      toolbar.setNavigationContentDescription(R.string.back);
      toolbar
          .getNavigationIcon()
          .setTint(
              com.google.android.material.color.MaterialColors.getColor(
                  toolbar, com.google.android.material.R.attr.colorOnSurface));
    }
    selecting = true;
    String root = page;
    int selected = route.getInt("tab", 0);
    if (selected == 0)
      selected =
          "TOPICS".equals(root)
              ? R.id.nav_topics
              : "LEARNING".equals(root)
                      || "SESSION".equals(root)
                      || "SOURCE".equals(root)
                      || "QUIZ_CONFIG".equals(root)
                  ? R.id.nav_learning
                  : "FAVORITES".equals(root) || "MEMBERS".equals(root)
                      ? R.id.nav_favorites
                      : "PROFILE".equals(root)
                              || "SETTINGS".equals(root)
                              || "HISTORY".equals(root)
                              || "STATS".equals(root)
                              || "ACHIEVEMENTS".equals(root)
                              || "ABOUT".equals(root)
                          ? R.id.nav_profile
                          : R.id.nav_home;
    navigation.setSelectedItemId(selected);
    selecting = false;
    if (s.user == null) {
      auth("REGISTER".equals(page));
      return;
    }
    switch (page) {
      case "HOME":
        home(s);
        break;
      case "TOPICS":
        topics(s);
        break;
      case "WORDS":
        words(s, route, false);
        break;
      case "DETAIL":
        detail(s, route.getInt("wordId"));
        break;
      case "FAVORITES":
        favorites(s);
        break;
      case "MEMBERS":
        words(s, route, true);
        break;
      case "LEARNING":
        learning(s);
        break;
      case "SOURCE":
        sources(s, route);
        break;
      case "QUIZ_CONFIG":
        quizConfig(s, route);
        break;
      case "SESSION":
        session(s, route.getString("sessionId"));
        break;
      case "RESULT":
        result(s, route.getString("sessionId"));
        break;
      case "COLLECTION_FORM":
        collectionForm(route);
        break;
      case "PASSWORD":
        passwordScreen();
        break;
      case "PROFILE":
        profile(s);
        break;
      case "SETTINGS":
        settings(s);
        break;
      case "HISTORY":
        history(s);
        break;
      case "STATS":
        statistics(s);
        break;
      case "ACHIEVEMENTS":
        achievements(s);
        break;
      case "ABOUT":
        about();
        break;
      default:
        home(s);
    }
  }

  private void auth(boolean registration) {
    toolbar.setTitle(registration ? R.string.register : R.string.sign_in);
    text(getString(R.string.auth_headline), true).setTextSize(30);
    text(R.string.auth_caption);
    TextView local = text(R.string.local_accounts);
    local.setTextSize(12);
    EditText name =
        registration ? field("name", R.string.display_name, InputType.TYPE_CLASS_TEXT, "") : null;
    EditText email =
        field(
            "email",
            R.string.email,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            "");
    EditText password =
        field(
            "password",
            R.string.password,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            "");
    EditText confirm =
        registration
            ? field(
                "confirm",
                R.string.confirm_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                "")
            : null;
    button(
        registration ? R.string.register : R.string.sign_in,
        () -> {
          if (registration) vm.register(value(name), value(email), value(password), value(confirm));
          else vm.login(value(email), value(password));
        });
    button(
        registration ? R.string.sign_in : R.string.register,
        () -> vm.root(registration ? "LOGIN" : "REGISTER"));
    MaterialButton reload = button(R.string.retry, vm::refresh);
    reload.setStrokeWidth(0);
    reload.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
    reload.setTextSize(13);
  }

  private Bundle sourceRoute(String page, String type, long id, String name) {
    Bundle b = AppViewModel.screen(page);
    b.putString("sourceType", type);
    b.putLong("sourceId", id);
    b.putString("sourceName", name);
    b.putInt("tab", navigation.getSelectedItemId());
    return b;
  }

  private void openSource(String type, long id, String name) {
    vm.open(sourceRoute("WORDS", type, id, name));
  }

  private int color(int resource) {
    return ContextCompat.getColor(this, resource);
  }

  private TextView label(CharSequence value, int size, int color) {
    TextView text = new TextView(this);
    text.setText(value);
    text.setTextSize(size);
    text.setTextColor(color);
    text.setLineSpacing(dp(3), 1f);
    return text;
  }

  private LinearLayout vertical() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    return box;
  }

  private void space(LinearLayout parent, int height) {
    View gap = new View(this);
    parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
  }

  private void metric(LinearLayout parent, String value, int caption) {
    LinearLayout box = vertical();
    box.setPadding(dp(18), dp(18), dp(18), dp(18));
    box.setBackgroundResource(R.drawable.bg_soft);
    TextView number = label(value, 26, color(R.color.ui_on_brand_soft));
    number.setTypeface(Typeface.DEFAULT_BOLD);
    box.addView(number);
    space(box, 6);
    box.addView(label(getString(caption), 12, color(R.color.ui_on_brand_soft)));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
    params.setMarginEnd(dp(8));
    parent.addView(box, params);
  }

  private void tile(LinearLayout row, int title, int subtitle, int icon, Runnable action) {
    MaterialCardView card = new MaterialCardView(this);
    card.setRadius(dp(24));
    card.setCardElevation(0);
    card.setCardBackgroundColor(color(R.color.ui_card));
    card.setStrokeColor(color(R.color.ui_line));
    card.setStrokeWidth(dp(1));
    card.setClickable(true);
    card.setFocusable(true);
    LinearLayout box = vertical();
    box.setPadding(dp(18), dp(20), dp(16), dp(20));
    ImageView image = new ImageView(this);
    image.setImageResource(icon);
    image.setImageTintList(ColorStateList.valueOf(color(R.color.ui_brand)));
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    box.addView(image, new LinearLayout.LayoutParams(dp(28), dp(28)));
    space(box, 18);
    TextView heading = label(getString(title), 17, color(R.color.ui_ink));
    heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    box.addView(heading);
    space(box, 6);
    box.addView(label(getString(subtitle), 12, color(R.color.ui_muted)));
    card.addView(box);
    card.setContentDescription(getString(title) + ". " + getString(subtitle));
    card.setOnClickListener(
        v -> {
          if (!Boolean.TRUE.equals(vm.busy.getValue())) action.run();
        });
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
    params.setMarginEnd(dp(8));
    row.addView(card, params);
  }

  private LinearLayout tileRow() {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = dp(10);
    params.bottomMargin = dp(2);
    content.addView(row, params);
    return row;
  }

  private void home(AppState s) {
    toolbar.setTitle(R.string.app_name);
    TextView kicker = text(getString(R.string.home_kicker), false);
    kicker.setTextSize(10);
    kicker.setLetterSpacing(.12f);
    text(getString(R.string.greeting, s.user.name), true).setTextSize(25);
    text(R.string.home_caption);
    LinearLayout hero = vertical();
    hero.setPadding(dp(24), dp(24), dp(24), dp(24));
    hero.setBackgroundResource(R.drawable.bg_hero);
    TextView headline = label(getString(R.string.home_headline), 30, Color.WHITE);
    headline.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    hero.addView(headline);
    space(hero, 22);
    hero.addView(label(getString(R.string.daily_target), 13, 0xFFE5DFFF));
    space(hero, 6);
    TextView goal =
        label(getString(R.string.progress, s.today(), s.settings.dailyGoal), 17, Color.WHITE);
    hero.addView(goal);
    space(hero, 14);
    LinearProgressIndicator progress = new LinearProgressIndicator(this);
    progress.setMax(100);
    progress.setTrackCornerRadius(dp(4));
    progress.setTrackThickness(dp(7));
    progress.setIndicatorColor(Color.WHITE);
    progress.setTrackColor(0x447C6ACC);
    progress.setProgress((int) Math.min(100, 100L * s.today() / s.settings.dailyGoal));
    progress.setContentDescription(goal.getText());
    hero.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
    LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
    heroParams.topMargin = dp(12);
    heroParams.bottomMargin = dp(18);
    content.addView(hero, heroParams);
    LinearLayout metrics = new LinearLayout(this);
    metrics.setOrientation(LinearLayout.HORIZONTAL);
    metric(metrics, String.valueOf(s.learned()), R.string.learned_words);
    metric(metrics, String.valueOf(s.streak()), R.string.streak_days);
    content.addView(metrics);
    text(getString(R.string.quick_practice), true).setTextSize(20);
    LinearLayout first = tileRow();
    tile(
        first,
        R.string.flashcards,
        R.string.flash_hint,
        R.drawable.ic_learning,
        () -> chooseSource("FLASH"));
    tile(
        first, R.string.quiz, R.string.quiz_hint, R.drawable.ic_topics, () -> chooseSource("QUIZ"));
    LinearLayout second = tileRow();
    tile(
        second,
        R.string.topics,
        R.string.explore_hint,
        R.drawable.ic_topics,
        () -> vm.root("TOPICS"));
    tile(
        second,
        R.string.favorites,
        R.string.favorite_hint,
        R.drawable.ic_favorite,
        () -> vm.root("FAVORITES"));
    if (s.learned() == 0) text(R.string.start_hint).setTextSize(13);
    button(R.string.review, () -> openSource("REVIEW", 0, getString(R.string.review)));
    for (Session session : s.sessions)
      if ("IN_PROGRESS".equals(session.status))
        button(getString(R.string.pending_session, session.sourceName), () -> openSession(session));
  }

  private void openSession(Session s) {
    Bundle b = AppViewModel.screen("SESSION");
    b.putString("sessionId", s.sessionId);
    vm.open(b);
  }

  private static class Row {
    final String title, subtitle;
    final Runnable action;

    Row(String title, String subtitle, Runnable action) {
      this.title = title;
      this.subtitle = subtitle;
      this.action = action;
    }
  }

  private void listHeader() {
    content.measure(
        View.MeasureSpec.makeMeasureSpec(
            Math.max(dp(320), scroll.getWidth()), View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int max = Math.min(dp(240), getResources().getDisplayMetrics().heightPixels / 3);
    scroll.setLayoutParams(
        new LinearLayout.LayoutParams(-1, Math.min(content.getMeasuredHeight(), max)));
  }

  private void rows(List<Row> rows) {
    if (rows.isEmpty()) {
      text(R.string.empty);
      return;
    }
    listHeader();
    list.setVisibility(View.VISIBLE);
    list.setAdapter(new RowAdapter(rows));
  }

  private class RowAdapter extends RecyclerView.Adapter<RowHolder> {
    final List<Row> data;

    RowAdapter(List<Row> data) {
      this.data = data;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
      return new RowHolder(
          LayoutInflater.from(parent.getContext()).inflate(R.layout.row_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
      Row row = data.get(position);
      holder.marker.setText(
          row.title.isEmpty()
              ? ""
              : row.title
                  .substring(0, row.title.offsetByCodePoints(0, 1))
                  .toUpperCase(getResources().getConfiguration().getLocales().get(0)));
      holder.title.setText(row.title);
      holder.subtitle.setText(row.subtitle);
      holder.itemView.setContentDescription(row.title + ". " + row.subtitle);
      holder.itemView.setOnClickListener(
          v -> {
            if (!Boolean.TRUE.equals(vm.busy.getValue())) row.action.run();
          });
    }

    @Override
    public int getItemCount() {
      return data.size();
    }
  }

  private static class RowHolder extends RecyclerView.ViewHolder {
    final TextView title, subtitle, marker;

    RowHolder(View view) {
      super(view);
      marker = view.findViewById(R.id.row_marker);
      title = view.findViewById(R.id.row_title);
      subtitle = view.findViewById(R.id.row_subtitle);
    }
  }

  private void topics(AppState s) {
    toolbar.setTitle(R.string.topics);
    List<Row> data = new ArrayList<>();
    for (Topic t : s.topics)
      data.add(
          new Row(
              t.getTopicName(),
              getString(R.string.word_count, s.source("TOPIC", t.getTopicId()).size())
                  + " · "
                  + optional(t.getDescription()),
              () -> openSource("TOPIC", t.getTopicId(), t.getTopicName())));
    rows(data);
  }

  private String status(AppState s, int id) {
    int state = s.status(id).state;
    return getString(
        state == 2 ? R.string.mastered : state == 1 ? R.string.studying : R.string.not_studied);
  }

  private void words(AppState s, Bundle route, boolean manage) {
    String type = route.getString("sourceType", "ALL");
    long id = route.getLong("sourceId");
    String name = route.getString("sourceName", getString(R.string.all_words));
    toolbar.setTitle(name);
    if ("COLLECTION".equals(type) && s.collections.stream().noneMatch(c -> c.collectionId == id)) {
      text(R.string.error_source);
      return;
    }
    if (!manage) {
      button(R.string.flashcards, () -> startFlash(route));
      button(R.string.quiz, () -> vm.open(copyRoute(route, "QUIZ_CONFIG")));
      if ("COLLECTION".equals(type)) {
        button(R.string.manage_members, () -> vm.open(copyRoute(route, "MEMBERS")));
        button(R.string.rename, () -> collectionDialog(id, name));
        button(
            R.string.delete_collection,
            () ->
                new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_collection)
                    .setMessage(getString(R.string.delete_collection_message, name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm, (d, w) -> vm.deleteCollection(id))
                    .show());
      }
    }
    EditText search = field("search." + type + id, R.string.search, InputType.TYPE_CLASS_TEXT, "");
    Runnable update =
        () -> {
          String key = value(search).trim().toLowerCase(Locale.ROOT);
          List<Row> data = new ArrayList<>();
          for (Vocabulary w : manage ? s.words : s.source(type, id))
            if (w.getEnglishWord().toLowerCase(Locale.ROOT).contains(key)
                || w.getVietnameseMeaning().toLowerCase(Locale.ROOT).contains(key)) {
              boolean member =
                  s.members.stream()
                      .anyMatch(m -> m.collectionId == id && m.vocabularyId == w.getVocabularyId());
              String subtitle =
                  w.getVietnameseMeaning()
                      + " · "
                      + (manage
                          ? getString(member ? R.string.remove_member : R.string.add_member)
                          : status(s, w.getVocabularyId()))
                      + (s.status(w.getVocabularyId()).favorite ? " ★" : "");
              data.add(
                  new Row(
                      w.getEnglishWord(),
                      subtitle,
                      () -> {
                        if (manage) vm.member(id, w.getVocabularyId(), !member);
                        else {
                          Bundle b = AppViewModel.screen("DETAIL");
                          b.putInt("wordId", w.getVocabularyId());
                          b.putInt("tab", navigation.getSelectedItemId());
                          vm.open(b);
                        }
                      }));
            }
          // Keep the header and search field while replacing the list.
          list.setVisibility(View.VISIBLE);
          listHeader();
          list.setAdapter(
              new RowAdapter(
                  data.isEmpty()
                      ? Collections.singletonList(new Row(getString(R.string.empty), "", () -> {}))
                      : data));
        };
    search.addTextChangedListener(
        new Watcher() {
          @Override
          public void afterTextChanged(Editable e) {
            update.run();
          }
        });
    update.run();
  }

  private Bundle copyRoute(Bundle original, String page) {
    Bundle b = new Bundle(original);
    b.putString("screen", page);
    return b;
  }

  private String optional(String value) {
    return value == null || value.trim().isEmpty() ? getString(R.string.missing_info) : value;
  }

  private void detail(AppState s, int id) {
    Vocabulary w = s.word(id);
    toolbar.setTitle(R.string.select_word);
    if (w == null) {
      text(R.string.error_source);
      return;
    }
    text(w.getEnglishWord(), true);
    text(getString(R.string.meaning) + ": " + w.getVietnameseMeaning(), false);
    text(getString(R.string.pronunciation) + ": " + optional(w.getPronunciation()), false);
    text(getString(R.string.word_type) + ": " + optional(w.getWordType()), false);
    text(getString(R.string.example) + ": " + optional(w.getExampleSentence()), false);
    text(getString(R.string.topics) + ": " + optional(s.topicName(w.getTopicId())), false);
    text(status(s, id), false);
    button(s.status(id).favorite ? R.string.unstar : R.string.star, () -> vm.star(id));
    button(
        R.string.add_to_collection,
        () -> {
          if (s.collections.isEmpty()) {
            collectionDialog(0, "");
            return;
          }
          String[] names = new String[s.collections.size()];
          for (int i = 0; i < names.length; i++) names[i] = s.collections.get(i).name;
          new MaterialAlertDialogBuilder(this)
              .setTitle(R.string.add_to_collection)
              .setItems(names, (d, i) -> vm.member(s.collections.get(i).collectionId, id, true))
              .show();
        });
  }

  private void favorites(AppState s) {
    toolbar.setTitle(R.string.favorites);
    button(R.string.favorites, () -> openSource("FAVORITES", 0, getString(R.string.favorites)));
    button(R.string.create_collection, () -> collectionDialog(0, ""));
    text(R.string.collections);
    List<Row> data = new ArrayList<>();
    for (Collection c : s.collections)
      data.add(
          new Row(
              c.name,
              getString(R.string.word_count, s.source("COLLECTION", c.collectionId).size()),
              () -> openSource("COLLECTION", c.collectionId, c.name)));
    rows(data);
  }

  private void collectionDialog(long id, String initial) {
    Bundle b = AppViewModel.screen("COLLECTION_FORM");
    b.putLong("collectionId", id);
    b.putString("name", initial);
    vm.setInput("COLLECTION_FORM.name", initial);
    vm.open(b);
  }

  private void collectionForm(Bundle route) {
    long id = route.getLong("collectionId");
    toolbar.setTitle(id == 0 ? R.string.create_collection : R.string.rename);
    EditText name =
        field(
            "name",
            R.string.collection_name,
            InputType.TYPE_CLASS_TEXT,
            route.getString("name", ""));
    button(R.string.save, () -> vm.collection(id, value(name)));
  }

  private void learning(AppState s) {
    toolbar.setTitle(R.string.learning);
    button(R.string.flashcards, () -> chooseSource("FLASH"));
    button(R.string.quiz, () -> chooseSource("QUIZ"));
    button(R.string.review, () -> openSource("REVIEW", 0, getString(R.string.review)));
    for (Session session : s.sessions)
      if ("IN_PROGRESS".equals(session.status))
        button(getString(R.string.pending_session, session.sourceName), () -> openSession(session));
  }

  private void chooseSource(String mode) {
    Bundle b = AppViewModel.screen("SOURCE");
    b.putString("mode", mode);
    vm.open(b);
  }

  private void sources(AppState s, Bundle route) {
    toolbar.setTitle(R.string.choose_source);
    List<Row> rows = new ArrayList<>();
    java.util.function.Consumer<Bundle> choose =
        b -> {
          if ("FLASH".equals(route.getString("mode"))) startFlash(b);
          else vm.open(copyRoute(b, "QUIZ_CONFIG"));
        };
    rows.add(
        new Row(
            getString(R.string.all_words),
            getString(R.string.word_count, s.words.size()),
            () -> choose.accept(sourceRoute("WORDS", "ALL", 0, getString(R.string.all_words)))));
    rows.add(
        new Row(
            getString(R.string.favorites),
            getString(R.string.word_count, s.source("FAVORITES", 0).size()),
            () ->
                choose.accept(
                    sourceRoute("WORDS", "FAVORITES", 0, getString(R.string.favorites)))));
    for (Topic t : s.topics)
      rows.add(
          new Row(
              t.getTopicName(),
              getString(R.string.word_count, s.source("TOPIC", t.getTopicId()).size()),
              () ->
                  choose.accept(sourceRoute("WORDS", "TOPIC", t.getTopicId(), t.getTopicName()))));
    for (Collection c : s.collections)
      rows.add(
          new Row(
              c.name,
              getString(R.string.word_count, s.source("COLLECTION", c.collectionId).size()),
              () -> choose.accept(sourceRoute("WORDS", "COLLECTION", c.collectionId, c.name))));
    rows(rows);
  }

  private void startFlash(Bundle route) {
    vm.start(
        "FLASH",
        route.getString("sourceType"),
        route.getLong("sourceId"),
        route.getString("sourceName"),
        "",
        0,
        route.getString("parent"));
  }

  private void quizConfig(AppState s, Bundle route) {
    toolbar.setTitle(R.string.choose_quiz);
    text(getString(R.string.source_label, route.getString("sourceName")), false);
    Spinner type =
        spinner(
            R.string.quiz,
            Arrays.asList(
                getString(R.string.en_vi), getString(R.string.vi_en), getString(R.string.fill)),
            Integer.parseInt(vm.input("quiz.type", "0")));
    int available = s.source(route.getString("sourceType"), route.getLong("sourceId")).size();
    if (route.getString("parent") != null) {
      available = 0;
      for (Item i : s.items.getOrDefault(route.getString("parent"), Collections.emptyList()))
        if (Boolean.FALSE.equals(i.correct)) available++;
    }
    if (available == 0) {
      text(R.string.error_empty_source);
      return;
    }
    List<String> counts = new ArrayList<>();
    if (available < 5) counts.add(String.valueOf(available));
    else for (int n : new int[] {5, 10, 20}) if (n <= available) counts.add(String.valueOf(n));
    Spinner count = spinner(R.string.question_count, counts, 0);
    button(
        R.string.start,
        () -> {
          vm.setInput("quiz.type", String.valueOf(type.getSelectedItemPosition()));
          vm.start(
              "QUIZ",
              route.getString("sourceType"),
              route.getLong("sourceId"),
              route.getString("sourceName"),
              new String[] {"EN_VI", "VI_EN", "FILL"}[type.getSelectedItemPosition()],
              Integer.parseInt(count.getSelectedItem().toString()),
              route.getString("parent"));
        });
  }

  private void session(AppState s, String id) {
    Session session = s.session(id);
    if (session == null || !"IN_PROGRESS".equals(session.status)) {
      text(R.string.error_source);
      return;
    }
    if ("COLLECTION".equals(session.sourceType)
        && s.collections.stream().noneMatch(c -> c.collectionId == session.sourceId)) {
      text(R.string.error_source);
      return;
    }
    List<Item> items = s.items.get(id);
    if (items == null || items.isEmpty()) {
      text(R.string.error_source);
      return;
    }
    displayedSession = id;
    displayedIndex = session.currentIndex;
    displayedFace = session.cardFace;
    Item item = items.get(displayedIndex);
    toolbar.setTitle("FLASH".equals(session.mode) ? R.string.flashcards : R.string.quiz);
    text(getString(R.string.source_label, session.sourceName), false);
    text(
        getString(
            "FLASH".equals(session.mode) ? R.string.card_position : R.string.question_position,
            displayedIndex + 1,
            items.size()),
        false);
    if ("FLASH".equals(session.mode)) {
      TextView face = text(displayedFace ? item.meaning : item.word, true);
      text(R.string.card_hint).setTextSize(10);
      face.setMinHeight(dp(240));
      face.setGravity(Gravity.CENTER);
      face.setPadding(dp(24), dp(32), dp(24), dp(32));
      face.setTextSize(34);
      face.setTextColor(color(R.color.ui_on_brand_soft));
      face.setBackgroundResource(R.drawable.bg_soft);
      LinearLayout.LayoutParams faceParams = (LinearLayout.LayoutParams) face.getLayoutParams();
      faceParams.topMargin = dp(16);
      faceParams.bottomMargin = dp(12);
      face.setLayoutParams(faceParams);
      face.setContentDescription(getString(R.string.flip) + ". " + face.getText());
      face.setOnClickListener(
          v -> {
            if (!Boolean.TRUE.equals(vm.busy.getValue()))
              vm.move(id, displayedIndex, !displayedFace, null, takeElapsed());
          });
      if (displayedFace) {
        text(optional(item.pronunciation), false);
        text(optional(item.wordType), false);
        text(optional(item.example), false);
      }
      MaterialButton flip =
          button(
              R.string.flip,
              () -> vm.move(id, displayedIndex, !displayedFace, null, takeElapsed()));
      flip.setContentDescription(getString(R.string.flip));
      if (item.memoryRating != null) text(R.string.rated_already);
      button(R.string.remember, () -> vm.rate(id, displayedIndex, true, takeElapsed()))
          .setEnabled(displayedFace && item.memoryRating == null);
      button(R.string.need_review, () -> vm.rate(id, displayedIndex, false, takeElapsed()))
          .setEnabled(displayedFace && item.memoryRating == null);
    } else {
      text("EN_VI".equals(item.questionType) ? item.word : item.meaning, true);
      selectedAnswer = item.answer;
      if ("FILL".equals(item.questionType)) {
        answerField = new TextInputEditText(this);
        answerField.setInputType(
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        answerField.setHint(R.string.typed_answer);
        answerField.setMinHeight(dp(64));
        answerField.setPadding(dp(18), dp(12), dp(18), dp(12));
        answerField.setBackgroundResource(R.drawable.bg_soft);
        answerField.setText(item.answer);
        content.addView(answerField, new LinearLayout.LayoutParams(-1, -2));
        answerField.addTextChangedListener(
            new Watcher() {
              @Override
              public void afterTextChanged(Editable e) {
                selectedAnswer = e.toString();
                vm.checkpoint(id, displayedIndex, false, selectedAnswer, 0);
              }
            });
      } else {
        RadioGroup options = new RadioGroup(this);
        try {
          JSONArray array = new JSONArray(item.options);
          for (int i = 0; i < array.length(); i++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(View.generateViewId());
            choice.setText(array.getString(i));
            choice.setMinHeight(dp(60));
            choice.setPadding(dp(12), dp(8), dp(12), dp(8));
            choice.setBackgroundResource(R.drawable.bg_soft);
            RadioGroup.LayoutParams choiceParams = new RadioGroup.LayoutParams(-1, -2);
            choiceParams.topMargin = dp(8);
            choice.setLayoutParams(choiceParams);
            options.addView(choice);
            if (Objects.equals(item.answer, array.getString(i))) choice.setChecked(true);
          }
        } catch (org.json.JSONException e) {
          showMessage(R.string.error_storage);
          return;
        }
        options.setOnCheckedChangeListener(
            (group, checked) -> {
              RadioButton choice = group.findViewById(checked);
              if (choice != null) {
                selectedAnswer = choice.getText().toString();
                vm.checkpoint(id, displayedIndex, false, selectedAnswer, 0);
              }
            });
        content.addView(options);
      }
    }
    button(R.string.previous, () -> moveSession(-1)).setEnabled(displayedIndex > 0);
    if (displayedIndex + 1 < items.size()) button(R.string.next, () -> moveSession(1));
    button(
        "QUIZ".equals(session.mode) ? R.string.submit : R.string.finish,
        () -> {
          checkpoint();
          boolean blank = false;
          for (Item i : items) {
            String answer = i.itemIndex == displayedIndex ? selectedAnswer : i.answer;
            if (answer == null || answer.trim().isEmpty()) blank = true;
          }
          if ("QUIZ".equals(session.mode) && blank)
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.blank_answers)
                .setMessage(R.string.blank_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.submit, (d, w) -> vm.finish(id, takeElapsed()))
                .show();
          else vm.finish(id, takeElapsed());
        });
    if (resumed && activeSince < 0) activeSince = SystemClock.elapsedRealtime();
  }

  private void moveSession(int offset) {
    String id = displayedSession;
    int next = displayedIndex + offset;
    checkpoint();
    vm.move(id, next, false, null, takeElapsed());
  }

  private long takeElapsed() {
    if (!resumed || activeSince < 0) return 0;
    long now = SystemClock.elapsedRealtime();
    long elapsed = Math.max(0, now - activeSince);
    activeSince = now;
    return elapsed;
  }

  private void checkpoint() {
    if (displayedSession != null
        && "SESSION".equals(screen())
        && !Boolean.TRUE.equals(vm.busy.getValue()))
      vm.checkpoint(displayedSession, displayedIndex, displayedFace, selectedAnswer, takeElapsed());
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    checkpointHandler.removeCallbacks(heartbeat);
    checkpointHandler.postDelayed(heartbeat, 15000);
    if (vm != null && "SESSION".equals(screen())) activeSince = SystemClock.elapsedRealtime();
    else if (vm != null && vm.data() != null && !Boolean.TRUE.equals(vm.busy.getValue()))
      vm.refresh();
  }

  @Override
  protected void onPause() {
    checkpoint();
    resumed = false;
    checkpointHandler.removeCallbacks(heartbeat);
    activeSince = -1;
    super.onPause();
  }

  private String duration(long ms) {
    return getString(R.string.duration, ms / 60_000, (ms / 1000) % 60);
  }

  private String date(long epoch) {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(new Date(epoch));
  }

  private void result(AppState s, String id) {
    toolbar.setTitle(R.string.results);
    Session session = s.session(id);
    if (session == null) {
      text(R.string.error_source);
      return;
    }
    displayedSession = null;
    activeSince = -1;
    List<Item> items = s.items.get(id);
    text(getString(R.string.source_label, session.sourceName), false);
    if ("QUIZ".equals(session.mode)) {
      text(
          getString(
              R.string.quiz_summary,
              session.correctAnswers,
              session.totalQuestions,
              LearningRules.percent(session.correctAnswers, session.totalQuestions),
              session.totalQuestions - session.correctAnswers,
              duration(session.activeDurationMs)),
          true);
      boolean wrong = items.stream().anyMatch(i -> Boolean.FALSE.equals(i.correct));
      button(
              R.string.retry_wrong,
              () -> {
                Bundle b = sourceRoute("WORDS", "RETRY", 0, getString(R.string.retry_wrong));
                b.putString("parent", id);
                new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.retry_wrong)
                    .setItems(
                        new String[] {getString(R.string.flashcards), getString(R.string.quiz)},
                        (d, which) -> {
                          if (which == 0) startFlash(b);
                          else vm.open(copyRoute(b, "QUIZ_CONFIG"));
                        })
                    .show();
              })
          .setEnabled(wrong);
      for (Item i : items)
        text(
            getString(Boolean.TRUE.equals(i.correct) ? R.string.correct : R.string.incorrect)
                + " · "
                + getString(
                    R.string.answer_review,
                    "EN_VI".equals(i.questionType) ? i.word : i.meaning,
                    optionalAnswer(i.answer),
                    i.expected),
            false);
    } else {
      int remembered = 0, review = 0;
      for (Item i : items) {
        if ("REMEMBER".equals(i.memoryRating)) remembered++;
        if ("REVIEW".equals(i.memoryRating)) review++;
      }
      text(
          getString(
              R.string.flash_summary,
              remembered + review,
              remembered,
              review,
              duration(session.activeDurationMs)),
          true);
    }
    button(R.string.home, () -> vm.root("HOME"));
  }

  private String optionalAnswer(String answer) {
    return answer == null || answer.trim().isEmpty() ? getString(R.string.unanswered) : answer;
  }

  private void profile(AppState s) {
    toolbar.setTitle(R.string.profile);
    TextView avatar =
        text(
            s.user
                .name
                .substring(0, s.user.name.offsetByCodePoints(0, 1))
                .toUpperCase(getResources().getConfiguration().getLocales().get(0)),
            true);
    avatar.setBackgroundResource(R.drawable.bg_avatar);
    avatar.setGravity(Gravity.CENTER);
    avatar.setTextColor(color(R.color.ui_brand));
    avatar.setTextSize(30);
    avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(80), dp(80)));
    text(s.user.name, true);
    text(R.string.profile_caption);
    text(s.user.email, false);
    EditText name = field("name", R.string.display_name, InputType.TYPE_CLASS_TEXT, s.user.name);
    button(R.string.edit_profile, () -> vm.profile(value(name)));
    button(R.string.change_password, () -> passwordDialog());
    button(R.string.history, () -> vm.open(AppViewModel.screen("HISTORY")));
    button(R.string.achievements, () -> vm.open(AppViewModel.screen("ACHIEVEMENTS")));
    button(R.string.statistics, () -> vm.open(AppViewModel.screen("STATS")));
    button(R.string.settings, () -> vm.open(AppViewModel.screen("SETTINGS")));
    button(R.string.about, () -> vm.open(AppViewModel.screen("ABOUT")));
    button(
        R.string.logout,
        () ->
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> vm.logout())
                .show());
  }

  private void passwordDialog() {
    Bundle b = AppViewModel.screen("PASSWORD");
    vm.open(b);
  }

  private void passwordScreen() {
    content.removeAllViews();
    toolbar.setTitle(R.string.change_password);
    EditText old =
        field(
            "old",
            R.string.current_password,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            "");
    EditText next =
        field(
            "password",
            R.string.new_password,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            "");
    EditText confirm =
        field(
            "confirm",
            R.string.confirm_password,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            "");
    button(R.string.save, () -> vm.password(value(old), value(next), value(confirm)));
  }

  private void history(AppState s) {
    toolbar.setTitle(R.string.history);
    Spinner mode =
        spinner(
            R.string.filter_mode,
            Arrays.asList(
                getString(R.string.all), getString(R.string.flashcards), getString(R.string.quiz)),
            Integer.parseInt(vm.input("history.mode", "0")));
    EditText from = field("from", R.string.from_date, InputType.TYPE_CLASS_TEXT, "");
    EditText to = field("to", R.string.to_date, InputType.TYPE_CLASS_TEXT, "");
    Runnable update =
        () -> {
          LocalDate first = LocalDate.MIN, last = LocalDate.MAX;
          try {
            if (!value(from).trim().isEmpty()) first = LocalDate.parse(value(from).trim());
            if (!value(to).trim().isEmpty()) last = LocalDate.parse(value(to).trim());
            if (first.isAfter(last)) throw new IllegalArgumentException();
          } catch (Exception e) {
            showMessage(R.string.date_error);
            return;
          }
          vm.setInput("history.mode", String.valueOf(mode.getSelectedItemPosition()));
          List<Row> data = new ArrayList<>();
          for (Session session : s.completed()) {
            LocalDate day =
                Instant.ofEpochMilli(session.completedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            int m = mode.getSelectedItemPosition();
            if (day.isBefore(first)
                || day.isAfter(last)
                || (m == 1 && !"FLASH".equals(session.mode))
                || (m == 2 && !"QUIZ".equals(session.mode))) continue;
            String result =
                "QUIZ".equals(session.mode)
                    ? String.format(
                        getResources().getConfiguration().getLocales().get(0),
                        "%.1f%%",
                        LearningRules.percent(session.correctAnswers, session.totalQuestions))
                    : getString(R.string.word_count, s.rated(session.sessionId));
            data.add(
                new Row(
                    getString("QUIZ".equals(session.mode) ? R.string.quiz : R.string.flashcards)
                        + " · "
                        + session.sourceName,
                    date(session.completedAt)
                        + " · "
                        + duration(session.activeDurationMs)
                        + " · "
                        + result,
                    () -> {
                      Bundle b = AppViewModel.screen("RESULT");
                      b.putString("sessionId", session.sessionId);
                      vm.open(b);
                    }));
          }
          list.setAdapter(
              new RowAdapter(
                  data.isEmpty()
                      ? Collections.singletonList(new Row(getString(R.string.empty), "", () -> {}))
                      : data));
          listHeader();
          list.setVisibility(View.VISIBLE);
        };
    button(R.string.apply_filter, update);
    update.run();
  }

  private void statistics(AppState s) {
    toolbar.setTitle(R.string.statistics);
    text(getString(R.string.stats_counts, s.learned(), s.mastered(), s.quizCount()), true);
    if (s.quizCount() == 0) text(R.string.no_scores);
    else text(getString(R.string.stats_scores, s.average(), s.accuracy()), false);
    LocalDate today = LocalDate.now(),
        monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    text(
        getString(
            R.string.stats_time,
            duration(s.duration(today, today)),
            duration(s.duration(monday, monday.plusDays(6)))),
        false);
    for (int i = 0; i < 7; i++) {
      LocalDate day = monday.plusDays(i);
      text(day + " · " + duration(s.duration(day, day)), false);
    }
  }

  private void achievements(AppState s) {
    toolbar.setTitle(R.string.achievements);
    text(getString(R.string.streak_summary, s.streak(), s.longest()), true);
    String[] codes = {"START", "WORDS_50", "STREAK_7", "PERFECT"};
    int[] labels = {
      R.string.badge_start, R.string.badge_words, R.string.badge_streak, R.string.badge_perfect
    };
    for (int i = 0; i < codes.length; i++) {
      String code = codes[i];
      Achievement found = null;
      for (Achievement a : s.achievements) if (a.code.equals(code)) found = a;
      text(labels[i]);
      text(
          found == null
              ? getString(R.string.not_earned)
              : getString(R.string.earned, date(found.achievedAt)),
          false);
    }
  }

  private void settings(AppState s) {
    toolbar.setTitle(R.string.settings);
    Spinner theme =
        spinner(
            R.string.theme,
            Arrays.asList(
                getString(R.string.system), getString(R.string.light), getString(R.string.dark)),
            "LIGHT".equals(s.settings.theme) ? 1 : "DARK".equals(s.settings.theme) ? 2 : 0);
    Spinner language =
        spinner(
            R.string.language,
            Arrays.asList(getString(R.string.language_vi), getString(R.string.language_en)),
            "en".equals(s.settings.language) ? 1 : 0);
    EditText goal =
        field(
            "goal",
            R.string.daily_goal,
            InputType.TYPE_CLASS_NUMBER,
            String.valueOf(s.settings.dailyGoal));
    SwitchMaterial reminder = new SwitchMaterial(this);
    reminder.setText(R.string.reminder);
    reminder.setMinHeight(dp(48));
    reminder.setChecked(
        Boolean.parseBoolean(vm.input("SETTINGS.reminder", String.valueOf(s.settings.reminder))));
    content.addView(reminder);
    final int[] time = {
      Integer.parseInt(vm.input("SETTINGS.hour", String.valueOf(s.settings.hour))),
      Integer.parseInt(vm.input("SETTINGS.minute", String.valueOf(s.settings.minute)))
    };
    MaterialButton clock =
        button(
            getString(R.string.reminder_time)
                + " · "
                + String.format(Locale.ROOT, "%02d:%02d", time[0], time[1]),
            () ->
                new android.app.TimePickerDialog(
                        this,
                        (view, h, m) -> {
                          time[0] = h;
                          time[1] = m;
                          vm.setInput("SETTINGS.hour", String.valueOf(h));
                          vm.setInput("SETTINGS.minute", String.valueOf(m));
                          renderTimeButton(h, m);
                        },
                        time[0],
                        time[1],
                        true)
                    .show());
    clock.setTag("clock");
    reminder.setOnCheckedChangeListener(
        (button, checked) -> {
          vm.setInput("SETTINGS.reminder", String.valueOf(checked));
          if (checked && !ReminderScheduler.permitted(this)) {
            reminder.setChecked(false);
            if (Build.VERSION.SDK_INT >= 33)
              notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            else showMessage(R.string.permission_denied);
          }
        });
    button(
        R.string.save,
        () -> {
          int n;
          try {
            n = Integer.parseInt(value(goal));
          } catch (NumberFormatException e) {
            showMessage(R.string.error_goal);
            return;
          }
          if (n < 1 || n > 100) {
            showMessage(R.string.error_goal);
            return;
          }
          boolean enabled = reminder.isChecked();
          if (enabled && !ReminderScheduler.permitted(this)) {
            showMessage(R.string.permission_denied);
            return;
          }
          vm.settings(
              new String[] {"SYSTEM", "LIGHT", "DARK"}[theme.getSelectedItemPosition()],
              language.getSelectedItemPosition() == 0 ? "vi" : "en",
              n,
              enabled,
              time[0],
              time[1]);
        });
    button(
        R.string.open_app_settings,
        () ->
            startActivity(
                new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    button(
        R.string.reset_learning,
        () ->
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_learning)
                .setMessage(R.string.reset_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> vm.reset())
                .show());
  }

  private void renderTimeButton(int h, int m) {
    MaterialButton clock = content.findViewWithTag("clock");
    if (clock != null)
      clock.setText(
          getString(R.string.reminder_time)
              + " · "
              + String.format(Locale.ROOT, "%02d:%02d", h, m));
  }

  private void about() {
    toolbar.setTitle(R.string.about);
    text(R.string.app_name);
    try {
      text(
          getString(
              R.string.version,
              getPackageManager().getPackageInfo(getPackageName(), 0).versionName),
          false);
    } catch (PackageManager.NameNotFoundException e) {
      showMessage(R.string.error_storage);
    }
    text(R.string.about_description);
    text(R.string.team_pending);
  }

  private abstract static class Watcher implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}
  }
}
