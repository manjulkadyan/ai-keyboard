# Team 3: Monetization & SDKs — Voice Keyboard - Voice Typing v1.3

App: `com.logiapps.language.keyboard.voice.typing.translator`, versionName 1.3, versionCode 4. It is a FlorisBoard fork (Apache-2.0), built with Kotlin and Compose and heavily R8-obfuscated (10684 classes in `defpackage/`).
This is a static-only pass. I use the same tags as the sibling reports: `[obs]` means read directly from the manifest, resources or decompiled source. `[dec]` is a decision or recommendation for the clone. `[inf]` is an inference that still needs confirmation. Anything about runtime timing or server values is `[inf]` and has a numbered open item in §11.
Paths are relative to `ai-keyboard/voicetyping_work/`. Sources are in `jadx_out/sources/` and resources in `jadx_out/resources/`.
AdMob unit IDs ship inside every APK, so they are not treated as secrets and are given in full. API keys are redacted to the first 8 and last 4 characters. No string tables or assets are reproduced.

**Headline.** The app makes money from ads only, using AdMob next-gen as the single network with no mediation and no billing [obs]. Its most unusual feature is a **native ad rendered inside the keyboard (IME)** while the user types in other apps. That ad is suppressed for an 86-entry hard-coded list of Google apps and launchers [obs] (§2.3). There is a dormant `is_user_premium` flag, but nothing ever sets it to true [obs].

---

## 1. Ad SDK roster

| SDK | Evidence | Version | Wired? |
|---|---|---|---|
| **Google Mobile Ads next-gen** (`ads_mobile_sdk/`, 1734 files; `com.google.android.libraries.ads.mobile.sdk.*`) | `base/ads-mobile-sdk.properties` | **1.4.0** [obs] | Yes. This is the only ad renderer: 409 first-party/defpackage files reference it [obs]. |
| Legacy `com/google/android/gms/ads` | only `mediation/` stubs plus UMP/GMS internals (`hia`, `zx9`, `cd9`, `si9`, `b9`) | — | No. It comes in transitively. Do not count it as a second ad stack [obs]. |
| **UMP consent** (`com/google/android/ump`) | `base/user-messaging-platform.properties` | **4.0.0** [obs] | Yes. The splash shows a GDPR gate whose result writes `PREF_IS_GDPR_SHOWN_TO_USER` / `PREF_CAN_USER_REQUEST_ADS` (`defpackage/ds7.java:60-71`) [obs]. |
| Firebase Remote Config | `"sdkVersion","23.1.0"` in sources; `fire-rc` registrar | 23.1.0 [inf] | Yes. It holds every ad unit ID and flag (§7). |
| Firebase Analytics | `base/firebase-analytics.properties`, `"fire-analytics","23.2.0"` | 23.2.0 [obs] | Yes. This is the only analytics sink (§9). |
| Firebase Crashlytics / Perf / Sessions / Installations / ABT / FCM | `fire-cls` 20.1.0, `fire-perf`, `fire-sessions`, `fire-abt`, `fire-fcm` | — | Crashlytics, Perf and FCM are wired. ABT ships only as an RC dependency and has no first-party experiment code [inf]. |
| Play In-App Update | `base/app-update.properties`, `app-update-ktx` | 2.1.0 [obs] | Partly. The library and an update dialog UI (`defpackage/tm.java:149,158`) are present, but I could not resolve the trigger path (open item 6). |
| Play In-App Review | — | — | **Absent.** There is no `review` artifact, and "Rate us" is a custom dialog that opens `market://` (§8.2) [obs]. |
| Play Billing | no `com.android.billingclient`, no `BillingClient`, no product-id strings | — | **Absent** [obs]. |
| Shimmer (`com/facebook/shimmer`) | `ShimmerFrameLayout` | — | UI placeholder only. This is not Facebook Audience Network [obs]. |

**Not present** (checked by grepping for the package roots): Meta Audience Network, AppLovin/MAX, ironSource, Unity, Vungle/Liftoff, Mintegral, Pangle, InMobi, Chartboost, AppsFlyer, Adjust, Branch, Amplitude, Mixpanel, RevenueCat, Adapty, Qonversion [obs]. **One network, no mediation, no MMP, no paid-event (tROAS) logging.** `grep` finds no `onPaidEvent`/`AdValue`/`valueMicros` in first-party code [obs].

### 1.1 Initialisation

