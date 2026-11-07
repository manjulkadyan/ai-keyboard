# Team 3: Monetization & SDKs. Voice Keyboard and Translator v1.4

App: `com.heri.language.keyboard.voice.typing` · versionName 1.4 · versionCode 5 · native Kotlin + Compose · FlorisBoard fork · R8 (map id `53b39b15…`) · minSdk 24 / targetSdk 36.
Pass: **static only**. Tags: `[obs]` means read directly from the decompiled source, manifest or resources. `[dec]` is a design decision or recommendation. `[inf]` is an inference that needs runtime confirmation. Runtime claims are `[inf]`, and each one has a numbered open item (§12).
Paths are relative to `voicetranslator_work/jadx_out/sources/` unless they start with `resources/`.

**Ad unit IDs ship inside every APK, so this file treats them as non-secret and gives them in full.** The single API key (`google_api_key`, `AIzaSyCd…lqfo`) is covered by Team 5 and is not repeated here.

**Headline:** monetization is **ads only, on a single network (AdMob Next-Gen)**. There is no IAP catalog, no paywall, and no free-tier caps. Every ad placement is gated by Remote Config. One unit ID is shared by all 13 interstitial placements, and one static `InterstitialAd` slot serves them all. An in-keyboard native ad shows inside third-party apps, refreshes at most once every 10 minutes, and is suppressed on a hard-coded list of 86 Google, Chrome, Play and launcher packages. `[obs]`

---

## 1. SDK roster (monetization-relevant)

| SDK | Evidence | Wired from first-party? |
|---|---|---|
| **Google Mobile Ads, Next-Gen** (`com.google.android.libraries.ads.mobile.sdk` plus obfuscated `ads_mobile_sdk/`) | Types used in first-party code: `InterstitialAd`, `AppOpenAd`, `NativeAd`, `BannerAdRequest`, `AdRequest` (`rb/g.java`, `o0/e.java:152-160`, `sb/c.java`, `nb/f.java:277`) `[obs]` | **Yes. It is the only ad renderer.** |
| Google Mobile Ads, legacy (`com/google/android/gms/ads`) | Listed as PRESENT in `sdk-roster.txt`. The manifest `APPLICATION_ID` meta-data (`resources/AndroidManifest.xml:130`) is read by both stacks `[obs]` | Only transitive. First-party code uses the Next-Gen classes `[inf]`. Do not count it as a second network. |
| UMP consent (`com/google/android/ump`, `internal/consent_sdk`) | `kb/g.java:156-170` gathers consent on the splash; the result goes to `kb/e.java:103-112` `[obs]` | **Yes.** It writes `is_gdpr_shown_to_user` and `can_user_request_ads` (§3.2). |
| Firebase Remote Config | `ob/e.java:118-208`: defaults come from `res/xml/remote_config_defaults.xml`, **minimumFetchInterval = 3 s** (`fVar.f17945b = 3L`) `[obs]` | Yes. It controls every ad switch (§10). |
| Firebase Analytics | `tb/a.java` (event helper) and `kb/a.java:449-455` (screen_view) `[obs]` | Yes. It logs ad-pipeline events only (§11). |
| Firebase Crashlytics, Performance, Sessions, Installations, ABT, FCM | Manifest component registrars at `resources/AndroidManifest.xml:191-209`. `com/google/firebase/{crashlytics,perf,abt,messaging}` `[obs]` | Crashlytics and Perf are auto-init only, with no first-party call sites found `[inf]`. ABT is pulled in by Remote Config, and no first-party A/B API is used `[inf]`. The triage roster missed Crashlytics and Perf. |
| **Play Billing Library 8.3.0** | `resources/AndroidManifest.xml:174-175` `com.google.android.play.billingclient.version=8.3.0`. `ProxyBillingActivity(V2)` at `:178,:183`. BILLING permission `[obs]` | **No.** `grep com.android.billingclient` outside its own package finds nothing. There is no `BillingClient`, `launchBillingFlow`, `queryProductDetails` or `queryPurchases` call `[obs]`. The library is vestigial. |
| Play In-App Update (`com/google/android/play/core`) | Library present `[obs]` | No first-party `AppUpdateManager` call found `[obs]`. Probably vestigial `[inf]`. |
| Play In-App Review | Not present. There is no `ReviewManager` or `requestReviewFlow` `[obs]` | n/a. Rating is done with a custom dialog and a market link (§9). |
| Facebook Shimmer (`com/facebook/shimmer`) | Used only as the ad-loading shimmer (`nb/f.java` banner path) `[obs]` | This is UI only, not Facebook Audience Network. |

