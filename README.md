# Eren — Remote Update Dialog (dialog app)

A single-purpose Android app: it hosts the **Eren update dialog** that reads its
content from your Firebase Realtime Database. The admin panel writes the config,
this app paints it — live, with no rebuild.

```
Eren Admin  ──write──▶  Firebase RTDB  ──read──▶  Eren (this app)  ──▶  dialog
```

---

## 1. Files that matter

| File | What it is |
|---|---|
| `app/src/main/java/com/eren/dialog/MainActivity.java` | Host activity. Calls `Eren.show(this);` |
| `app/src/main/java/com/eren/dialog/Eren.java` | Public API + config model + **the strings you edit in MT Manager** |
| `app/src/main/java/com/eren/dialog/ErenDialog.java` | The whole dialog UI (pure programmatic, no XML) |
| `app/src/main/java/com/eren/dialog/ErenData.java` | RTDB REST + live stream + poller + disk cache |
| `app/src/main/assets/fonts/` | Drop your `.ttf` files here |

---

## 2. Using it in a modded APK

Compile the project (GitHub Actions does it for you), then:

1. Open your target APK in **MT Manager** → `View`.
2. Take `classes.dex` from `Eren-debug.apk` and copy `com/eren/dialog/*` into
   the target APK's `classes.dex` (or merge the dex files).
3. Add to the target `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.INTERNET"/>
   ```
4. In your launcher activity's `onCreate`, add one line:

   **smali**
   ```smali
   invoke-static {p0}, Lcom/eren/dialog/Eren;->show(Landroid/app/Activity;)V
   ```

   **Java**
   ```java
   Eren.show(this);
   ```
5. Copy the font files into the target APK's `assets/fonts/` (optional).

That's it. No resources, no `R.*` references, no third-party libraries — the
dialog is 100% programmatic so it survives any resource-id clash.

---

## 3. Editing the keys in MT Manager (Eren.smali)

Both keys live in the **static constructor** `<clinit>` of `Eren.smali`, each
stored exactly **once** as a string constant. You only edit two lines.

Open `com/eren/dialog/Eren.smali` and search for `CONNECT_KEY`:

```smali
# static fields
.field public static CONNECT_KEY:Ljava/lang/String;

.field public static DATABASE_URL:Ljava/lang/String;

# direct methods
.method static constructor <clinit>()V
    .locals 1

    const-string v0, "MM-XXX-XXX-XXX-ST"                     # <-- (1) APP CONNECT KEY

    sput-object v0, Lcom/eren/dialog/Eren;->CONNECT_KEY:Ljava/lang/String;

    const-string v0, "https://your-project-default-rtdb.firebaseio.com"   # <-- (2) DATABASE URL

    sput-object v0, Lcom/eren/dialog/Eren;->DATABASE_URL:Ljava/lang/String;
    ...
```

Change those two `const-string` lines, save, done. Everything else in the class
reads them with `sget-object`, so there is nothing else to touch.

Other tunables (all `const-string` / `const` in the same `<clinit>`):

| Field | Meaning |
|---|---|
| `REMOTE_ROOT` | Root node in the DB (default `apps`) |
| `KILL_PROCESS_ON_EXIT` | `true` = EXIT kills the whole app process |
| `OFFLINE_FALLBACK` | Show the built-in dialog when the DB is unreachable |
| `POLL_INTERVAL_MS` | Refresh interval when the live stream is unavailable |
| `FONT_BRAND` / `FONT_TITLE` / `FONT_BUTTON` / `FONT_BODY` | assets paths |

> `boolean` / `int` fields appear as `const/4`, `const/16` or `const` — for
> example `const/4 v0, 0x1` followed by `sput-boolean v0, ...KILL_PROCESS_ON_EXIT`.

---

## 4. Programmatic API

```java
Eren.show(activity);                                  // compiled-in CONNECT_KEY
Eren.show(activity, "MM-XXX-XXX-XXX-ST");             // explicit key
Eren.show(activity, key, new Eren.Callback() {        // with callbacks
    public void onConfigLoaded(Eren.Cfg cfg) { }
    public void onExit() { }
    public void onUpdate(String url) { }
    public void onDismiss() { }
    public void onError(String message) { }
});

Eren.isShowing();                 // dialog on screen?
Eren.dismiss();                   // close it
Eren.clearCache(context, key);    // forget the cached config
```

---

## 5. Behaviour

- **Shown on every launch** until the user installs a newer build.
- **UPDATE** opens the URL from the admin panel (`updateUrl`).
- **EXIT** closes the host app (`finishAffinity()`; set `KILL_PROCESS_ON_EXIT = true`
  to kill the process outright).
- **Dialog Show = Disabled** in the panel → nothing is drawn at all, and a dialog
  already on screen disappears within a second.
- **Realtime** — RTDB REST streaming; a poller and an on-disk cache keep it
  working on flaky networks and make the first paint instant.
- **Media** — image or video, any ratio (16:9, 4:3, 1:1, 9:16…), URL or Base64.

---

## 6. Build

```bash
gradle assembleDebug           # Gradle 8.7 + JDK 17 (or open in Android Studio)
```

The APK lands in `app/build/outputs/apk/debug/`.

> No Gradle wrapper is committed (the `gradle-wrapper.jar` is a binary). Android
> Studio generates one automatically on first open, or run `gradle wrapper` once.

### GitHub Actions

Push to `main` and the workflow in `.github/workflows/build.yml` builds a debug
and a release APK and uploads them as artifacts (`EREN_APP_APK`). Tag a commit
(`v1.0`) and they are attached to the GitHub release.

---

## 7. Fonts

Put the files in `app/src/main/assets/fonts/`:

```
Oswald-Medium.ttf          -> brand headline
Audiowide-Regular.ttf      -> UPDATE small title + buttons
Poppins-SemiBold.ttf       -> feature lines
```

Missing files fall back to the system font, so the APK always builds.

---

## 8. Icon

`app/src/main/res/mipmap-*/ic_launcher.png` and `ic_launcher_round.png` are
generated from the supplied artwork (round mask, all densities).