- There are two `MobileAds` init paths in `defpackage/o7.java` [obs]:
  - **case 0** (lines ~62-75): app ID hard-coded as `ca-app-pub-3051…~…1938`. It queues callers on `p7`. **This is the keyboard-process path**: `wy2.java` loads the in-IME ad through `p7.a.a(...)`.
  - **case 1** (lines ~80-95): app ID taken from `R.string.admob_app_id` (`res/values/strings.xml:25`). It queues callers on `kr`. **This is the app path**: interstitial and app-open loads go through `kr.a.a(...)` (`dn3.java` `t()`, `dt.java:53`).
- Both paths pass a `RequestConfiguration` with a **hard-coded test-device ID** `B3EEABB8…9ECB` (`o7.java:64,85`) and `MAX_AD_CONTENT_RATING_UNSPECIFIED` [dec]. Correction (class sweep, 2026-10-17): the same constant appears inside the AdMob SDK (`ads_mobile_sdk` `sv2.java:14`, `y91.java:52`, `OfflinePingSender`), so it is the SDK's emulator test-device constant, not a developer device, and not a template tell [dec].
- Manifest meta-data [obs]: `com.google.android.gms.ads.APPLICATION_ID`, `flag.OPTIMIZE_INITIALIZATION` and `flag.OPTIMIZE_AD_LOADING` (`AndroidManifest.xml:101-107`). Ad permissions: `AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_AD_ID` (lines 54-59).

### 1.2 Ad unit IDs (complete)

All of these come from `res/xml/remote_config_defaults.xml` (90 entries) unless noted. **No ad unit ID is hard-coded in Java.** Units always come from Remote Config and fall back to the XML defaults [obs].

| Role | ID | Used by |
|---|---|---|
| App ID | `ca-app-pub-3051…~…1938` | `strings.xml:25` and `o7.java:64` |
| Interstitial | `ca-app-pub-3051…/…5092` | all 10 interstitial placements |
| Banner | `ca-app-pub-3051…/…7392` | 4 banner placements |
| App open | `ca-app-pub-3051…/…4091` | `app_open_main_id` |
| Native | `ca-app-pub-3051…/…0652` | 6 in-app native placements plus the in-keyboard native |

**Correction to the triage brief:** there are **4 production ad units plus 1 app ID**, not 5 units. The `ca-app-pub-0000000000000000~…0000` match is the UMP SDK's own placeholder in an error message (`hia.java:217`, `zx9.java:84`). It is not a test unit used by the app [obs].

---

## 2. Per-placement ad table

### 2.1 Interstitials (one shared preloaded slot)

Gate code: `defpackage/dn3.java:71` `A(activity, model, nextModel, eventPrefix, cb)`. Loader: `dn3.java:1122` `t(...)` [obs].

| RC prefix (`<p>_interstitial_*`) | js6 field | Call site | Event prefix | Trigger | `capping_enabled` default |
|---|---|---|---|---|---|
| `splash` | `i` | `ac3.java:1567,1596`, `ay3.java:68` | `splash_interstitial` | end of the splash wait window (§2.5) | false |
| `home_word_game` | `j` | `r60.java:188` | `word_game_interstitial` | open the Guess-Word game | **true** |
| `home_main_card` | `q` | `r60.java:196` | `home_main_card_click_inter` | tap the home hero card | false |
| `home_sticker_click` | `k` | `vm.java:124` (grid index 0) | `home_sticker_click_inter` | Stickers tile | false |
| `home_translation_click` | `l` | `vm.java:56` (index 1) | `translation_click_inter` | Translator tile | false |
| `home_dictionary_click` | `m` | `vm.java:88` (index 3) | `dictionary_click_inter` | Dictionary tile | false |
| `theme_click` | `n` | `hc8.java:93` | `theme_click_interstitial` | tap a keyboard theme | false |
| `save_theme` | `o` | `xg.java:121` | `saveThemeInterstitialAdmob` | save/apply a custom theme | false |
| `leave_theme` | `p` | `wj.java:58` | `leaveThemeInterstitialAdmob` | leave the theme editor | false |
| `translate_button` | `r` | `oh8.java:5111-5361` (7 sites) | `translate_button_inter` | **every Translate press** while online | false |