**Not present** (grep for package roots): Facebook Audience Network, AppLovin/MAX, ironSource/LevelPlay, Unity, Vungle/Liftoff, Mintegral, Pangle, InMobi, mediation adapters, AppsFlyer, Adjust, Branch, Singular, Amplitude, Mixpanel, RevenueCat, Adapty, Qonversion. `[obs]` **The app runs on one ad network with no mediation and no MMP.**

## 2. Ad unit IDs (complete)

There is one publisher, `pub-8730…7007`. The app id is in `resources/res/values/strings.xml:21`. Every unit lives only in `resources/res/xml/remote_config_defaults.xml`, and none is hard-coded in Java. `[obs]`

| ID | Format (from key names) | Used by RC keys |
|---|---|---|
| `ca-app-pub-8730…~…0830` | App ID (`admob_app_id`) | Manifest `APPLICATION_ID` |
| `ca-app-pub-8730…/…9004` | Banner | `{splash,main,customization,enable}_screen_banner_Id` (4×) |
| `ca-app-pub-8730…/…8142` | Native | `{main,customization,theme_apply,try}_screen_native_Id` and `keyboard_native_ad_keyboard_id` (5×) |
| `ca-app-pub-8730…/…0666` | App Open | `app_open_main_id` |
| `ca-app-pub-8730…/…7927` | Interstitial | All 13 `*_inter_id` keys |

Four units plus one app id. The formats are confirmed by the loader each key feeds: `ob/e.java:216` (Banner model), `:220` (Native), `:322-333` (AppOpen) and `:239` (Interstitial). `[obs]` Because every placement shares a unit, the publisher cannot see per-placement eCPM in AdMob. Placement attribution exists only in the Firebase events (§11). `[inf]`

## 3. Ad architecture

### 3.1 Classes

| Role | Class | Notes |
|---|---|---|
| Remote-config / ad-state manager ("remoteManager") | `ob/e.java` | Holds 13 `InterstitialAdModel` fields, `AppOpenAdModel`, and a `StateFlow<Boolean>` `isUserSubscribed` (`f39461e`/`f39462f`). It wires itself into `sb.c.f42710j`, `rb.g.f41807h` and `qb.c.f40850b` (`:209-211`). `[obs]` |
| Gate `canRequestAds()` | `ob/e.java:221-228` | `!isUserSubscribed && tinyDB["can_user_request_ads"]` `[obs]` |
| Interstitial engine | `rb/g.java` (static) plus callbacks `rb/a..f.java` | There is a single static `InterstitialAd f41800a` and flags for loading, loaded and showing. `[obs]` |
| Splash flow | `kb/a.java` (`b()` = splash composable, `d()`/`e()` = ad flow), `kb/g.java` (coroutine: subscription → RC fetch → consent), `kb/e.java` (consent callback), `kb/h.java` (wait-window CountDownTimer) | `[obs]` |
| App Open | `pb/d.java` (`ComposeOpenAppManager`, `ActivityLifecycleCallbacks` + `ProcessLifecycle` `onStart`) | `[obs]` |
| Banner composable | `nb/f.java:164` `c(...)`, sizing in `qb/c.java` | Supports collapsible top/bottom, a "large 100dp" variant, and a shimmer placeholder. `[obs]` |
| In-app native composable | `nb/f.java:752` `d(...)` → `sb/c.java` (`b()` load, `c()` inflate with 4+ layout variants via `populateFourthMediaAdmob`) | `[obs]` |
| Keyboard native | `wi/v.java` (load LaunchedEffect), `cb/n2.java:485-530` (cooldown), `a2/h.java:1110` (CTA color), `FlorisImeService.java:672-717` (eligibility) | `[obs]` |
| Ad-loading dialog | `pe/b.java:2084` `c0()` is a full-screen, non-cancelable `AlertDialog` built from binding `v3/e0` | `[obs]` **Correction to triage:** `v3/e0` is this ad-loading dialog's ViewBinding, not OCR. |

