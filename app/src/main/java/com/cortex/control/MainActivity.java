package com.cortex.control;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String CREATOR = "vano2111";
    private SharedPreferences prefs;
    private LinearLayout root;
    private boolean registerMode = false;
    private String signedIn = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("cortex_accounts", MODE_PRIVATE);
        showAuth();
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.CENTER); return t;
    }
    private Button button(String value) {
        Button b = new Button(this); b.setText(value); return b;
    }
    private EditText field(String hint, boolean password) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint);
        e.setTextColor(Color.WHITE); e.setHintTextColor(0xff8fa6bb);
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setPadding(24, 12, 24, 12); return e;
    }
    private void base(String heading, String subheading) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28); root.setBackgroundColor(0xff07111f);
        ScrollView scroll = new ScrollView(this); scroll.addView(root);
        setContentView(scroll);
        TextView title = text(heading, 27, 0xff55ddff); title.setPadding(0, 22, 0, 8); root.addView(title);
        TextView sub = text(subheading, 14, 0xffb4c5d6); sub.setPadding(0, 0, 0, 24); root.addView(sub);
    }
    private void showAuth() {
        base("CORTEX CONTROL", registerMode ? "СОЗДАНИЕ АККАУНТА" : "АВТОРИЗАЦИЯ");
        EditText username = field("Логин", false); root.addView(username);
        EditText password = field("Пароль", true); root.addView(password);
        Button primary = button(registerMode ? "ЗАРЕГИСТРИРОВАТЬСЯ" : "ВОЙТИ"); root.addView(primary);
        Button toggle = button(registerMode ? "Уже есть аккаунт — войти" : "Создать аккаунт"); root.addView(toggle);
        TextView note = text("Аккаунты этой демонстрационной версии хранятся только на этом телефоне. Для настоящего входа с разных устройств нужен сервер авторизации.", 12, 0xffffc66d);
        note.setPadding(4, 22, 4, 0); root.addView(note);
        primary.setOnClickListener(v -> {
            String u = username.getText().toString().trim().toLowerCase(Locale.ROOT);
            String p = password.getText().toString();
            if (u.isEmpty() || p.length() < 8) { toast("Введи логин и пароль не короче 8 символов"); return; }
            if (registerMode) {
                if (prefs.contains("user_" + u)) { toast("Такой аккаунт уже существует"); return; }
                if (u.equals(CREATOR) && prefs.getBoolean("creator_claimed", false)) { toast("Аккаунт создателя уже зарегистрирован"); return; }
                prefs.edit().putString("user_" + u, hash(p)).putBoolean(u.equals(CREATOR) ? "creator_claimed" : "unused", true).apply();
                toast("Аккаунт создан. Теперь войди.");
                registerMode = false; showAuth();
            } else {
                String saved = prefs.getString("user_" + u, null);
                if (saved == null || !saved.equals(hash(p))) { toast("Неверный логин или пароль"); return; }
                signedIn = u; showDashboard();
            }
        });
        toggle.setOnClickListener(v -> { registerMode = !registerMode; showAuth(); });
    }
    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (Exception e) { return ""; }
    }
    private void showDashboard() {
        boolean creator = CREATOR.equals(signedIn);
        base("CORTEX CONTROL", "ПАНЕЛЬ УПРАВЛЕНИЯ  •  " + signedIn);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView account = text(creator ? "СОЗДАТЕЛЬ" : "ПОЛЬЗОВАТЕЛЬ", 12, creator ? 0xff55ddff : 0xffb4c5d6);
        top.addView(account, new LinearLayout.LayoutParams(0, -2, 1));
        Button picker = button("▣  ВЫБРАТЬ ТЕЛЕФОН  ⋮"); top.addView(picker); root.addView(top);
        TextView status = text("Устройства пока не подключены", 15, 0xffb4c5d6); status.setPadding(0, 28, 0, 20); root.addView(status);
        Button devices = button("МОИ УСТРОЙСТВА"); root.addView(devices);
        Button connect = button("＋  ПОДКЛЮЧИТЬ УСТРОЙСТВО"); root.addView(connect);
        Button screen = button("ПОСМОТРЕТЬ ЭКРАН"); root.addView(screen);
        Button camera = button("КАМЕРА"); root.addView(camera);
        Button end = button("ЗАВЕРШИТЬ СЕАНС"); root.addView(end);
        TextView info = text(creator ? "Режим создателя: доступ к выбору устройств отображён." : "Обычный аккаунт: управление устройствами недоступно.", 13, 0xff55ddff);
        info.setPadding(0, 24, 0, 12); root.addView(info);
        picker.setEnabled(creator); devices.setEnabled(creator); connect.setEnabled(creator);
        screen.setEnabled(creator); camera.setEnabled(creator); end.setEnabled(creator);
        picker.setOnClickListener(v -> showDevicePicker());
        devices.setOnClickListener(v -> showDevicePicker());
        connect.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Подключение телефона")
            .setMessage("На втором телефоне должно быть установлено CORTEX CONTROL. Владелец устройства должен подтвердить сопряжение.").setPositiveButton("Понятно", null).show());
        screen.setOnClickListener(v -> explainConsent());
        camera.setOnClickListener(v -> explainConsent());
        end.setOnClickListener(v -> { status.setText("Активных сеансов нет"); toast("Сеанс завершён"); });
        Button logout = button("ВЫЙТИ ИЗ АККАУНТА"); root.addView(logout);
        logout.setOnClickListener(v -> { signedIn = ""; registerMode = false; showAuth(); });
    }
    private void showDevicePicker() {
        new AlertDialog.Builder(this).setTitle("Выбрать телефон")
            .setItems(new String[]{"Устройства ещё не сопряжены", "＋ Добавить телефон"}, (d, which) -> {
                if (which == 1) new AlertDialog.Builder(this).setMessage("Сопряжение появится после подключения защищённого сервера. На телефоне друга обязательно подтверждение владельца.").setPositiveButton("ОК", null).show();
            }).setNegativeButton("Закрыть", null).show();
    }
    private void explainConsent() {
        new AlertDialog.Builder(this).setTitle("Требуется подтверждение")
            .setMessage("Просмотр экрана или камеры ещё не подключён. В рабочей версии владелец второго телефона должен явно разрешить сеанс; индикатор будет виден, а отключить сеанс можно в любой момент.")
            .setPositiveButton("Понятно", null).show();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}