Mechanics [obs]:
- `js6.d()` (`js6.java:187`) loops over the 10 prefixes and builds `InterstitialAdModel(admobId, isEnabled, shouldLoad, cappingEnabled)` from RC.
- **Show gate** (`dn3.A`):
  1. `isAdEnabled`.
  2. `!js6.f` (the `_isUserSubscribed` StateFlow).
  3. `js6.a()`, which equals `!subscribed && PREF_CAN_USER_REQUEST_ADS` (the UMP result).
  4. **30-second global cooldown** (`dn3.java:94`, `30000 - (now - p)`), applied *only if* that placement's `capping_enabled=true`. With the defaults, only `home_word_game` is capped.
  5. If an ad is loaded (`dn3.k`): show a "loading ad" dialog (`bn8.y`, `layout_ad_loading.xml`), then show the ad after a **1000 ms** delay (`dn3.java:115`).
  6. If nothing is loaded, it calls `t()` to start a load and **lets the user through without an ad**.
- **Load gate** (`dn3.t`): requires `shouldLoadNextAd`, a non-empty id, network, and no load or ad already in flight (`j`/`k`). There is a single global slot (`dn3.i`), and `onAdDismissed` immediately reloads (`by3.java:69-71`).
- [inf] In practice, an uncapped placement shows an ad on every tap whenever one is ready. Because the slot reloads on dismiss, the translate button will interrupt most Translate presses. The home grid has an extra 600 ms tap debounce (`vm.java:49`), and grid index 4 navigates without an ad.

### 2.2 Banners

Model: `BannerAdModel(id, enabled, collapsing, large, top)` via `js6.c(...)` (`js6.java:154`). The renderer is `y61.java` / `u70.java` [obs].

| RC prefix | Screen (route) | Call site | Defaults |
|---|---|---|---|
| `main_screen_` | `BottomNavHomeScreenRoute` | `bc3.java:245` | enabled, not collapsing, standard size, bottom |
| `customization_screen_` | `CustomizeKeyBoardScreenRoute` | `ws8.java:1201` | same |
| `enable_screen_` | `KeyboardEnableScreenRoute` (onboarding) | `hj5.java:295` | same |
| `translation_screen_` | `TranslationScreenRoute` | `eh8.java:116` | same |

- `is_banner_large=true` selects a 100 dp banner (`w70.a(activity, isLarge100dpBanner)`). Otherwise the banner uses adaptive sizing [obs] (`y61.java:108`).
- `is_banner_collapsing=true` adds the extra `collapsible = top|bottom`, using `is_banner_top` to choose (`y61.java:131`, `u70.java:79`) [obs].
- The banner uses a shimmer placeholder (`banner_ad_shimmer_layout.xml`) and hides itself if the computed height is 0 or less.

### 2.3 Natives

Model: `NativeAdModel(id, enabled, ctaColor, type)` via `js6.e(...)` (`js6.java:158`). Renderer: `ak5.java`, with shimmer placeholders in `z70.java` [obs].

| RC prefix | Screen | Call site | Wired? |
|---|---|---|---|
| `main_screen_native_*` | Home | `ut5.java:1312` | yes (event prefix `home_screen_native`) |
| `guess_word_screen_native_*` | Guess-Word game | `bh3.java:463` | yes (`word_guess_game_native`) |
| `settings_screen_native_*` | Settings | `rd7.java:330` | yes (`settings_native`) |
| `translator_screen_native_*` | — | none | **Defined in RC, never read** [obs] |
| `customization_screen_native_*` | — | none | **Defined in RC, never read** [obs] |
| `enable_screen_native_*` | — | none | **Defined in RC, never read** [obs] |

`native_type` → layout (`ak5.java:90-460`) [obs]:

| type | Layout |
|---|---|
| 1 | `native_first_banner_admob` |
| 3 | `native_third_medium_media_admob` (MediaView) |
| 4 | `native_fourth_media_admob` (MediaView) |
| 5 | `native_fifth_banner_admob` |
| anything else, including the default **2** | `native_second_banner_admob` (no MediaView, "Ad" badge) |

`*_native_cta_color` (default `#FF0000`) tints the CTA button.

### 2.4 Ads inside the keyboard: yes

This is the key monetization differentiator, and also the main policy risk.

- **Data path** [obs]:
  - `js6.h()` (`js6.java:276`) copies RC `my_keyboard_native_ad_keyboard_id`, `_enable`, `_button_color` and `_time`, plus `PREF_CAN_USER_REQUEST_ADS` and `is_user_premium`, into a separate SharedPreferences store (`q91`).
  - The IME process reads that store, because it cannot use the app's DI graph. The log line says "Syncing to Keyboard DB".