### 3.2 Global gates (applied to every format)

1. **Premium flag:** `isUserSubscribed` / `is_user_premium`. `kb/g.java:116` reads `isUserSubscribed` from prefs and mirrors it into the StateFlow and into `is_user_premium` (`:122`). **No code writes `isUserSubscribed = true`** (grep finds only that read), so the flag is always false in this build. `[obs]`
2. **Consent:** `can_user_request_ads` is the UMP `canRequestAds()` result, written in `kb/e.java:105,112`. It defaults to false (`FlorisImeService.java:702`). Until consent completes, no ad requests are made. `[obs]`
3. **Network:** `com.bumptech.glide.e.q(context)` is a connectivity check before every load (`rb/g.java:60`, `kb/a.java:907`, `ob/e.java:231`). `[obs]`
4. **Per-placement `*_enabled`** comes from Remote Config. `[obs]`

## 4. Per-placement table

### 4.1 Interstitials (all on unit `/…7927`)

Show call: `rb.g.b(activity, adData, newAdData, "<tag>", onDone)`. In every call except splash, `adData == newAdData`. `[obs]`

| RC prefix | Model field (`ob/e`) | Trigger (call site) | Event tag |
|---|---|---|---|
| `splash` | `f39467k` | End of the splash wait window (`kb/a.java:762-833`). It preloads the next ad using the **`customize_keyboard_card`** config (`f39476v`, `:775`) | `splash_interstitial` |
| `translate_button` | `f39474t` | Translate button on the text-translator screen (`mb/n0.java:82-83`) | `translate_button_interstitial` |
| `text_translation` | `f39470o` | Home card → text translation (`cj/o.java:393` + 3 dup sites) | `text_translation_click_interstitial` |
| `conversation` | `f39472q` | Home card → conversation (`cj/o.java:380-381`) | `conversation_click_interstitial` |
| `dictionary` | `r` | Home card → dictionary (`cj/o.java:384-385`) | `dictionary_click_interstitial` |
| `phrasebook_translation` | `f39473s` | Home card → phrasebook (`cj/o.java:389`) | `phrasebook_click_interstitial` |
| `camera_translation` | `f39471p` | Camera-translation entry (`c1/a.java:238-239`, `cb/b.java:70-71`) | `camera_translation_click_interstitial` |
| `language_translator_card` | `f39469n` | Main-screen card (`cb/n2.java:274-275`, `:297`) | `language_translation_card_interstitial` |
| `keyboard_sticker_card` | `f39477w` | Main-screen sticker card (`cb/n2.java:278-279`, `:301`) | `keyboard_sticker_card_interstitial` |
| `customize_keyboard` | `f39475u` | Theme pick → customize (`cb/t0.java:251`) | `customize_keyboard_interstitial` |
| `customize_keyboard_card` | `f39476v` | Main-screen customize card (`a2/b.java:175-176`) | `customization_card_interstitial` |
| `apply_customization` | `l` | Apply theme (`cb/w0.java:114`, `eb/s.java:818`) | `apply_customization_interstitial` |
| `leave_customization` | `f39468m` | Back out of the customization editor (`ab/l.java:128-129`) | `leave_customization_interstitial` |

Every interstitial RC key is read and wired. `[obs]` Each one triggers on **navigation into a feature**. None triggers on a result or after N uses. `[obs]`

