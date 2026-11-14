# OD-21: Drop conversation mode and camera translation

Status: **decided: both features leave this app.** They move to a separate future app. Owner: Product. Date:
2026-11-14 (before Sprint 5).

## Question

PRD FR-19 (voice conversation mode) and PRD FR-20 (camera translation) were v1 scope for Sprint 5. TRD FR-14 and
FR-15 hold their designs. Neither feature has any code yet. Two code comments only say that Sprint 5 adds them. Do
these features belong in a keyboard app?

## Decision

- **Drop PRD FR-19 and PRD FR-20 from this app.** They belong in a separate future app. If we add them here, the
  app gets too heavy and loses its keyboard focus.
- **Keep the requirement text.** The PRD marks FR-19 and FR-20 as moved out and points to this decision. The text
  stays as input for the future app.
- **No code change is needed to remove anything.** No screen, route, table or permission exists for these features.
  The two comments that promised them now say that they are out of scope.

## What is out of scope

- PRD FR-19 conversation mode (TRD FR-14) and PRD FR-20 camera translation (TRD FR-15), including the v2 live
  camera overlay.
- Jobs J9 (read a sign or menu with the camera) and J10 (a face-to-face conversation across languages).
- The `CAMERA` permission. The app does not request it.
- The `conversation` and `camera` routes. They do not go on the `RouteRegistry` allow-list (PRD FR-32).
- The Conversation and Camera tiles on the Home hub (screen S-04), and screens S-13, S-14 and S-15.
- The TRD parts that only these features use: CameraX, ML Kit Text Recognition v2, `:core:ocr`,
  `:feature:conversation`, `:feature:camera`, the `conversation_session` and `conversation_message` tables, the
  `cache/ocr/` folder and the `/v1/ocr` endpoint.
- The `conversation` and `camera` values of `TranslateSurface`, and the `conversation_turn` and
  `camera_translate_done` events.

## What it saves

About 7 raw dev-days in Sprint 5: 2.5 days for voice conversation plus 4.5 days for the camera translator. Sprint 5
goes from 13 days to 6 days. The v1 roll-up goes from 88.5 to 81.5 days (TRD §8.1), and from 104.5 to 97.5 days
after the TRD §11.9 additions. The APK also stays smaller, because it does not bundle CameraX or the text
recognition models.

## What it costs

- Persona P3 Omar loses camera OCR and conversation mode. Two of his "What wins them" items are gone. The offline
  translate bar still serves him in chat.
- Both target apps (App H and App L) ship camera and conversation translation. Many other competitors do too. Our
  store listing has fewer features to show, and we cannot rank for "camera translator" or "conversation
  translator" keywords.
- Translations/day loses two of its five sources (PRD §5). The targets stay the same until real data exists
  (PRD OD-12 re-baseline).

## Effect on other decisions

- **TRD OD-10 (Arabic-script OCR) no longer applies to this app.** It only existed for camera translation. TRD R-09
  closes for the same reason. The future app owns this question.
- PRD OD-10 (glide typing) is a different decision and does not change. TRD OD IDs are local to the TRD.

## Notes

- The code comments are in `app/src/main/kotlin/com/manjul/aikeyboard/fork/routes/RouteRegistry.kt` and
  `core/analytics/src/main/kotlin/com/manjul/aikeyboard/core/analytics/AnalyticsEnums.kt`. Both are our own fork
  files, so they need no `FORK` marker.
- PRD sources: FR-19, FR-20, FR-32, J9, J10, NG-5, §5, §6 and §9.
- TRD sources: §4.5, §4.10, Sprint 5, §8.1, §11.9, OD-10 and R-09.
