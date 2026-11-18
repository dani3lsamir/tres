package app.tres.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int VOICE = 101;
    private NoteStore store;
    private SharedPreferences prefs;
    private Notebook book = new Notebook(Collections.emptyList());
    private LinearLayout root;
    private LinearLayout list;
    private TextView status;
    private boolean ready;
    private boolean busy;
    private boolean showDone;
    private boolean speechActive;
    private int visibleNotes = 60;
    private String openNote;
    private boolean openVoice;
    private AlertDialog editor;
    private EditText editorText;
    private int background, surface, ink, muted, border;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new NoteStore(getApplicationContext());
        prefs = getSharedPreferences("preferences", MODE_PRIVATE);
        if (state != null) {
            showDone = state.getBoolean("done");
            speechActive = state.getBoolean("speech");
        }
        openVoice = state == null && getIntent().getBooleanExtra("voice", false);
        openNote = state == null ? getIntent().getStringExtra("note_id") : null;
        createScreen();
    }

    /** Widget taps reuse the running activity (singleTop) instead of being dropped. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openVoice = intent.getBooleanExtra("voice", false);
        openNote = intent.getStringExtra("note_id");
        if (ready && !busy) handleLaunchRequest();
    }

    @Override protected void onDestroy() {
        // Avoid leaking dialog windows on rotation; the draft is already persisted in preferences.
        if (editor != null && editor.isShowing()) editor.dismiss();
        super.onDestroy();
    }

    private void handleLaunchRequest() {
        if (prefs.contains("capture_text")) saveCapture();
        else if (openVoice) { openVoice = false; startVoice(); }
        else if (openNote != null) {
            Note note = book.find(openNote);
            openNote = null;
            if (note != null) edit(note);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!busy) refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("done", showDone);
        state.putBoolean("speech", speechActive);
        super.onSaveInstanceState(state);
    }

    private void colors() {
        background=Color.BLACK;surface=Color.BLACK;ink=Color.rgb(244,243,239);muted=Color.rgb(188,191,198);border=Color.rgb(74,77,84);
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private void createScreen() {
        colors();
        root = column();
        root.setBackgroundColor(background);
        root.setPadding(dp(22), dp(16), dp(22), dp(12));
        setContentView(root);

        LinearLayout header = row();
        TextView logo = text("tres", 38, ink);
        logo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("Settings", false, v -> settings()));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = column();
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        status = text("Loading notes…", 13, muted);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        LinearLayout actions = row();
        Button voice = button("Dictate note", true, v -> startVoice());
        voice.setContentDescription("Dictate a note in " + (language().startsWith("es") ? "Spanish" : "English"));
        actions.addView(voice, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button write = button("+", false, v -> edit(null));
        write.setTextSize(27);
        write.setContentDescription("Write a note");
        actions.addView(write, new LinearLayout.LayoutParams(dp(52), dp(56)));
        root.addView(actions);
        render();
    }

    private void refresh() {
        busy = true;
        NoteStore.IO.execute(() -> {
            try {
                Notebook loaded = store.read();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    book = loaded;
                    busy = false;
                    ready = true;
                    render();
                    handleLaunchRequest();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    ready = false;
                    status.setText("Couldn't load your notes. Tap to retry.");
                    status.setOnClickListener(v -> refresh());
                });
            }
        });
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        heading("Your three priorities", book.priorities().size() + " / 3");
        for (Note note : book.priorities()) card(note);
        LinearLayout switcher = row();
        Button active = button("Other notes", !showDone, v -> { showDone = false; render(); });
        Button done = button("Completed", showDone, v -> { showDone = true; render(); });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1);
        switcher.addView(active, half);
        LinearLayout.LayoutParams other = new LinearLayout.LayoutParams(0, dp(48), 1);
        other.setMargins(dp(8), 0, 0, 0);
        switcher.addView(done, other);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, -2);
        tabParams.setMargins(0, dp(25), 0, dp(14));
        list.addView(switcher, tabParams);
        List<Note> others = new ArrayList<>();
        for (Note note : book.all()) if (note.priority == 0 && note.done == showDone) others.add(note);
        others.sort(Comparator.comparingLong((Note n) -> n.updated).reversed());
        for (int i = 0; i < Math.min(others.size(), visibleNotes); i++) card(others.get(i));
        if (others.size() > visibleNotes) list.addView(button("Show more", false, v -> { visibleNotes += 60; render(); }));
        if (others.isEmpty()) list.addView(text(showDone ? "Finished notes will appear here." : "An idea, a to-do. Say it and save it.", 15, muted));
        if (status != null) {
            status.setOnClickListener(null);
            String draft = prefs.getString("draft_text", "");
            status.setText(!ready ? "Loading notes…" : !draft.isEmpty() ? "You have a draft · Tap to continue" : "Saved on this phone");
            if (!draft.isEmpty()) status.setOnClickListener(v -> resumeDraft());
        }
    }

    private void heading(String title, String value) {
        LinearLayout line = row();
        TextView name = text(title, 20, ink);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(text(value, 14, muted));
        line.setPadding(0, 0, 0, dp(14));
        list.addView(line);
    }

    private void card(Note note) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(12), dp(12), dp(8));
        card.setBackground(shape(surface, border));
        TextView content = text(note.text, 18, ink);
        content.setMaxLines(5);
        content.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.setPadding(0, dp(4), 0, dp(8));
        content.setOnClickListener(v -> edit(note));
        content.setContentDescription("Edit note. " + note.text);
        content.setFocusable(true);
        card.addView(content);
        if (!note.due.isEmpty()) {
            boolean overdue = !note.done && note.due.compareTo(java.time.LocalDate.now().toString()) < 0;
            card.addView(text((overdue ? "Overdue · " : "") + note.due + " · " + repeatLabel(note.repeat), 13, muted));
        }
        if (note.done) {
            long days = Math.max(0, (long) Math.ceil((note.completedAt + Notebook.RETENTION_MS - now()) / (double) Notebook.DAY_MS));
            card.addView(text("Deleted in " + days + (days == 1 ? " day" : " days"), 13, muted));
        } else card.addView(button("Date & repeat", false, v -> schedule(note)));
        LinearLayout controls = row();
        controls.addView(button(note.done ? "Restore" : "Complete", false, v -> {
            change(b -> { if (note.done) b.restore(note.id, now()); else b.complete(note.id, now()); }, "Note " + (note.done ? "restored" : "completed"));
        }));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.setMargins(dp(8), 0, 0, 0);
        Button delete = button("Delete", false, v -> confirmDelete(note));
        delete.setContentDescription("Delete note. " + note.text);
        controls.addView(delete, gap);
        View spacer = new View(this);
        controls.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        if (!note.done) controls.addView(button(note.priority > 0 ? "Unpin" : "Prioritize", false, v -> {
            change(b -> { if (note.priority > 0) b.unpin(note.id, now()); else b.prioritize(note.id, now()); }, "Priorities updated");
        }));
        card.addView(controls);
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
        margin.setMargins(0, 0, 0, dp(10));
        list.addView(card, margin);
    }

    private void confirmDelete(Note note) {
        if (!canAct()) return;
        String preview = note.text.length() > 140 ? note.text.substring(0, 140) + "…" : note.text;
        new AlertDialog.Builder(this).setTitle("Delete this note?").setMessage(preview)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    // A pending draft of this note must not come back as a new note.
                    if (note.id.equals(prefs.getString("draft_id", "")))
                        prefs.edit().remove("draft_id").remove("draft_text").apply();
                    change(b -> b.delete(note.id), "Note deleted");
                }).show();
    }

    private void change(NoteStore.Change operation, String message) {
        if (!canAct()) return;
        busy = true;
        status.setText("Saving…");
        NoteStore.IO.execute(() -> {
            try {
                Notebook updated = store.change(operation);
                PriorityWidget.updateAll(getApplicationContext());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    book = updated;
                    render();
                    toast(message);
                });
            } catch (Exception error) { report(error); }
        });
    }

    private void edit(Note note) {
        if (!canAct()) return;
        if (editor != null && editor.isShowing()) return;
        String savedId = prefs.getString("draft_id", "");
        String id = note == null ? "" : note.id;
        String text = id.equals(savedId) ? prefs.getString("draft_text", note == null ? "" : note.text) : note == null ? "" : note.text;
        if (!prefs.getString("draft_text", "").isEmpty() && !id.equals(savedId)) {
            toast("Finish your saved draft first.");
            resumeDraft();
            return;
        }
        editorText = new EditText(this);
        editorText.setMinLines(4);
        editorText.setMaxLines(12);
        editorText.setGravity(Gravity.TOP);
        editorText.setTextSize(18);
        editorText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editorText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Notebook.MAX_TEXT)});
        editorText.setHint("What's on your mind?");
        editorText.setPadding(dp(24), dp(18), dp(24), dp(18));
        editorText.setText(text);
        editorText.setSelection(text.length());
        if (id.isEmpty() && !prefs.contains("draft_capture_id"))
            prefs.edit().putString("draft_capture_id", UUID.randomUUID().toString()).commit();
        editorText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString("draft_id", id).putString("draft_text", s.toString()).apply();
            }
            public void afterTextChanged(Editable value) {}
        });
        editor = new AlertDialog.Builder(this).setTitle(note == null ? "New note" : "Edit note")
                .setView(editorText).setNegativeButton("Later", (d, w) -> render())
                .setPositiveButton("Save", null).create();
        editor.setOnShowListener(d -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> saveEditor(id)));
        editor.setOnDismissListener(d -> render());
        editor.show();
        editorText.requestFocus();
        editor.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void resumeDraft() {
        String id = prefs.getString("draft_id", "");
        Note note = book.find(id);
        if (!id.isEmpty() && note == null) prefs.edit().putString("draft_id", "").apply();
        edit(note);
    }

    private void saveEditor(String id) {
        if (!canAct()) return;
        final String text;
        try { text = Notebook.validText(editorText.getText().toString()); }
        catch (IllegalArgumentException error) { editorText.setError(error.getMessage()); return; }
        busy = true;
        final String captureId = prefs.getString("draft_capture_id", UUID.randomUUID().toString());
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        editorText.setEnabled(false);
        editor.setCancelable(false);
        editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        NoteStore.IO.execute(() -> {
            try {
                Notebook updated = store.change(b -> { if (id.isEmpty()) b.addCapture(captureId, text, now()); else b.edit(id, text, now()); });
                prefs.edit().remove("draft_id").remove("draft_text").remove("draft_capture_id").commit();
                PriorityWidget.updateAll(getApplicationContext());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    book = updated;
                    editor.dismiss();
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    editorText.setEnabled(true);
                    editor.setCancelable(true);
                });
                report(error);
            }
        });
    }

    private String language() { return prefs.getString("language", "es-BO"); }

    private void chooseLanguage() {
        new AlertDialog.Builder(this).setTitle("Dictation language")
                .setSingleChoiceItems(new String[]{"Spanish", "English"}, language().startsWith("es") ? 0 : 1, (dialog, which) -> {
                    prefs.edit().putString("language", which == 0 ? "es-BO" : "en-US").apply();
                    dialog.dismiss();
                    createScreen();
                }).setNegativeButton("Close", null).show();
    }

    private void startVoice() {
        if (!canAct() || speechActive) return;
        if (prefs.contains("capture_text")) { saveCapture(); return; }
        if (!prefs.getBoolean("voice_notice", false)) {
            new AlertDialog.Builder(this).setTitle("Notes by voice")
                    .setMessage("Your phone's speech service will transcribe the audio in the chosen language. Depending on its settings, it may process it online. Tres saves the transcript on this device, and you can edit it later.")
                    .setPositiveButton("Dictate", (d, w) -> { prefs.edit().putBoolean("voice_notice", true).apply(); launchVoice(); })
                    .setNegativeButton("Later", null).show();
        } else launchVoice();
    }

    private void launchVoice() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, language().startsWith("es") ? "Di tu nota" : "Speak your note")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { speechActive = true; startActivityForResult(intent, VOICE); }
        catch (ActivityNotFoundException | SecurityException error) {
            speechActive = false;
            new AlertDialog.Builder(this).setTitle("Dictation unavailable")
                    .setMessage("Turn on or install a speech recognition service on Android. You can also use your keyboard's microphone when writing a note.")
                    .setPositiveButton("Write note", (d, w) -> edit(null)).setNegativeButton("Close", null).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != VOICE) return;
        speechActive = false;
        if (result != RESULT_OK || data == null) return;
        ArrayList<String> options = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (options == null || options.isEmpty() || options.get(0).trim().isEmpty()) { toast("No text received. You can dictate again."); return; }
        String text = options.get(0);
        prefs.edit().putString("capture_text", text).putString("capture_id", UUID.randomUUID().toString()).commit();
        if (ready && !busy) saveCapture();
    }

    private void saveCapture() {
        if (!canAct()) return;
        final String text = prefs.getString("capture_text", "");
        final String id = prefs.getString("capture_id", "");
        busy = true;
        status.setText("Saving dictation…");
        NoteStore.IO.execute(() -> {
            try {
                Notebook updated = store.change(b -> b.addCapture(id, text, now()));
                prefs.edit().remove("capture_text").remove("capture_id").commit();
                PriorityWidget.updateAll(getApplicationContext());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    book = updated;
                    showDone = false;
                    render();
                    status.setText("Dictation saved · Tap to edit");
                    status.setOnClickListener(v -> edit(book.find(id)));
                });
            } catch (Exception error) { report(error); }
        });
    }

    private void settings() {
        new AlertDialog.Builder(this).setTitle("Tres")
                .setItems(new String[]{"Dictation language", "Add widget", "About your notes"}, (d, which) -> {
                    if (which == 0) chooseLanguage();
                    else if (which == 1) {
                        AppWidgetManager widgets = AppWidgetManager.getInstance(this);
                        if (widgets.isRequestPinAppWidgetSupported()) widgets.requestPinAppWidget(new ComponentName(this, PriorityWidget.class), null, null);
                        else new AlertDialog.Builder(this).setMessage("Long-press your home screen, choose Widgets and look for Tres.").setPositiveButton("Got it", null).show();
                    } else new AlertDialog.Builder(this).setTitle("Your notes, at hand")
                            .setMessage("Notes are saved on this phone. Pending priorities stay when the day changes. Completed notes are deleted after 10 days, when you open the app or change notes. Repeating tasks create their next date when completed. No notifications are sent.\n\nDictation uses Android's speech service and may use the internet. Tres doesn't store audio.\n\nUninstalling the app or clearing its data deletes your notes. Version " + versionName() + ".")
                            .setPositiveButton("Got it", null).show();
                }).show();
    }

    private String repeatLabel(String value) {
        switch(value){case "daily":return "Every day";case "weekly":return "Every week";case "monthly":return "Every month";default:return "Don't repeat";}
    }
    private void schedule(Note note) {
        final String[] due={note.due};final String[] values={"none","daily","weekly","monthly"};
        LinearLayout fields=column();fields.setPadding(dp(20),dp(12),dp(20),dp(12));
        Button date=button(due[0].isEmpty()?"Pick a date":due[0],false,v->{
            java.time.LocalDate d=due[0].isEmpty()?java.time.LocalDate.now():java.time.LocalDate.parse(due[0]);
            new android.app.DatePickerDialog(this,(picker,y,m,day)->{due[0]=java.time.LocalDate.of(y,m+1,day).toString();((Button)fields.getChildAt(0)).setText(due[0]);},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();
        });fields.addView(date);
        fields.addView(button("Clear date",false,v->{due[0]="";date.setText("Pick a date");}));
        android.widget.Spinner repeat=new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Don't repeat","Every day","Every week","Every month"});
        repeat.setAdapter(adapter);repeat.setSelection(java.util.Arrays.asList(values).indexOf(note.repeat));fields.addView(repeat);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Date & repeat").setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String r=values[repeat.getSelectedItemPosition()];
            try {Notebook.validateSchedule(due[0],r);}catch(IllegalArgumentException e){toast(e.getMessage());return;}
            if(!canAct())return;change(b->b.schedule(note.id,due[0],r,now()),"Date updated");dialog.dismiss();
        }));dialog.show();
    }

    private boolean canAct() {
        if (!ready) { toast("Wait for your notes to load."); return false; }
        if (busy) { toast("Saving. One moment…"); return false; }
        return true;
    }

    private void report(Exception error) {
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            busy = false;
            String message = error instanceof IllegalArgumentException ? error.getMessage() : "Couldn't save. Your draft is kept. Try again.";
            status.setText(message);
            toast(message);
        });
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException error) { return "?"; }
    }

    private long now() { return System.currentTimeMillis(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private GradientDrawable shape(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill); drawable.setCornerRadius(dp(18)); drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private Button button(String label, boolean filled, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label); button.setTextSize(14); button.setAllCaps(false);
        button.setTextColor(filled ? background : ink);
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44777777), shape(filled ? ink : surface, filled ? ink : border), null));
        button.setOnClickListener(action);
        return button;
    }
}