- **Show gate**: `FlorisImeService.onStartInputView` (`dev/patrickgold/florisboard/FlorisImeService.java:570-620`) [obs]. The ad is allowed when all of these hold:
  - the user profile is unlocked;
  - `editorInfo.packageName` is **not** this app;
  - the package is **not** on a hard-coded 86-entry deny-list (line 586);
  - `my_keyboard_native_ad_enable` is true;
  - `is_user_premium` is false;
  - `PREF_CAN_USER_REQUEST_ADS` is true.

  The result goes to the static StateFlow `FlorisImeService.x`.
- **Deny-list contents** [obs]:
  - about 55 Google first-party apps: Search, Chrome (all channels), Play Store, Gmail, YouTube (all flavours), Maps, Messages, Gboard, Translate, Docs/Sheets/Slides, Photos, Keep, Calendar, Contacts, Dialer, Assistant, Family Link, Classroom, Meet, Lens, Files and others, plus Snapseed;
  - about 29 OEM and third-party launchers: Pixel, Samsung One UI, MIUI, Huawei, OPPO/ColorOS, realme, vivo, OnePlus, Sony, LG, Motorola, Asus, Lenovo ZUI, Microsoft, Nova, Lawnchair, Action, Smart Launcher and others;
  - `com.android.settings.intelligence`;
  - `com.sh.smart.caller`.

  [inf] This looks designed to keep the ad away from the surfaces Google staff and policy reviewers use most, so the ad mostly shows in third-party apps (messengers, social). I read it as a policy-evasion pattern.
- **Render** [obs]:
  - It lives inside FlorisBoard's `ImeUi` composable (`defpackage/n21.java:1040-1180`) and also requires network.
  - It shows a shimmer while loading, then an `AndroidView` that inflates `native_banner_small_compose_admob.xml`: a `NativeAdView` 60sdp tall with an icon, title, body, advertiser, an "Ad" badge, and a CTA button tinted from `my_keyboard_native_ad_button_color` (code default `#2567FC`, RC default `#FF0000`; `k5.java:411`).
  - [inf] It is full width in the keyboard column. I am not sure whether it sits above or below the key area.
- **Load/refresh** [obs]:
  - `wy2.java:50-100` loads through the keyboard-process init (`p7`), with a `KeyboardMainNativeAdModel`.
  - Cooldown: `my_keyboard_native_ad_time`. **A value under 1000 is treated as minutes and multiplied by 60000** (`n21.java:1092-1096`). The RC default is `600000`, which is 10 minutes in ms; the code fallback is `10`, which also means 10 minutes.
  - A new ad loads only when the keyboard is shown and `now > keyboardBannerPreviousTime + cooldown`. On load it writes `keyboardBannerPreviousTime` and `isBannerFirstAd=false` (`x6.java:183-188`).
  - No in-keyboard analytics events are emitted. The logs use the tags `KeyboardNativeBanner` and `Native_keyboard_Ads_`.
- I found no `inputType` / password-field exclusion in the gate [obs]. Whether FlorisBoard hides the ImeUi area in password fields is open item 3.
- [dec] **Do not clone the in-IME ad.** Play's Ads policy forbids ads shown outside the app's own UI and ads that interfere with other apps. An ad inside a system keyboard that appears in third-party apps falls squarely in that zone. The deny-list makes it look intentional, which raises enforcement risk to account level.

### 2.5 App open + splash sequence

- **App open** (`defpackage/dt.java`, "ComposeOpenAppManager") [obs]:
  - It registers `ActivityLifecycleCallbacks` and ProcessLifecycle `onStart` (`dt.java:135`).
  - It shows on foreground when all of these hold: not premium, `app_open_main_show`, ad loaded less than **4 h** ago (`14400000`, `dt.java:64`), current activity is not `AdActivity` or `AudioPermissionActivity`, no interstitial showing (`dn3.o`), and the current route is not `SplashMainScreenRoute` (`p65`, `dt.java:159`).
  - Otherwise it fetches a new ad.
