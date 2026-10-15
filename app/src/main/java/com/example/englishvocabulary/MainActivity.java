package com.example.englishvocabulary;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import com.example.englishvocabulary.ui.LessonProgressView;
import com.example.englishvocabulary.ui.PlayfulUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
  private PlayfulUi ui;
  private LinearLayout content;
  private ScrollView scroll;
  private RecyclerView list;
  private MaterialToolbar toolbar;
  private BottomNavigationView navigation;
  private TextView message;
  private View loading;
  private boolean selecting;
  private String renderedKey = "";
  private int scrollTarget;
  private boolean restoringScroll;
  private final Runnable restoreScroll =
      () -> {
        if (scroll != null) {
          scroll.scrollTo(0, scrollTarget);
          restoringScroll = false;
        }
      };
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
    ui = new PlayfulUi(this);
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
    boolean success =
        Arrays.asList(
                R.string.saved,
                R.string.registered,
                R.string.password_changed,
                R.string.settings_saved)
            .contains(resource);
    message.setTextColor(color(success ? R.color.ui_brand : R.color.ui_red));
    message.setBackgroundResource(success ? R.drawable.bg_soft : R.drawable.bg_error);
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
    TextView view = ui.label(value, title ? 24 : 15, title);
    view.setPadding(0, dp(8), 0, dp(8));
    content.addView(view, new LinearLayout.LayoutParams(-1, -2));
    return view;
  }

  private TextView text(int resource) {
    return text(getString(resource), false);
  }

  private void intro(int title, int caption, int icon) {
    content.addView(ui.intro(title, caption, icon), ui.spaced());
  }

  private void section(int title) {
    TextView view = text(getString(title), true);
    view.setTextSize(19);
    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
    params.topMargin = dp(14);
    view.setLayoutParams(params);
  }

  private void info(int title, String value, int background) {
    content.addView(ui.info(getString(title), value, background), ui.spaced());
  }

  private MaterialButton button(CharSequence label, Runnable action) {
    MaterialButton button = new MaterialButton(this);
    button.setText(label);
    ui.button(button, false);
    button.setOnClickListener(
        v -> {
          if (!Boolean.TRUE.equals(vm.busy.getValue())) action.run();
        });
    content.addView(button, ui.spaced());
    return button;
  }

  private MaterialButton button(int resource, Runnable action) {
    MaterialButton button = button(getString(resource), action);
    ui.button(
        button,
        Arrays.asList(
                R.string.save,
                R.string.start,
                R.string.sign_in,
                R.string.register,
                R.string.submit,
                R.string.finish,
                R.string.remember,
                R.string.start_lesson)
            .contains(resource));
    if (Arrays.asList(R.string.delete_collection, R.string.reset_learning, R.string.logout)
        .contains(resource)) button.setTextColor(color(R.color.ui_red));
    return button;
  }

  private MaterialButton menu(int title, int icon, Runnable action) {
    MaterialButton b = button(title, action);
    b.setIconResource(icon);
    b.setIconTint(ColorStateList.valueOf(color(R.color.ui_brand)));
    b.setIconPadding(dp(14));
    b.setIconSize(dp(24));
    b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    return b;
  }

  private void pair(MaterialButton first, MaterialButton second) {
    content.removeView(first);
    content.removeView(second);
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1);
    a.setMarginEnd(dp(6));
    row.addView(first, a);
    LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -2, 1);
    b.setMarginStart(dp(6));
    row.addView(second, b);
    content.addView(row, ui.spaced());
  }

  private void empty() {
    LinearLayout box = ui.panel(R.drawable.bg_soft);
    box.setGravity(Gravity.CENTER);
    ImageView illustration = new ImageView(this);
    illustration.setImageResource(R.drawable.companion_fox);
    illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    box.addView(illustration, new LinearLayout.LayoutParams(dp(112), dp(112)));
    TextView title = ui.label(getString(R.string.empty_title), 21, true);
    title.setGravity(Gravity.CENTER);
    box.addView(title);
    TextView caption = ui.label(getString(R.string.empty_caption), 14, false);
    caption.setGravity(Gravity.CENTER);
    box.addView(caption, ui.spaced());
    content.addView(box, ui.spaced());
  }

  private EditText field(String key, int label, int inputType, String initial) {
    TextInputLayout wrapper = new TextInputLayout(this);
    wrapper.setHint(getString(label));
    wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    wrapper.setBoxCornerRadii(dp(16), dp(16), dp(16), dp(16));
    wrapper.setBoxBackgroundColor(color(R.color.ui_disabled));
    wrapper.setBoxStrokeColor(color(R.color.ui_brand));
    wrapper.setBoxStrokeWidth(dp(2));
    wrapper.setBoxStrokeWidthFocused(dp(2));
    if ((inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD)
        == InputType.TYPE_TEXT_VARIATION_PASSWORD)
      wrapper.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
    TextInputEditText edit = new TextInputEditText(wrapper.getContext());
    edit.setInputType(inputType);
    edit.setMinHeight(dp(58));
    edit.setTextColor(color(R.color.ui_ink));
    edit.setTextSize(17);
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
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_value, labels);
    adapter.setDropDownViewResource(R.layout.spinner_dropdown);
    spinner.setAdapter(adapter);
    spinner.setBackgroundResource(R.drawable.button_outline);
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
    String nextKey =
        page
            + "/"
            + route.getString("sourceType", "")
            + "/"
            + route.getLong("sourceId")
            + "/"
            + route.getInt("wordId");
    if ("SESSION".equals(page)) {
      Session active = s.session(route.getString("sessionId"));
      if (active != null)
        nextKey += "/" + active.sessionId + "/" + active.currentIndex + "/" + active.cardFace;
    }
    boolean changed = !renderedKey.equals(nextKey);
    if (changed) scrollTarget = 0;
    else if (!restoringScroll) scrollTarget = scroll.getScrollY();
    renderedKey = nextKey;
    restoringScroll = true;
    scroll.removeCallbacks(restoreScroll);
    scroll.post(restoreScroll);
    if (changed) {
      content.setFocusableInTouchMode(true);
      content.requestFocus();
    }

    content.removeAllViews();
    temporarilyDisabled.clear();
    fields.clear();
    answerField = null;
    selectedAnswer = null;
    list.setVisibility(View.GONE);
    scroll.setVisibility(View.VISIBLE);
    scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
    navigation.setVisibility(s.user == null || "SESSION".equals(page) ? View.GONE : View.VISIBLE);
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
                              || "EDIT_PROFILE".equals(root)
                              || "PASSWORD".equals(root)
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
      case "EDIT_PROFILE":
        profileEditor(s);
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
    LinearLayout welcome = ui.column();
    welcome.setGravity(Gravity.CENTER);
    ImageView fox = new ImageView(this);
    fox.setImageResource(R.drawable.companion_fox);
    fox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    welcome.addView(fox, new LinearLayout.LayoutParams(dp(120), dp(120)));
    TextView headline = ui.label(getString(R.string.auth_welcome), 28, true);
    headline.setGravity(Gravity.CENTER);
    welcome.addView(headline);
    TextView caption = ui.label(getString(R.string.auth_support), 14, false);
    caption.setGravity(Gravity.CENTER);
    welcome.addView(caption, ui.spaced());
    content.addView(welcome, ui.spaced());
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
    MaterialButton alternate =
        button(
            registration ? R.string.sign_in : R.string.register,
            () -> vm.root(registration ? "LOGIN" : "REGISTER"));
    ui.button(alternate, false);
    TextView privacy = text(R.string.local_accounts);
    privacy.setTextSize(12);
    privacy.setGravity(Gravity.CENTER);
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
    box.setBackgroundResource(R.drawable.button_outline);
    TextView number = label(value, 26, color(R.color.ui_on_brand_soft));
    number.setTypeface(ui.bold());
    box.addView(number);
    space(box, 6);
    box.addView(label(getString(caption), 12, color(R.color.ui_on_brand_soft)));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
    params.setMarginEnd(dp(8));
    parent.addView(box, params);
  }

  private void tile(LinearLayout row, int title, int subtitle, int icon, Runnable action) {
    MaterialCardView card = new MaterialCardView(this);
    card.setRadius(dp(20));
    card.setCardElevation(0);
    card.setCardBackgroundColor(color(R.color.ui_card));
    card.setStrokeColor(color(R.color.ui_line));
    card.setStrokeWidth(dp(2));
    card.setClickable(true);
    card.setFocusable(true);
    LinearLayout box = vertical();
    box.setPadding(dp(16), dp(18), dp(14), dp(20));
    box.setBackgroundResource(
        icon == R.drawable.ic_spark ? R.drawable.bg_blue : R.drawable.bg_soft);
    ImageView image = new ImageView(this);
    image.setImageResource(icon);
    image.setImageTintList(ColorStateList.valueOf(color(R.color.ui_brand)));
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    box.addView(image, new LinearLayout.LayoutParams(dp(28), dp(28)));
    space(box, 18);
    TextView heading = label(getString(title), 17, color(R.color.ui_ink));
    heading.setTypeface(ui.bold());
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
    LinearLayout chips = new LinearLayout(this);
    metric(chips, String.valueOf(s.streak()), R.string.streak_days);
    metric(chips, String.valueOf(s.learned()), R.string.learned_words);
    content.addView(chips, ui.spaced());
    TextView greeting = text(getString(R.string.greeting, s.user.name), true);
    greeting.setTextSize(24);
    intro(R.string.companion_message, R.string.home_caption, R.drawable.companion_fox);
    LinearLayout goal = ui.panel(R.drawable.button_outline);
    TextView title = ui.label(getString(R.string.daily_target), 18, true);
    goal.addView(title);
    TextView count =
        ui.label(getString(R.string.progress_short, s.today(), s.settings.dailyGoal), 14, false);
    goal.addView(count);
    LessonProgressView bar = progress(s.today(), s.settings.dailyGoal);
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(16));
    bp.topMargin = dp(14);
    goal.addView(bar, bp);
    content.addView(goal, ui.spaced());
    button(R.string.start_lesson, () -> vm.root("TOPICS"));
    section(R.string.quick_practice);
    LinearLayout modes = tileRow();
    tile(
        modes,
        R.string.flashcards,
        R.string.flash_hint,
        R.drawable.ic_book,
        () -> chooseSource("FLASH"));
    tile(modes, R.string.quiz, R.string.quiz_hint, R.drawable.ic_spark, () -> chooseSource("QUIZ"));
    menu(
        R.string.review,
        R.drawable.ic_flame,
        () -> openSource("REVIEW", 0, getString(R.string.review)));
    for (Session session : s.sessions)
      if ("IN_PROGRESS".equals(session.status))
        button(getString(R.string.pending_session, session.sourceName), () -> openSession(session));
  }

  private LessonProgressView progress(int value, int total) {
    LessonProgressView bar = new LessonProgressView(this, value, total);
    bar.setContentDescription(getString(R.string.progress_short, value, total));
    return bar;
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
      empty();
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
      boolean vocabulary = Arrays.asList("WORDS", "MEMBERS").contains(screen());
      holder.marker.setVisibility(vocabulary ? View.VISIBLE : View.GONE);
      holder.icon.setVisibility(vocabulary ? View.GONE : View.VISIBLE);
      holder.icon.setImageResource(
          "HISTORY".equals(screen())
              ? R.drawable.ic_check
              : "FAVORITES".equals(screen())
                  ? R.drawable.ic_favorite
                  : new int[] {
                        R.drawable.ic_book,
                        R.drawable.ic_globe,
                        R.drawable.ic_spark,
                        R.drawable.ic_learning
                      }
                      [position % 4]);
      holder.kicker.setVisibility("TOPICS".equals(screen()) ? View.VISIBLE : View.GONE);
      if ("TOPICS".equals(screen()))
        holder.kicker.setText(getString(R.string.unit_label, position + 1));
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
    final TextView title, subtitle, marker, kicker;
    final ImageView icon;

    RowHolder(View view) {
      super(view);
      marker = view.findViewById(R.id.row_marker);
      kicker = view.findViewById(R.id.row_kicker);
      icon = view.findViewById(R.id.row_icon);
      title = view.findViewById(R.id.row_title);
      subtitle = view.findViewById(R.id.row_subtitle);
    }
  }

  private void topics(AppState s) {
    toolbar.setTitle(R.string.topics);
    intro(R.string.path_title, R.string.path_hint, R.drawable.ic_globe);
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
      pair(
          button(R.string.flashcards, () -> startFlash(route)),
          button(R.string.quiz, () -> vm.open(copyRoute(route, "QUIZ_CONFIG"))));
      if ("COLLECTION".equals(type))
        menu(
            R.string.manage_collection,
            R.drawable.ic_topics,
            () ->
                new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.manage_collection)
                    .setItems(
                        new String[] {
                          getString(R.string.manage_members),
                          getString(R.string.rename),
                          getString(R.string.delete_collection)
                        },
                        (dialog, which) -> {
                          if (which == 0) vm.open(copyRoute(route, "MEMBERS"));
                          else if (which == 1) collectionDialog(id, name);
                          else
                            new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_collection)
                                .setMessage(getString(R.string.delete_collection_message, name))
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(
                                    R.string.confirm, (d, w) -> vm.deleteCollection(id))
                                .show();
                        })
                    .show());
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
    LinearLayout word = ui.panel(R.drawable.bg_soft);
    TextView english = ui.label(w.getEnglishWord(), 32, true);
    english.setGravity(Gravity.CENTER);
    word.addView(english);
    TextView pronunciation = ui.label(optional(w.getPronunciation()), 16, false);
    pronunciation.setGravity(Gravity.CENTER);
    word.addView(pronunciation);
    TextView meaning = ui.label(w.getVietnameseMeaning(), 22, true);
    meaning.setGravity(Gravity.CENTER);
    word.addView(meaning, ui.spaced());
    content.addView(word, ui.spaced());
    info(R.string.word_type, optional(w.getWordType()), R.drawable.button_outline);
    info(R.string.example, optional(w.getExampleSentence()), R.drawable.bg_blue);
    info(R.string.topics, optional(s.topicName(w.getTopicId())), R.drawable.button_outline);
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
    text(getString(R.string.collections), true).setTextSize(22);
    text(R.string.collection_hint).setTextSize(13);
    pair(
        button(R.string.favorites, () -> openSource("FAVORITES", 0, getString(R.string.favorites))),
        button(R.string.create_collection, () -> collectionDialog(0, "")));
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
    intro(R.string.collections, R.string.collection_hint, R.drawable.ic_book);
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
    intro(R.string.practice_title, R.string.practice_hint, R.drawable.companion_fox);
    section(R.string.quick_practice);
    LinearLayout modes = tileRow();
    tile(
        modes,
        R.string.flashcards,
        R.string.flash_hint,
        R.drawable.ic_book,
        () -> chooseSource("FLASH"));
    tile(modes, R.string.quiz, R.string.quiz_hint, R.drawable.ic_spark, () -> chooseSource("QUIZ"));
    menu(
        R.string.review,
        R.drawable.ic_flame,
        () -> openSource("REVIEW", 0, getString(R.string.review)));
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
    intro(R.string.choose_source, R.string.source_hint, R.drawable.ic_book);
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
    intro(R.string.quiz, R.string.quiz_setup_hint, R.drawable.ic_spark);
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
    content.addView(
        progress(displayedIndex + 1, items.size()), new LinearLayout.LayoutParams(-1, dp(16)));
    text(getString(R.string.source_label, session.sourceName), false);
    text(
        getString(
            "FLASH".equals(session.mode) ? R.string.card_position : R.string.question_position,
            displayedIndex + 1,
            items.size()),
        false);
    if ("FLASH".equals(session.mode)) {
      LinearLayout studyCard = ui.panel(R.drawable.button_outline);
      TextView cardLabel =
          ui.label(
              getString(displayedFace ? R.string.flash_back : R.string.flash_front), 11, false);
      cardLabel.setGravity(Gravity.CENTER);
      studyCard.addView(cardLabel);
      TextView face = ui.label(displayedFace ? item.meaning : item.word, 34, true);
      face.setTextColor(color(R.color.ui_on_brand_soft));
      face.setGravity(Gravity.CENTER);
      face.setMinHeight(dp(displayedFace ? 110 : 180));
      studyCard.addView(face, new LinearLayout.LayoutParams(-1, -2));
      if (displayedFace) {
        int[] labels = {R.string.pronunciation, R.string.word_type, R.string.example};
        String[] details = {item.pronunciation, item.wordType, item.example};
        for (int n = 0; n < labels.length; n++) {
          TextView key = ui.label(getString(labels[n]), 11, false);
          studyCard.addView(key);
          TextView value = ui.label(optional(details[n]), 14, false);
          LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
          params.bottomMargin = dp(8);
          studyCard.addView(value, params);
        }
      }
      Runnable flipCard =
          () -> {
            if (!Boolean.TRUE.equals(vm.busy.getValue()))
              vm.move(id, displayedIndex, !displayedFace, null, takeElapsed());
          };
      studyCard.setOnClickListener(v -> flipCard.run());
      studyCard.setFocusable(true);
      studyCard.setContentDescription(getString(R.string.flip) + ". " + face.getText());
      content.addView(studyCard, ui.spaced());
      TextView hint = text(R.string.card_hint);
      hint.setTextSize(11);
      hint.setGravity(Gravity.CENTER);
      MaterialButton flip =
          button(
              R.string.flip,
              () -> vm.move(id, displayedIndex, !displayedFace, null, takeElapsed()));
      flip.setContentDescription(getString(R.string.flip));
      if (item.memoryRating != null) text(R.string.rated_already);
      MaterialButton remember =
          button(R.string.remember, () -> vm.rate(id, displayedIndex, true, takeElapsed()));
      remember.setEnabled(displayedFace && item.memoryRating == null);
      MaterialButton review =
          button(R.string.need_review, () -> vm.rate(id, displayedIndex, false, takeElapsed()));
      review.setEnabled(displayedFace && item.memoryRating == null);
      pair(remember, review);
    } else {
      text(R.string.choose_answer);
      TextView prompt = text("EN_VI".equals(item.questionType) ? item.word : item.meaning, true);
      prompt.setTextSize(28);
      prompt.setPadding(0, dp(12), 0, dp(22));
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
            choice.setBackgroundResource(R.drawable.answer_option);
            choice.setTypeface(ui.bold());
            choice.setTextSize(17);
            choice.setButtonTintList(ColorStateList.valueOf(color(R.color.ui_brand)));
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
    MaterialButton previous = button(R.string.previous, () -> moveSession(-1));
    previous.setEnabled(displayedIndex > 0);
    if (displayedIndex + 1 < items.size())
      pair(previous, button(R.string.next, () -> moveSession(1)));
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
    intro(R.string.great_work, R.string.result_hint, R.drawable.companion_fox);
    text(getString(R.string.source_label, session.sourceName), false);
    if ("QUIZ".equals(session.mode)) {
      LinearLayout summary = ui.panel(R.drawable.bg_soft);
      TextView score =
          ui.label(
              String.format(
                  getResources().getConfiguration().getLocales().get(0),
                  "%.1f%%",
                  LearningRules.percent(session.correctAnswers, session.totalQuestions)),
              40,
              true);
      score.setGravity(Gravity.CENTER);
      score.setTextColor(color(R.color.ui_brand));
      summary.addView(score);
      TextView details =
          ui.label(
              getString(
                  R.string.quiz_summary,
                  session.correctAnswers,
                  session.totalQuestions,
                  LearningRules.percent(session.correctAnswers, session.totalQuestions),
                  session.totalQuestions - session.correctAnswers,
                  duration(session.activeDurationMs)),
              15,
              false);
      details.setGravity(Gravity.CENTER);
      summary.addView(details);
      content.addView(summary, ui.spaced());
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
        content.addView(
            ui.info(
                getString(Boolean.TRUE.equals(i.correct) ? R.string.correct : R.string.incorrect),
                getString(
                    R.string.answer_review,
                    "EN_VI".equals(i.questionType) ? i.word : i.meaning,
                    optionalAnswer(i.answer),
                    i.expected),
                Boolean.TRUE.equals(i.correct) ? R.drawable.bg_soft : R.drawable.bg_error),
            ui.spaced());
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
    intro(R.string.profile, R.string.profile_caption, R.drawable.companion_fox);
    text(s.user.name, true);
    text(s.user.email, false);
    LinearLayout counters = new LinearLayout(this);
    metric(counters, String.valueOf(s.learned()), R.string.learned_words);
    metric(counters, String.valueOf(s.streak()), R.string.streak_days);
    content.addView(counters, ui.spaced());
    section(R.string.learning_section);
    menu(R.string.history, R.drawable.ic_check, () -> vm.open(AppViewModel.screen("HISTORY")));
    menu(
        R.string.achievements,
        R.drawable.ic_trophy,
        () -> vm.open(AppViewModel.screen("ACHIEVEMENTS")));
    menu(R.string.statistics, R.drawable.ic_flame, () -> vm.open(AppViewModel.screen("STATS")));
    section(R.string.account_section);
    menu(
        R.string.edit_profile,
        R.drawable.ic_profile,
        () -> vm.open(AppViewModel.screen("EDIT_PROFILE")));
    menu(R.string.change_password, R.drawable.ic_profile, () -> passwordDialog());
    menu(R.string.settings, R.drawable.ic_topics, () -> vm.open(AppViewModel.screen("SETTINGS")));
    menu(R.string.about, R.drawable.ic_book, () -> vm.open(AppViewModel.screen("ABOUT")));
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

  private void profileEditor(AppState s) {
    toolbar.setTitle(R.string.edit_profile);
    intro(R.string.account_section, R.string.profile_edit_hint, R.drawable.ic_profile);
    EditText name = field("name", R.string.display_name, InputType.TYPE_CLASS_TEXT, s.user.name);
    button(R.string.save, () -> vm.profile(value(name)));
  }

  private void passwordDialog() {
    Bundle b = AppViewModel.screen("PASSWORD");
    vm.open(b);
  }

  private void passwordScreen() {
    content.removeAllViews();
    toolbar.setTitle(R.string.change_password);
    intro(R.string.account_section, R.string.local_accounts, R.drawable.ic_profile);
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
    intro(R.string.history, R.string.history_hint, R.drawable.ic_check);
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
    intro(R.string.your_progress, R.string.profile_caption, R.drawable.ic_flame);
    LinearLayout first = new LinearLayout(this);
    metric(first, String.valueOf(s.learned()), R.string.learned_words);
    metric(first, String.valueOf(s.mastered()), R.string.mastered_count);
    content.addView(first, ui.spaced());
    info(R.string.quiz_count, String.valueOf(s.quizCount()), R.drawable.button_outline);
    if (s.quizCount() == 0) text(R.string.no_scores);
    else {
      info(
          R.string.average_score,
          String.format(
              getResources().getConfiguration().getLocales().get(0), "%.1f%%", s.average()),
          R.drawable.bg_soft);
      info(
          R.string.overall_accuracy,
          String.format(
              getResources().getConfiguration().getLocales().get(0), "%.1f%%", s.accuracy()),
          R.drawable.bg_blue);
    }
    section(R.string.week_activity);
    LocalDate today = LocalDate.now(),
        monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    info(
        R.string.week_activity,
        getString(
            R.string.stats_time,
            duration(s.duration(today, today)),
            duration(s.duration(monday, monday.plusDays(6)))),
        R.drawable.button_outline);
    long max = 1;
    for (int i = 0; i < 7; i++)
      max = Math.max(max, s.duration(monday.plusDays(i), monday.plusDays(i)));
    LinearLayout chart = ui.panel(R.drawable.button_outline);
    LinearLayout columns = new LinearLayout(this);
    columns.setGravity(Gravity.BOTTOM);
    java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
    for (int i = 0; i < 7; i++) {
      LocalDate day = monday.plusDays(i);
      long ms = s.duration(day, day);
      LinearLayout column = ui.column();
      column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      TextView value =
          ui.label(String.format(locale, "%d:%02d", ms / 60000, (ms / 1000) % 60), 10, false);
      column.addView(value);
      FrameLayout track = new FrameLayout(this);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(20), dp(100));
      tp.topMargin = dp(8);
      tp.bottomMargin = dp(8);
      column.addView(track, tp);
      View bar = new View(this);
      bar.setBackgroundResource(day.equals(today) ? R.drawable.bg_green : R.drawable.bg_soft);
      FrameLayout.LayoutParams bp =
          new FrameLayout.LayoutParams(
              -1, dp(ms == 0 ? 5 : Math.max(8, (int) (90 * ms / max))), Gravity.BOTTOM);
      track.addView(bar, bp);
      TextView label =
          ui.label(
              day.format(java.time.format.DateTimeFormatter.ofPattern("EE", locale)),
              11,
              day.equals(today));
      column.addView(label);
      column.setContentDescription(day + ". " + duration(ms));
      columns.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }
    chart.addView(columns);
    content.addView(chart, ui.spaced());
  }

  private void achievements(AppState s) {
    toolbar.setTitle(R.string.achievements);
    intro(R.string.earned_badges, R.string.profile_caption, R.drawable.ic_trophy);
    text(getString(R.string.streak_summary, s.streak(), s.longest()), true).setTextSize(18);
    String[] codes = {"START", "WORDS_50", "STREAK_7", "PERFECT"};
    int[] labels = {
      R.string.badge_start, R.string.badge_words, R.string.badge_streak, R.string.badge_perfect
    };
    for (int i = 0; i < codes.length; i++) {
      Achievement earned = null;
      for (Achievement a : s.achievements) if (a.code.equals(codes[i])) earned = a;
      content.addView(
          ui.info(
              getString(labels[i]),
              earned == null
                  ? getString(R.string.not_earned)
                  : getString(R.string.earned, date(earned.achievedAt)),
              earned == null ? R.drawable.button_outline : R.drawable.bg_orange),
          ui.spaced());
    }
  }

  private void settings(AppState s) {
    toolbar.setTitle(R.string.settings);
    intro(R.string.settings, R.string.settings_hint, R.drawable.ic_topics);
    section(R.string.appearance_section);
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
    section(R.string.habit_section);
    EditText goal =
        field(
            "goal",
            R.string.daily_goal,
            InputType.TYPE_CLASS_NUMBER,
            String.valueOf(s.settings.dailyGoal));
    SwitchMaterial reminder = new SwitchMaterial(this);
    reminder.setText(R.string.reminder);
    reminder.setMinHeight(dp(60));
    reminder.setTypeface(ui.bold());
    reminder.setTextColor(color(R.color.ui_ink));
    reminder.setPadding(dp(14), dp(8), dp(14), dp(8));
    reminder.setBackgroundResource(R.drawable.button_outline);
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
    section(R.string.data_section);
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
    intro(R.string.app_name, R.string.about_description, R.drawable.companion_fox);
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
