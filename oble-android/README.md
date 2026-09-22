# OBLE – Idea Capture Assistant (Android)

Put the phone on the table → **Start Idea Mode** → confirm everyone agrees → talk naturally in
Gujarati / Hindi / English / Hinglish → OBLE picks out only the useful ideas, merges related
points into one note, removes duplicates, stores them, and prepares them for your WhatsApp contact.

Kotlin · Jetpack Compose (Material 3) · Room · Foreground Service (microphone) · WorkManager ·
Gemini API · WhatsApp click-to-chat / WhatsApp Cloud API.

## Build

```bash
cp local.properties.example local.properties   # set sdk.dir (and optionally GEMINI_API_KEY)
./gradlew :core:test :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/oble-android.yml`) runs the unit tests and uploads the debug APK as the
`oble-debug-apk` artifact on every push.

### API keys (never committed)
* **Recommended:** open the app → Settings → *AI · Gemini* → paste your key
  (from https://aistudio.google.com). It is encrypted with an Android Keystore AES‑GCM key.
* Optional for a personal build: `GEMINI_API_KEY=` in `local.properties` (git‑ignored) or as an
  environment variable (`.env.example`). This bakes the key into *your* APK only — don't share that APK.
* The WhatsApp Cloud API token is also entered in Settings and stored encrypted.

## How it works

```
Start Idea Mode (+ consent) ─► Foreground service (type=microphone, persistent notification)
   ├─ Gemini audio engine: temporary ≤30 s AAC clips, cut at natural pauses
   │     silent clips deleted unsent · speech clips → Gemini transcription → deleted
   └─ Android engine: SpeechRecognizer (gu-IN / hi-IN / en-IN / auto on Android 14+), no files
        ▼
Transcript segments saved (analyzed=false) ─► rolling ConversationBuffer
        ▼  (every ~45 s with enough new speech, after a silence, or when large; never per fragment)
Gemini analysis → JSON {is_idea,title,summary,category,action_items,confidence,type,merge_into_id}
        ▼  confidence ≥ threshold (Settings, default 70 %)
DuplicateResolver: AI merge decision → Gemini embeddings (cosine) → lexical fallback
        ▼  Create / Merge into existing note / Drop duplicate (mention count +1)
Room ─► WhatsAppDispatcher ─► one-tap notification  or  Cloud API queue (WorkManager, retries)
```

| Module | Contents |
|---|---|
| `core/` (pure Kotlin, unit‑tested) | `LanguageDetector`, `ConversationBuffer`, `IdeaJsonParser`, `IdeaPrompts`, `DuplicateResolver`, `TextSimilarity`, `WhatsAppFormatter` |
| `app/ai` | `GeminiClient` (transcribe / analyze / embed), `IdeaPipeline` |
| `app/speech` | `ChunkedAudioEngine`, `AndroidSpeechEngine`, `PendingAudioStore` |
| `app/service` | `IdeaListeningService`, notifications, listening state |
| `app/whatsapp` | `WhatsAppDispatcher`, `CloudApiClient`, `WhatsAppHandoffActivity` |
| `app/work` | `PendingProcessingWorker`, `CloudSendWorker` |
| `app/data` | Room entities/DAOs, `SettingsRepository`, `SecretStore` |
| `app/ui` | Home, Ideas (search/copy/share/send/edit/delete), Settings, Conversations/Transcript |

## What is automatic vs. limited

| Feature | Status |
|---|---|
| Listening, only after *Start Idea Mode* + consent, with persistent notification | ✅ Automatic until you press Stop |
| Gujarati / Hindi / English / Hinglish / Gujarati‑English transcription | ✅ Automatic (Gemini audio engine is best for code‑mixing; Android engine is one primary language per session, auto‑switching only on Android 14+ where the recognizer supports it) |
| Ignoring random talk, detecting ideas/tasks/decisions, short notes | ✅ Automatic (Gemini + confidence threshold) |
| Merging related points into one concept, duplicate suppression | ✅ Automatic |
| Saving locally, offline queue, retry after network returns / app restart | ✅ Automatic |
| Preparing the WhatsApp message | ✅ Automatic |
| **Sending through your personal WhatsApp** | ⚠️ **One tap.** WhatsApp has no public API for personal accounts; Android blocks apps from opening other apps from the background. OBLE posts a notification — tap *Send to WhatsApp* → the chat opens with the note typed → press Send. *Send all* batches every waiting note into one message. OBLE cannot confirm you pressed Send, so the status becomes “Opened in WhatsApp” (you can mark it Sent). |
| **Fully automatic WhatsApp sending** | ✅ Only with Meta's official **WhatsApp Cloud API** (Settings → Delivery → Automatic): needs a WhatsApp Business number, a permanent token and an approved template with one body variable (e.g. `oble_note`: “New note from OBLE: {{1}}”). Plain text works only inside the 24‑hour service window. Failures fall back to the one‑tap notification. |
| Accessibility‑service automation of WhatsApp | ❌ Deliberately not used (policy/ToS violation, covert control). |
| Restarting listening after the app is killed | ❌ Deliberately not done (`START_NOT_STICKY`) — no silent recording. Captured speech is still processed. |
| Hiding the recording indicator | ❌ Impossible and not attempted. |

## Privacy
* Microphone is used only by the foreground service while Idea Mode is ON; Pause and Stop release it immediately.
* No raw audio is kept: clips live in private cache, silent clips are never uploaded, speech clips are
  deleted right after transcription. Offline clips are held privately until transcribed (max 24 h).
* Transcripts can be kept or auto‑deleted after analysis (Settings → Privacy); conversations can be
  deleted individually; *Clear all local data* wipes ideas, conversations, queued audio, settings and keys.
* Backups/device transfer are disabled for app data.
* With the Gemini engine, speech audio and transcripts are sent to Google's Gemini API — the consent
  dialog says so.

## Reliability handled
Internet loss (segments/clips queued, WorkManager retry), AI failures (rollback + backoff),
speech‑recognizer errors (restart with backoff, language fallback), microphone taken by a call or
another app (retry + status message), WhatsApp missing (share sheet fallback), permission denied
(guidance + settings link), app restart (dangling sessions closed, pending work resumed),
duplicates (semantic + lexical).