- **Splash** (`fs7.java`, `ac3.java:1440-1600`, `ds7.java`, `es7.java`) [obs]:
  1. Sync the premium flag (always false; §3).
  2. Global timeout: **12 000 ms** when online and not premium, otherwise **3 000 ms** (`fs7.java:103`).
  3. Fetch Remote Config.
  4. Run the UMP/GDPR gate if it has not been shown yet. If the global timeout fires first, the app moves on and the GDPR callback is ignored (`ds7.java:55`, `es7.java:76`).
  5. `afterConsentStart`: offline means a 3 s wait and no ad. Online means loading `splash_interstitial` and starting the `gs7` CountDownTimer wait window. When the ad is ready, it is shown after **2000 ms** (`ac3.java` → `ay3`).
  6. Move to the next route (`AppLanguageScreenRoute` / `KeyboardEnableScreenRoute` / home). [inf] The exact first-run branching was not traced (open item 5).

### 2.6 Placement summary

The app has 10 interstitial placements (on 1 unit), 4 banners, 3 live natives (plus 3 dead RC natives), 1 app-open ad and **1 in-keyboard native** [obs]. No rewarded or rewarded-interstitial ads are used. The only `rewardedAd` strings are inside `ads_mobile_sdk` [obs].

---

## 3. Billing catalog

**There is none** [obs]:
- no Play Billing library, no `BillingClient`, no SKU/product ID, base plan, offer token, trial or price string;
- no "premium", "pro", "remove ads", "subscribe" or "unlock" strings outside FlorisBoard's device-lock messages;
- no RevenueCat/Adapty/Qonversion.

**Vestigial entitlement plumbing** [obs]:
- `is_user_premium` (TinyDB `f05` and keyboard prefs `q91`) and `js6.e` (`_isUserSubscribed` StateFlow) gate every ad path: `js6.a()`, `dn3.A/t`, `dt.a/onStart`, `FlorisImeService` line 597, and the splash timeout.
- **Nothing ever writes `true`.** Every write copies the value back from the same prefs (`fs7.java:87-100`, `js6.java:300-302`), so the flag stays false for the whole lifetime [obs].
- [inf] The white-label template ("ZTech" / `voicetypingkeyboard_z`) supports a subscription SKU in other builds, and this build ships with billing stripped out.

[dec] A/B pricing variants: none exist to copy.

## 4. Paywall triggers + layout reconstruction

- **There is no paywall screen, dialog or route.** The 20 navigation routes (§8.1) contain no Premium/Subscription route, and `res/layout` has no premium layout [obs].
- The "soft paywall" here is the interstitial-on-action pattern in §2.1. Feature entry points (Translator, Dictionary, Stickers, Guess-Word, theme apply and save, Translate button) cost an ad view rather than money [obs].
- [dec] For the clone, if we want a "remove ads" upsell, it is new design work rather than reconstruction. A natural trigger is after the Nth interstitial or on the theme-save action.

## 5. Free-tier caps and where they are enforced

| Cap | Value | Enforced at | Monetization? |
|---|---|---|---|
| Translation input length | 10 000 chars; string `limit_reached_maximum_characters_allowed` (`strings.xml:207`) | `defpackage/my3.java` (translate call, see backend.md) | No. It guards the unofficial endpoint and is not an upsell [obs]. |
| Interstitial frequency | 30 s global cooldown, only for placements with `capping_enabled=true` | `dn3.java:94` (client-side) | Ad pacing [obs] |
| App-open freshness | 4 h ad expiry | `dt.java:64` | Ad pacing [obs] |
| In-keyboard ad refresh | 10 min | `n21.java:1092` | Ad pacing [obs] |
| Guess-Word hints | "Hint" button (`bh3.java:928`), no ad or limit found | — | [inf] unlimited and free |

There are no watermarks, daily quotas, feature locks or ad-for-unlock (rewarded) flows [obs].

---

## 6. Ad-frequency and policy observations

1. **Translate button** interstitial, uncapped by default [obs]. [inf] This is very disruptive and is the most likely source of "too many ads" reviews.
2. **Ad before navigation** on 6 home and theme entry points, with a 1 s blocking "loading ad" dialog before each one [obs].
3. **In-IME native ad** in third-party apps, with a Google/launcher deny-list (§2.4) [obs]. [inf] High Play-policy risk.
4. **Banner + native on the same screen**: Home has both `main_screen` banner and native. The Enable-keyboard onboarding screen has a banner. Its native placement is defined but unused [obs].
5. UMP consent is honoured: `PREF_CAN_USER_REQUEST_ADS` gates every load, including the IME [obs]. [inf] When the 12 s splash timeout beats the consent form, the flag keeps its default (`false`), so no ads load that session. That fails safe.