**Show algorithm** (`rb/g.java:67-136`) `[obs]`:
```
if !enabled || subscribed || !canRequestAds -> onDone(false)          // proceed, no ad
if capping_enabled && now - lastShown < 30_000 -> onDone(false)       // 30 s hard-coded cooldown
if no ad loaded -> load(newAdData) ; onDone(false)                    // NOT shown, preload for next time
else: if capping_enabled: lastShown = now
      show full-screen "loading ad" dialog (pe.b.c0) ; after 1000 ms -> interstitial.show()
      on dismiss -> preload again (if *_load_next) ; onDone(true)
```
- The cooldown is a **hard-coded 30 s** (`(long)30 * 1000`, `rb/g.java:90`). No RC key controls its length. Only `*_capping_enabled` switches it on, and **every default is `false`**, so with the defaults there is no cooldown at all. `[obs]`
- Because one `InterstitialAd` object is shared, the ad shown at placement X is whatever was preloaded at placement Y. Preloading is opportunistic: the first tap on a feature usually shows nothing and only triggers a load. `[obs]`/`[inf]`
- On splash, the dialog delay is 2000 ms (`kb/a.java:796`) instead of 1000 ms. `[obs]`

### 4.2 Splash flow and timing

`kb/g.java:100-170` → `kb/a.java:837-935`. `[obs]`
1. Destroy any cached keyboard native ad (`sb.c.f42705e`). Read the subscription flag.
2. Set the wait window: **3000 ms if subscribed or offline, otherwise 12000 ms** (`kb/g.java:124`). `[obs]`
3. Fetch Remote Config and wait for it.
4. If `is_gdpr_shown_to_user`, start the ad flow directly ("no_gdpr"). Otherwise log `ShowGDPR` and show the UMP form, with a timeout guard (`kb/e.java:92`). Then log `gdpr_show_callback` and continue with "gdpr".
5. Load the splash interstitial if the gates pass, then start the CountDownTimer for the wait window. The timer finishes (or the ad loads) → `moveToNext()` → show if loaded → navigate. While the splash is paused the flow is held (`Lifecycle → PAUSE/STOP` cancels the timer and RESUME restarts it, `kb/a.java:426-470`).

### 4.3 App Open (`/…0666`)

`pb/d.java` `[obs]`:
- It fetches when `app_open_main_show && !is_user_premium && id != ""` (`:57-58`). The cached ad expires after **4 h** (`:71`).
- Show happens on process `onStart` (foreground return). It is skipped if any of these hold: the current activity is `AdActivity` or `PermissionActivity`, an app-open ad is already showing, `MainActivity.f21251g` is set (an interstitial is on screen), the route is in a restricted set (`x0.INSTANCE`, `:103-104`, likely the splash `[inf]`), or no valid ad is cached. When skipped it fetches instead.
- The ad-pipeline events are `app_open_{requested,loaded,failed_load,shown,on_impression,failed_to_show,dismiss}`.

### 4.4 Banners (`/…9004`)

| Screen | RC prefix | Read at | Rendered at | Defaults (enabled/collapsing/large/top) |
|---|---|---|---|---|
| Main (home) | `main_screen_` | `kb/a.java:173` | `kb/a.java:247` | true / false / false / false |
| Customization editor | `customization_screen_` | `eb/s.java:535` | `eb/s.java:13020` | true / false / false / false |
| Enable-keyboard (onboarding) | `enable_screen_` | `cb/n.java:610` | `cb/n.java:679` | true / false / false / false |
| Splash | `splash_screen_` | **no read site** | none | true / false / **true** / false |

- **`splash_screen_*` (5 keys) is dead config.** It is defined in the defaults, but no code reads it. `[obs]`
- Collapsible banners are supported. The request gets the extra `collapsible = top|bottom` when `is_banner_collapsing` is set (`nb/f.java` around line 441). `is_banner_large` switches the size helper `qb/c.a(isLarge100dp, activity)`. `[obs]`
- The banner loads once per screen key, and the result is cached and reused ("showSingleRequestBannerAd", "AD_PENDING"). `[obs]`

### 4.5 In-app natives (`/…8142`)

