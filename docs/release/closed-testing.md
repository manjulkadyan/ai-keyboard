# Closed testing release (Sprint 4)

This guide takes the Sprint 4 build to a Play closed testing track. The owner of the Play Console account and the
GCP project does these steps. Nothing here runs in CI.

Replace these placeholders everywhere:

- `PROJECT_ID`: the Firebase and GCP project.
- `DOMAIN`: the host of kb-api (RN-08 is still open, so the strings use `example.com`).
- The application id is `com.manjul.aikeyboard`. Debug and beta builds use the suffixes `.debug` and `.beta`.

Do the sections in order. Billing and RTDN need kb-api deployed first (`backend/deploy/README.md`).

## 1. Before you start

1. Deploy kb-api with `backend/deploy/README.md`, sections 1 to 9.
2. Make sure that the deployed kb-api answers `POST /v1/account/delete` and `GET /delete-account`.
3. Keep the entitlement public key (`entitlement.pub.pem`, section 9 of the deploy README) for step 6.
4. Make an upload key if you do not have one. Keep the keystore out of the repo.

```sh
keytool -genkeypair -v -keystore ~/keys/aikb-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

## 2. Subscription product

Product and plan ids are fixed in code (`ProCatalog`, Sprint 4 decision 1). Use them exactly.

1. In Play Console, open Monetize → Products → Subscriptions.
2. Create the subscription with the product id `pro`.
3. Add the base plan `monthly`: auto-renewing, billing period 1 month, price $2.99 (RN-02).
4. Add the base plan `annual`: auto-renewing, billing period 1 year, price $19.99 (RN-02).
5. On `annual`, add an offer for new customers. Give it one free trial phase of 7 days.
6. Set the trial eligibility to "New customer acquisition: never had this subscription".
7. Activate both base plans and the offer.

The app reads the trial length from the pricing phases, not from the offer id. You can choose any offer id.

## 3. Licence testers

1. In Play Console, open Settings → License testing.
2. Add the Google accounts of the testers.
3. Set the licence response to `RESPOND_NORMALLY`.
4. Add the same accounts to the closed testing tester list (step 7).

Licence testers get test cards and short renewal periods (for example, 5 minutes for 1 month). Device rows B.1
to B.6 in `docs/sprint-4-device-checklist.md` need a licence tester.

## 4. RTDN topic and push subscription

The full commands are in `backend/deploy/README.md`, section 9, "RTDN". In short:

1. Create the Pub/Sub topic `play-rtdn`.
2. Give `google-play-developer-notifications@system.gserviceaccount.com` the role `roles/pubsub.publisher` on it.
3. Create the service account `rtdn-push`.
4. Create the push subscription `play-rtdn-push` to `https://DOMAIN/internal/rtdn`, with OIDC auth from
   `rtdn-push@PROJECT_ID.iam.gserviceaccount.com` and the audience `https://DOMAIN/internal/rtdn`.
5. Set `RTDN_AUDIENCE` and `RTDN_SERVICE_ACCOUNT` in `backend/deploy/cloudrun.yaml` to the same values. Deploy again.
6. In Play Console, open Monetize → Monetization setup.
7. Set the topic name to `projects/PROJECT_ID/topics/play-rtdn`.
8. Click "Send test notification". Make sure that the kb-api log shows a 204 for `/internal/rtdn`.

If `RTDN_AUDIENCE` or `RTDN_SERVICE_ACCOUNT` is not set, kb-api answers 401 to every push. Then refunds and
revocations do not remove Pro (device row B.5).

## 5. App content declarations

### Ads

1. In Play Console, open Policy → App content → Ads.
2. Answer "Yes, my app contains ads".

The build has these ad placements (TRD §11.6). They are all in the app process. No ad shows in the keyboard.

| Placement | Format | Where | Remote Config key | Shows in this build |
|---|---|---|---|---|
| `app_open` | App open | App start, second session or later. Never from PROCESS_TEXT, SEND or a deep link | `ad_unit_app_open` | Yes |
| `inter_feature_exit` | Interstitial | Home, after 2 completed feature uses | `ad_unit_inter_feature_exit` | Yes |
| `banner_home` | Banner | Home | `ad_unit_banner_home` | Yes |
| `native_translator` | Native | Translator screen | `ad_unit_native_translator` | Yes |
| `native_history` | Native | Translator history | `ad_unit_native_history` | Yes |
| `native_dictionary` | Native | Dictionary screen | `ad_unit_native_dictionary` | No (no UI yet) |
| `rewarded_game_hint` | Rewarded | Word game hint | `ad_unit_rewarded_game_hint` | No (no UI yet) |

Before the release, do these steps in AdMob and Remote Config:

1. Create one AdMob ad unit for each placement that shows in this build.
2. Put each ad unit id in its Remote Config key. The compiled-in defaults are Google's sample ids.
3. Make sure that the store listing does not show a placement that the build does not have.

### Advertising ID

1. In Policy → App content → Advertising ID, answer "Yes".
2. Pick the uses "Advertising or marketing" and "Analytics".

The GMA SDK adds the `AD_ID` permission to the merged manifest. Only the free tier in the app process uses the
advertising id.

### Data safety

1. Fill the form from `docs/release/data-safety-draft.md`. `docs/data-flows.md` is the source of truth.
2. For "Users can request deletion", give the URL `https://DOMAIN/delete-account`.
3. Set `DELETION_CONTACT_EMAIL` on kb-api before you submit.

### Target audience and content rating

1. In Policy → App content → Target audience and content, pick the age groups 18 and over.
2. Answer "No" to "appeal to children". The app is not for children (PRD §10.6, "Families").
3. In Content rating, fill the IARC questionnaire with the category "Utility, Productivity, Communication, or Other".
4. Answer "Yes" to "shares user content with other users or the app developer" only if Play counts AI input as user
   content. Use the same choice as the Data safety form ("Other user-generated content").
5. Answer "No" to the questions about violence, sexual content, gambling and controlled substances.
6. Do the other App content items: privacy policy URL, app access (no login needed), news app "No", government
   app "No", financial features "None", health "None".

Product approves the age groups. The PRD only says "not targeted at children".

## 6. Build the release bundle

Put the values in `~/.gradle/gradle.properties`. Never put them in the repo.

```properties
aikb.signing.storeFile=/Users/you/keys/aikb-upload.jks
aikb.signing.storePassword=...
aikb.signing.keyAlias=upload
aikb.signing.keyPassword=...
aikb.entitlement.publicKey=<base64 body of entitlement.pub.pem>
aikb.admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
aikb.ai.baseUrl=https://DOMAIN/
aikb.firebase.projectId=PROJECT_ID
aikb.firebase.appId=...
aikb.firebase.apiKey=...
aikb.firebase.projectNumber=...
```

Then do these steps:

1. Raise `projectVersionCode` in `gradle.properties`. Play refuses a version code that it already has.
2. Run `./gradlew :app:bundleRelease`.
3. Run `jarsigner -verify -verbose:summary app/build/outputs/bundle/release/app-release.aab`.
4. Make sure that the signer is your upload key, not "CN=Android Debug".
5. Keep `app/build/outputs/mapping/release/mapping.txt` for this version code.

If one of the four `aikb.signing.*` values is missing, the build signs with the debug key. Play refuses that bundle.
If `aikb.entitlement.publicKey` or `aikb.admob.appId` is missing, `bundleRelease` fails.
The beta build type has no such check. Without `aikb.entitlement.publicKey` a beta build fails closed: it trusts
no Pro cache, so every user stays free. Without `aikb.admob.appId` it uses Google's sample AdMob app id, which
serves only test ads. Set both values before you hand a beta build to testers who need Pro or real ads.

The Crashlytics Gradle plugin does not upload the R8 mapping file, because the build has no Google Services plugin.
Each build writes a new random mapping file id into the bundle. Crashlytics uses this id to find the mapping file.
Upload the mapping file with the resource file of the same build, so that crash stacks are readable:

```sh
firebase crashlytics:mappingfile:upload --app=<aikb.firebase.appId> \
  --resource-file=app/build/generated/res/injectCrashlyticsMappingFileIdRelease/values/com_google_firebase_crashlytics_mappingfileid.xml \
  app/build/outputs/mapping/release/mapping.txt
```

Do this before the next build. The next build writes a new id into the same resource file.
Make sure that the id in the resource file is not `00000000000000000000000000000000`.

## 7. Upload to closed testing

1. In Play Console, open Test and release → Testing → Closed testing.
2. Create a track, or use the default "Alpha" track.
3. In Testers, add an email list with the licence testers (step 3). Copy the opt-in link.
4. Click "Create new release". Accept Play App Signing if Play asks.
5. Upload `app/build/outputs/bundle/release/app-release.aab`.
6. Write the release notes. Say that this is a closed test build.
7. Click "Next". Fix every error. Read every warning.
8. Click "Save", then "Send changes for review".
9. Send the opt-in link to the testers. They install from the Play Store link, not from a side-loaded APK.

After the release is live, do these steps:

1. Open Testing → Pre-launch report for the version code. Make sure that it shows no crash and no ANR (row G.2).
2. Run the device rows in `docs/sprint-4-device-checklist.md`.
3. Copy the app signing key SHA-256 from Setup → App signing into the Firebase app settings.
4. Turn on App Check enforcement only after D.1 and B.1 pass on this build (deploy README, section 7).