---

## 7. Remote Config keys (complete: 90 defaults, 6 directly-read keys plus generated keys)

Fetch setup: `js6` constructor (`js6.java:44-150`). `minimumFetchIntervalInSeconds = 86400` (24 h, `js6.java:59`), and defaults are parsed from `R.xml.remote_config_defaults` [obs]. `js6.b()` and `js6.f()` run fetch-and-activate and then rebuild the models. [inf] A 24 h interval means server changes take up to a day to reach existing users. There are **no non-ad keys**: no force-update, paywall or feature flags [obs].

| Key pattern | Type | Default | Reader |
|---|---|---|---|
| `{splash,home_word_game,home_main_card,home_sticker_click,home_translation_click,home_dictionary_click,theme_click,save_theme,leave_theme,translate_button}_interstitial_admob_id` | string | `…/…5092` | `js6.d()` (concatenated keys) |
| `…_interstitial_is_enabled` | bool | true | same |
| `…_interstitial_should_load` | bool | true | same |
| `…_interstitial_capping_enabled` | bool | **true only for `home_word_game`**, false for the rest | same |
| `{main,customization,enable,translation}_screen_banner_Id` | string | `…/…7392` | `js6.c()` |
| `…_screen_is_banner_enabled` / `_collapsing` / `_large` / `_top` | bool | true / false / false / false | same |
| `{main,customization,guess_word,translator,settings,enable}_screen_native_Id` | string | `…/…0652` | `js6.e()` (main, guess_word, settings only) |
| `…_screen_is_native_enabled` | bool | true | same |
| `…_screen_native_cta_color` | string | `#FF0000` | same |
| `…_screen_native_type` | long | 2 | same |
| `app_open_main_id` / `app_open_main_show` | string / bool | `…/…4091` / true | `js6.g()` (`js6.java:263`) |
| `my_keyboard_native_ad_keyboard_id` | string | `…/…0652` | `js6.h()`, then keyboard prefs |
| `my_keyboard_native_ad_enable` | bool | true | same |
| `my_keyboard_native_ad_button_color` | string | `#FF0000` | same |
| `my_keyboard_native_ad_time` | long | 600000 | same (values under 1000 are treated as minutes) |

Count check: 40 interstitial + 20 banner + 24 native + 2 app-open + 4 keyboard = **90** [obs].
A/B testing: ABT ships with RC, but no first-party experiment API is used. [inf] Any experiments run through RC conditions on the server side (open item 2).

---

## 8. Growth hooks

### 8.1 Onboarding to enable the keyboard

- Routes (`presentationLayer.navigation.routes.MainScreenRoute.*`) [obs]: SplashMain, AppLanguage, KeyboardEnable, KeyboardLanguage, KeyboardTranslationBarLanguage, LanguageMain, BottomNavHome, Translation, Camera, Dictionary, GuessWordGame, Sticker, AddSticker, CustomizeKeyBoard, AppKeyboardCustomThemeCropView, SettingMain, plus `BottomScreenRoutes.KeyBoardThemeScreenRoute`.
- **KeyboardEnableScreen** (`hj5.java`) is a 2-step flow: `step_1_of_2` enables the IME in system settings, and `step_2_of_2` selects it [obs].
  - Step 1 uses `ACTION_INPUT_METHOD_SETTINGS` with `:settings:fragment_args_key = <pkg>/FlorisImeService` to deep-highlight the row (`ct0.java` case 6).
  - Step 2 uses `InputMethodManager.showInputMethodPicker()` (`ct0.java:57`, `se4.java:841`, `uh.java:244`).
  - Selection state is observed through `Settings.Secure default_input_method` (`wq1.java:1633`).
  - This screen carries the `enable_screen` banner [obs].
- [dec] Clone the 2-step enable flow, which is the standard Gboard pattern. Do not put an ad on the enable screen, because it hurts activation.

### 8.2 Rating

- "Rate Us" is a row in the drawer/settings (`kd4.java:86`). It opens a custom 5-star Compose dialog (`zi9.a`, `zi9.java:126-330`) with the image `rateus_img`, the strings `rate_us` / `your_5_star_rating_help_us_grow_and_bring_you_even_better_features`, and animated stars [obs].
- The confirm callback is `f32` case 3 (`kd4.java:132`), which **unconditionally** opens `market://details?id=<pkg>` (`f32.java:56`) [obs].
  - [inf] There is no star-threshold deflection (low stars to feedback) and no automatic prompt. It is user-initiated only.