| Screen | RC prefix | Read at | Rendered at | type / CTA |
|---|---|---|---|---|
| Customization | `customization_screen_` | `eb/s.java:542` | via `sb/c` `[inf]` | 1 / #ff0000 |
| Theme apply | `theme_apply_screen_` | `cb/y0.java:82` | `cb/t0.java:261` | 2 / #ff0000 |
| Try keyboard (onboarding step after enable) | `try_screen_` | `cb/n.java:765` | `ab/f.java:400` | 2 / #ff0000 |
| Main | `main_screen_` | **no read site** | none | 2 / #ff0000 |

- **`main_screen_native_*` (4 keys) is dead config.** `[obs]` The home screen shows a banner only.
- `*_native_type` picks the layout variant in `sb/c.c()`. Each variant has a different media and CTA arrangement (`populateFourthMediaAdmob`, `sb/c.java:288-608`). The CTA tint is parsed from the hex string. `[obs]` What each type looks like is `[inf]` (open item #5).

### 4.6 **Ads inside the keyboard** (in-IME native)

Yes, the IME shows a native ad while the user types in other apps. `[obs]`

| Aspect | Value | Evidence |
|---|---|---|
| Unit | `keyboard_native_ad_keyboard_id` = `/…8142` (the same unit as the in-app natives) | `ob/e.java:337-355` copies the RC values into IME prefs (`n.d` SharedPreferences). `wi/v.java:81` reads them. |
| Kill switch | `keyboard_native_ad_enable` (default true) | `FlorisImeService.java:710` |
| Eligibility on each `onStartInputView` | user unlocked **and** host package not in the deny-list **and** `keyboard_native_ad_enable && !is_user_premium && can_user_request_ads` | `FlorisImeService.java:679-717` |
| Host deny-list | **86** hard-coded packages: the app itself, ~48 `com.google.*` apps, Chrome (stable/beta/dev), Play Store, Snapseed, and ~30 OEM and third-party launchers (Samsung, MIUI, Huawei, Oppo/ColorOS, Realme, Vivo, OnePlus, Sony, LG, Motorola, Asus, Lenovo ZUI, Microsoft, Nova, Lawnchair, Action, etc.) | `FlorisImeService.java:683` (one `equals` chain) |
| Refresh cadence | A new ad is requested only when `now > keyboardBannerPreviousTime + keyboard_native_ad_time`. The default is **600000 ms (10 min)**. Otherwise the cached `NativeAd` is reused | `cb/n2.java:488-525` (log tag `KeyboardNativeBanner`) |
| CTA color | `keyboard_native_ad_button_color` (RC default `#ff0000`, code fallback `#2567FC`) | `a2/h.java:1110` |
| Placement | In the IME UI (`ImeUi`) above or near the smartbar `[inf]` | Open item #5 |

- **Policy note `[inf]`:** a native ad inside a keyboard overlays third-party apps' input, and hiding it specifically on Google-owned apps, Play Store and launchers looks designed to avoid review detection. AdMob/Play policy disallows ads that interfere with other apps' UI. **The clone should not copy this** `[dec]`. Keep ads inside the companion app only.

## 5. Ad cadence summary (static model)

| Surface | Max frequency with defaults | Controlled by |
|---|---|---|
| Splash interstitial | 1 per cold start (if loaded within 12 s) | `splash_inter_enabled` |
| Feature interstitials | Every feature entry, provided an ad is preloaded. **No cooldown with defaults** | `*_inter_enabled`, `*_capping_enabled` (30 s fixed) |
| App Open | Every return to foreground, unless an interstitial is up or the route is restricted | `app_open_main_show` |
| Banners | 1 per screen view, cached | `*_is_banner_enabled` |
| Natives | 1 per screen, preloaded | `*_is_native_enabled` |
| Keyboard native | 1 new ad per 10 min while typing; the cached ad stays visible in between | `keyboard_native_ad_time`, `_enable` |

The real production values, which can override all of these defaults, are unknown → open item #2. `[inf]`

## 6. Billing catalog

**There is none.** `[obs]`
- There are no product or SKU IDs anywhere in sources or resources (grep for `"subs"`, `"inapp"`, `BillingClient` and `ProductDetails` outside the library finds nothing).
- `strings.xml` (510 strings) has no price, trial, premium, pro, remove-ads or subscription strings.
- There are no crown, premium or VIP drawables. The only ad-related drawables are `bg_ad_attribute.xml`, `bg_native_ad.xml` and `native_ad_atrribute_bg.xml`.
- The BILLING permission, `ProxyBillingActivity(V2)` and the `InAppBillingService.BIND` query in the manifest all come from the library's merged manifest. `[inf]`
- There is no A/B variant of any paywall. ABT is present but has no first-party use. `[inf]`

The premium plumbing (`isUserSubscribed`, `is_user_premium`, and the shorter subscribed-splash wait) is a **template leftover**. It suggests a sister build or a future update with a subscription. `[inf]` (The `com.example.voicetypingkeyboard_s` package name also suggests a reused template.)

## 7. Paywall triggers and layout

**There is no paywall screen.** No Compose screen references billing, and no `R.string` related to a paywall exists. `[obs]` The only "upgrade-like" UI is the PairIP `LicenseActivity` (Play-license failure → "get it from Play" dialog). That is anti-piracy, not monetization (Team 5). `[inf]`

## 8. Free-tier limits

There are none. `[obs]` Every feature is unmetered: text, voice, conversation and camera translation, dictionary, phrasebook, themes, custom themes, stickers and languages. No counters, daily caps, watermarks or rewarded-for-unlock exist. **There is no rewarded format at all.** No `RewardedAd` or `RewardedInterstitialAd` is referenced first-party. `[obs]` The "price" of a feature is one interstitial per entry.

## 9. Growth hooks

| Hook | Implementation | Evidence |
|---|---|---|
| Onboarding to enable the keyboard (2 steps) | "Step 1 of 2: Enable Voice Typing Keyboard" opens `android.settings.INPUT_METHOD_SETTINGS`, deep-linked to `FlorisImeService` via `:settings:fragment_args_key`. "Step 2 of 2: Select" opens `showInputMethodPicker()`. It then goes to the "try" screen | `cb/x.java:31-50`, strings `step_1_of_2`/`step_2_of_2`/`tap_below_to_select` used in `ab/f.java`; enable detection via `getEnabledInputMethodList` and `Settings.Secure default_input_method` (`ok/a.java:92,129`) `[obs]` |
| Monetized onboarding | The enable screen carries a banner, and the try screen carries a native ad (§4.4/4.5) | `[obs]` |
| Rating prompt | A custom "Enjoying the App?" dialog with Rate Us / Maybe later (`nb/v.java:94`, `nb/g.java:49,63`). **It opens `market://details?id=<pkg>` regardless of the star value** (the Float is ignored, `ab/g.java:148-151`). It is triggered only from Settings → "Rate App" (`cb/m2.java:581-597`, `cb/c2.java:69`). There is no automatic or periodic trigger and no Play In-App Review | `[obs]`. The ignored star value means it is not review-gating, but it is also not a native prompt |
| Share app | `ACTION_SEND` chooser with a short promo text plus a Play URL | `cb/f2.java:33-42` `[obs]` |
| Feedback | `mailto:` to the developer Gmail (`ar…@gmail.com`), subject "Feedback: <app> 1.4", body with the device model and OS | `cb/f2.java:44-52` `[obs]` |
| Privacy policy | `sites.google.com/view/voice-keyboard-heri-policy/home` | `cb/f2.java:55` `[obs]` |
| Push re-engagement | FCM service `FCMHelper` only logs on receipt and on token. Notification display falls back to the FCM default. The app subscribes to a topic at startup (`gj/i.java:190-200`); the topic name sits behind an obfuscated continuation `[inf]`. The main screen shows an "Allow notifications" pre-prompt dialog (`kb/a.java`, strings `allow_notifications`, `later`) before `POST_NOTIFICATIONS` | `[obs]`/`[inf]`: open item #6 |
| Exit | "Tap back again to exit" toast, with no exit ad or exit dialog `[obs]` | strings.xml:464 |
| Referral, invite, share-to-unlock | None `[obs]` | |

## 10. Remote Config keys (all 94)

Source: `resources/res/xml/remote_config_defaults.xml`, fetched in `ob/e.java` with `minimumFetchInterval=3s`. **All 94 keys are ad configuration. No feature flags, kill switches, endpoints or paywall variants are server-driven.** `[obs]`

| Group | Keys | Defaults | Read? |
|---|---|---|---|
| Banner × 4 screens | `{splash,main,customization,enable}_screen_banner_Id`, `_is_banner_enabled`, `_is_banner_collapsing`, `_is_banner_large`, `_is_banner_top` (20) | id `/…9004`, enabled true, collapsing false, large false (splash: true), top false | main, customization and enable: yes. **splash: no** |
| Native × 4 screens | `{main,customization,theme_apply,try}_screen_native_Id`, `_is_native_enabled`, `_native_cta_color`, `_native_type` (16) | id `/…8142`, true, `#ff0000`, type 2 (customization: 1) | customization, theme_apply and try: yes. **main: no** |
| Keyboard native | `keyboard_native_ad_time`, `_keyboard_id`, `_enable`, `_button_color` (4) | 600000, `/…8142`, true, `#ff0000` | yes (`ob/e.h()`) |
| App open | `app_open_main_id`, `app_open_main_show` (2) | `/…0666`, true | yes (`ob/e.g()`) |
| Interstitial × 13 | `<p>_inter_id`, `<p>_inter_enabled`, `<p>_inter_load_next`, `<p>_inter_capping_enabled` for p ∈ {splash, translate_button, text_translation, phrasebook_translation, leave_customization, language_translator_card, dictionary, customize_keyboard, conversation, camera_translation, apply_customization, customize_keyboard_card, keyboard_sticker_card} (52) | `/…7927`, true, true, **false** | all yes (`ob/e.d()` loop, `:236-304`) |

20 + 16 + 4 + 2 + 52 = 94. **9 keys are dead** (5 `splash_screen_*` and 4 `main_screen_native_*`). `[obs]`
A 3 s minimum fetch interval in production effectively disables RC throttling. Combined with the fetch in front of every splash, this means a network round-trip on every cold start. `[obs]`/`[inf]`

## 11. Analytics event list (complete, first-party)

The only first-party sink is `tb/a.a(id, name, type)`. It logs an event named `id` with params `item_id`, `item_name` and `content_type` (`tb/a.java:22-31`). Plus one manual `screen_view` (`screen_name=SplashScreen`, `kb/a.java:449-455`). Only 16 files call it. **No product or feature events exist** (no translate, voice, theme_applied, keyboard_enabled or similar). Firebase auto-events (`first_open`, `session_start`, `ad_impression`, `screen_view` auto, …) are added on top. `[obs]`

**Static event names** (14):

| Event | content_type | Where |
|---|---|---|
| `splash_screen_created` | splash_screen | `kb/a.java:417,505` |
| `ShowGDPR` | ShowGDPRScreen | `kb/g.java` |
| `gdpr_show_callback` | splash_screen | `kb/e.java:103` |
| `startAdFlowOneShot_consent` | splash_screen | `kb/a.java:879` |
| `startAdFlowOneShot_!canShowNow` | splash_screen | `kb/a.java:854` |
| `afterConsentStart_<from>` (`no_gdpr` / `gdpr`) | splash_screen | `kb/a.java:843` (dynamic suffix) |
| `splash_interstitial_loaded` | onAdLoaded | `af/x.java:274` |
| `splash_interstitial_inter_failed` | `onAdFailedToLoad <msg>` | `pb/a.java:42` |
| `splash_show_failed` | `onAdFailedToShowFullScreenContent <msg>` | `pb/b.java:48` |
| `splash_inter_dismiss` | Admob Interstitial Dismiss … | `rb/a.java` |
| `app_open_requested` / `_loaded` / `_failed_load` / `_shown` / `_on_impression` / `_failed_to_show` / `_dismiss` | app_open_implementation | `o0/e.java:157`, `af/x.java:221,233`, `g3/k.java:64,76`, `pb/*` |

**Dynamic templates** (`<tag>` = the placement tag from §4.1, or the native screen key such as `try_screen_native`, `theme_apply_screen_native` or the keyboard key):

| Template | content_type | Where |
|---|---|---|
| `<tag>_loaded` | onAdLoaded | `b/t1.java:202` |
| `<tag>_inter_failed` | `onAdFailedToLoad <msg>` | `rb/c.java:40` |
| `<tag>_inter_failed_show` | `onAdFailedToShowFullScreenContent <msg>` | `rb/d.java:42` |
| `<tag>_dismiss` | onAdDismissedFullScreenContent | `rb/b.java:77` |
| `<key>_native_ad_requested` | native_ad | `o0/e.java:267` |
| `<key>_native_load_called` / `_native_load_returned1` / `_native_load_returned2` | native_ad | `sb/c.java:88,217,111` |
| `<key>_native_failed` | native_ad | `rb/c.java:50` |
| `<key>_preLoaded_inflate` / `<key>preLoad_inflate_return` / `<key>_native_pre_loaded_<type>` | native_ad | `sb/c.java:246,663,266` |
| `<key>_native_ad_impression` / `_native_ad_clicked` | native_ad | `sb/a.java:39,26` |

- Error messages go into `content_type`. That puts high-cardinality free text into GA4, which is truncated at 100 chars. `[inf]`
- Some event names exceed GA4's 40-char limit, for example `camera_translation_click_interstitial_inter_failed_show` (54 chars). GA4 drops these silently, so the dashboards are incomplete. `[inf]`
- There is no `setUserProperty` or `setUserId` first-party. `[obs]`

## 12. Clone recommendations `[dec]`

1. Keep the RC-driven ad model, but use **one unit per placement** so eCPM attribution happens in AdMob. Make cooldown seconds an RC value instead of a hard-coded 30 s, and turn capping on by default.
2. **Do not put ads in the IME**, and do not use a host-app deny-list. Monetize in the companion app plus an optional subscription.
3. Add a real subscription (remove-ads, plus advanced features such as offline translation or an LLM rewrite tier that the competitor lacks) with Play Billing 8.x. The `isUserSubscribed` gates show where the competitor planned to put them.
4. Replace the always-to-market rating dialog with the Play In-App Review API, triggered after N successful translations.
5. Instrument product events (keyboard_enabled, ime_session, translate_done, voice_used, theme_applied). The competitor has no funnel visibility past the ads.
6. Set the RC minimum fetch interval to 1 h or more in release builds, and do not block the splash on the fetch for 12 s.
7. Use a real Play listing name and package (the competitor ships `com.example.*`).

## 13. Open items (static-only; dynamic confirmation required)

| # | Item | Owner |
|---|---|---|
| 1 | Confirm at runtime that no paywall or IAP appears (Billing 8.3.0 vestigial; `isUserSubscribed` never written) | Monetization |
| 2 | Production Remote Config values: are capping flags, enables or unit IDs overridden? Real interstitial cadence and the splash ad rate | Backend / Monetization |
| 5 | Visual layout of the keyboard native ad inside the IME (position relative to the smartbar, size, close affordance), and of native `type` 1 vs 2 | Native/UX |
| 6 | FCM topic name and whether marketing pushes are sent (`gj/i.java:190-200`) | Backend |
| 7 | Whether the 86-package deny-list (`FlorisImeService.java:683`) suppresses ads on any other host classes (banking, password fields) via EditorInfo flags. Static check found only the package list | Native/UX |
| 8 | Whether Crashlytics and Perf are actually collecting (no first-party init found; auto-init via registrars) | Antitamper / Backend |