- No Play In-App Review API is used [obs].
- [dec] The clone should use the Play In-App Review API after a success moment, such as the 3rd successful voice dictation.

### 8.3 Share, feedback, privacy

- Share app (`ct0.java` case 7): a plain `ACTION_SEND` with a Play Store link and the generic text "I found this awesome app!…" [obs]. There is no referral code, reward or deep link.
- Share translated text: `f32.java` case 0 [obs].
- Feedback: `mailto:comboduo682@gmail.com` with device info and "Feedback: <app_name> 1.3" (`ct0.java` case 8) [obs].
- Privacy policy: `https://sites.google.com/view/voicekeyboard-logi-policy/home` (`ct0.java:96`) [obs].
- Back-press: "Press back again to exit" with no exit ad or dialog (`f32.java` case 1) [obs].

### 8.4 Re-engagement

- **FCM**: `FCMService` (`com/example/voicetypingkeyboard_z/utils/fcm/FCMService.java`) only logs incoming messages and new tokens. On token refresh it calls `ws8.a0`, which subscribes to topic **`voice_typing_voice_keyboard_app`** and unsubscribes from **`voice_typing_voice_keyboard_test_app`** (`vb2.java:221,226`; `"S"` means subscribe, `"U"` unsubscribe) [obs].
  - [inf] Pushes are console-composed notification messages to that topic, shown by the system tray, with no custom handling or deep links.
- A notification-permission prompt UI exists (`enable_notifications_to_never_miss_important_updates`, `ws8.java`) and can open `APP_NOTIFICATION_SETTINGS` (`f32.java` case 2) [obs]. The trigger was not traced (open item 5).
- In-app update dialog strings "Update available" / "App update is required to use the app please update" (`tm.java`). [inf] This may be an IMMEDIATE (forced) update flow (open item 6).
- There are no widgets, streaks, daily rewards or cross-promo "more apps" links [obs].

---

## 9. Analytics event inventory (complete, static)

**Sink:** `defpackage/z8.java` `a(name, itemName, contentType)` logs Firebase event `name` with params `item_id=name`, `item_name`, `content_type` [obs]. No user properties or user ID are set by first-party code [obs].

**Screen view (1):** `screen_view` with `screen_name=screen_class="SplashScreen"` (`le0.java:228-236`). No other screens are tracked manually [obs].

**App open (7, literal):** `app_open_requested` (`j5.java:143`), `app_open_loaded` (`yb.java:138`), `app_open_failed_load` (`at.java:26`), `app_open_on_impression` (`ec.java:62`), `app_open_shown` (`ec.java:71`), `app_open_failed_to_show` (`bt.java:23`), `app_open_dismiss` (`yb.java:150`) [obs].

**Splash interstitial (4, literal):** `splash_interstitial_loaded` (`da2.java:217`), `splash_interstitial_inter_failed` (`at.java:36`), `splash_inter_dismiss` (`ay3.java:63`), `splash_show_failed` (`bt.java:38`) [obs].

**Interstitial, generated `<prefix>` + suffix** [obs]:

| Suffix | Source |
|---|---|
| `_dismiss` | `by3.java:69` |
| `_inter_failed` (load) | `da2.java:203` |
| `_inter_failed_show` | `da2.java:231` |

The prefixes are the 10 event prefixes in §2.1: `splash_interstitial`, `word_game_interstitial`, `home_main_card_click_inter`, `home_sticker_click_inter`, `translation_click_inter`, `dictionary_click_inter`, `theme_click_interstitial`, `saveThemeInterstitialAdmob`, `leaveThemeInterstitialAdmob`, `translate_button_inter`. This gives up to 30 names. [inf] On the load path, the prefix comes from whichever placement triggered the reload, so the names are not strictly per-placement.

**Native, generated `<prefix>` + suffix** [obs]. Prefixes: `home_screen_native`, `word_guess_game_native`, `settings_native`.

| Suffix | Source |
|---|---|
| `_native_ad_requested` | `xg.java:329` |
| `_native_load_called` | `z70.java:91` |
| `_native_load_returned1` / `_native_load_returned2` | `z70.java:266` / `:114` |
| `_native_ad_impression` | `zj5.java:24` |
| `_native_ad_clicked` | `zj5.java:36` |
| `_preLoaded_inflate` | `ak5.java:64` |
| `preLoad_inflate_return` (no leading underscore) | `ak5.java:567` |
| `_native_pre_loaded_<type>` | `ak5.java:86`, for example `home_screen_native_native_pre_loaded_2` |

**Not tracked** [obs]: banner events, in-keyboard ad events, every product feature (voice typing, translate, dictionary lookup, game, sticker, theme apply), onboarding funnel steps, keyboard-enabled state, rating and share. The analytics are almost entirely ad-ops telemetry.

[dec] The clone should track the activation funnel (`onboarding_start` → `ime_enabled` → `ime_selected` → `first_dictation`), feature usage, and the `ad_impression` paid-value (`onPaidEvent`) needed for LTV.

---

## 10. Monetization model + clone spec

**What this app is** [obs]: a pure ad-arbitrage white-label app. It is a FlorisBoard keyboard wrapped in a Compose launcher app whose every feature tile is gated by an interstitial, with an extra native ad injected into the keyboard. There is no paid tier, and every lever is controlled by Remote Config without code changes.

**Clone spec** [dec]:
1. **Ad network:** AdMob next-gen (ads-mobile-sdk 1.4+) plus UMP 4.x. Keep RC-driven unit IDs with the `<placement>_{admob_id,is_enabled,should_load,capping_enabled}` schema, which is clean and worth reusing. Default `capping_enabled=true` for every placement, with a 45-60 s cooldown and a per-session cap.
2. **Interstitials:** at most these placements: splash (first launch excluded), Dictionary/Stickers entry, theme save. **Not** on the Translate button and **not** before every tile tap. Show a 1 s "loading ad" interstitial only when the ad is already loaded.
3. **Banners/natives:** Home native (type 2 layout equivalent) and Translator banner. No ad on the keyboard-enable onboarding screen.
4. **No ads inside the IME** (policy). Monetize the keyboard indirectly: themes, sticker packs, translation bar.
5. **Add what this app lacks:** a "Remove ads" one-time IAP or a low-priced subscription (Play Billing 8+/9). Possible premium perks: no ads, premium themes and gradients, unlimited conversation mode. Add a soft paywall after onboarding and a theme-lock paywall, the Play In-App Review API, and feature analytics plus paid-event revenue logging.
6. Base the keyboard on **upstream FlorisBoard (Apache-2.0)** with attribution. Do not reuse this fork's added code or assets.

---

## 11. Open items (static-only: need device/traffic or deeper tracing)

| # | Owner | Item |
|---|---|---|
| 1 | Monetization (Wave 2) | Pull the live RC values (`fetchAndActivate` on device, or a proxy with a system CA) to confirm which placements are capped or enabled server-side, the real `my_keyboard_native_ad_time`, any RC conditions or experiments, and whether the 3 dead native keys are toggled. |
| 2 | Monetization (Wave 2) | Check whether ABT experiments run on any of the 90 keys (server-side only). |
| 3 | Native/UX (Wave 2) | On device: confirm the in-IME native renders in a third-party app (for example WhatsApp), not in Chrome or Gmail. Confirm its position (above or below the keys), and **whether it shows in password / `TYPE_TEXT_VARIATION_PASSWORD` fields**. |
| 4 | Monetization (Wave 2) | Measure actual interstitial frequency on repeated Translate presses (preload latency vs. tap rate) and the 12 s splash wait in practice. |
| 5 | Native/UX (static) | Trace first-run routing after splash (AppLanguage → KeyboardEnable → Home conditions) and the notification-permission prompt trigger in `ws8.java`. |
| 6 | Backend/Monetization (static) | Resolve the In-App Update trigger: which class calls `AppUpdateManager` (`cpa`/`uy9`/`sv9` chain), whether it uses IMMEDIATE or FLEXIBLE, and what condition shows the "update required" dialog in `tm.java`. |
| 7 | Manifest (static) | Diff against upstream FlorisBoard to list every fork-added hook in `FlorisImeService` and `ImeUi` (ad gate, translation bar), so the clone can avoid them. |
| 8 | Monetization (static) | Identify `com.sh.smart.caller` on the deny-list. [inf] It is a sibling app from the same publisher or a partner that wanted no keyboard ads. |
