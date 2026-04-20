# PdfScrollReader — Agent Context

## ⚡ CRITICAL RULES

1. AUTO-APPROVE all file reads, writes, builds — NEVER ask for confirmation.
2. Windows PowerShell environment — NEVER use Linux commands (`ls`, `grep`, etc.).
3. ALWAYS read source files BEFORE modifying.
4. ALWAYS run `.\\gradlew.bat clean assembleDebug` after code changes.
5. NEVER reset `FilePickerFragment` as home, nav back stack, auto-scroll logic.
6. RUNTIME BEHAVIOR is truth — verify on-device before claiming "fixed".
7. NEVER re-introduce external Acrobat/Drive annotate chooser.

---

## 🖥️ Environment

| Property          | Value                                                       |
|-------------------|-------------------------------------------------------------|
| OS                | Windows (PowerShell / CMD)                                  |
| Project path      | `D:\\Users\\itssa\\AndroidStudioProjects\\PdfScrollReader` |
| Build command     | `.\\gradlew.bat clean assembleDebug`                        |
| adb               | `C:\\Users\\itssa\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe` |
| Android SDK       | `C:\\Users\\itssa\\AppData\\Local\\Android\\Sdk`           |
| Min SDK           | 31 (Android 12)                                            |
| Target SDK        | 35                                                          |

### Windows Command Equivalents

| Linux (NEVER use)        | Windows (ALWAYS use)                                      |
|--------------------------|-----------------------------------------------------------|
| `ls`                     | `dir` or `Get-ChildItem`                                  |
| `cat`                    | `Get-Content`                                             |
| `rm`                     | `Remove-Item`                                            |
| `grep` / `grep -r`       | `findstr` or `Select-String` / `Get-ChildItem -Recurse`  |
| `./gradlew`              | `.\\gradlew.bat`                                          |

### Crash Log

```powershell
& "C:\\Users\\itssa\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe" logcat -d | findstr "FATAL"
```

---

## 📱 Device

| Property           | Value              |
|--------------------|--------------------|
| Device             | Samsung Galaxy Tab |
| Android version    | Android 16         |
| Orientation focus  | Landscape          |
| Input              | S Pen + touch      |
| Use case           | Live performance   |

---

## 📱 App Overview

**App name:** `PdfScrollReader`  
**Purpose:** Musician-focused PDF chord sheet reader with auto-scroll for hands-free performance.

---

## 🏗️ Architecture

### Package & Files

```
app/src/main/java/com/sanjog/pdfscrollreader/
├── ui/
│   ├── MainActivity.kt
│   ├── fragment/
│   │   ├── PdfViewerFragment.kt      ← main brain (~1500 lines)
│   │   ├── AppEditablePdfViewerFragment.kt
│   │   ├── FilePickerFragment.kt
│   │   ├── SetlistFragment.kt
│   │   └── SetlistDetailFragment.kt
│   └── view/
│       ├── InkCanvasView.kt          ← custom ink overlay (1559 lines)
│       └── AnnotationOverlayView.kt
├── data/
│   ├── model/ (Stroke, ShapeAnnotation, etc.)
│   └── repository/ (AnnotationRepository, etc.)
└── util/
    ├── Constants.kt
    ├── Extensions.kt
    └── AnnotationSerializer.kt
```

### Navigation Flow

```
App Launch → FilePickerFragment (HOME) → PdfViewerFragment → back → FilePickerFragment
```

**Rules:**
- FilePickerFragment is ALWAYS home screen
- PdfViewerFragment always pushed on top
- Back navigation uses `OnBackPressedDispatcher`

---

## 📦 Dependencies

```kotlin
implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-alpha17")
implementation("androidx.pdf:pdf-ink:1.0.0-alpha17")
implementation("com.google.code.gson:gson:2.11.0")
```

---

## Phase 3 — Stylus Annotations (CURRENT FOCUS)

**User expectation:** Samsung Notes-style annotation on PDF with auto-scroll.

**Requirements:**
- Phones: reader-only, NO annotation UI
- Stylus tablets: annotation button → annotation mode
- Stylus = draw ink, finger = scroll/zoom only
- Auto-scroll disabled while stylus drawing
- NO external app chooser

### Key Implementation

| Component | Location | Notes |
|-----------|----------|-------|
| InkCanvasView | `ui/view/InkCanvasView.kt` | 1559 lines, PageDelegate at line 35 |
| PdfViewerFragment | `ui/fragment/PdfViewerFragment.kt` | ~1500 lines |
| PageDelegate | InkCanvasView line 35-41 | interface for page coordinate mapping |
| Stylus detection | `MotionEvent.TOOL_TYPE_STYLUS` | value = 2 |
| Page index fallback | line 564 | `delegate?.getPageIndexAtPoint(x, y) ?: 0` |
| ViewerMode | PdfViewerFragment | enum READER, ANNOTATION |
| InputCapabilityUtils | `ui/util/InputCapabilityUtils.kt` | Helper for stylus vs finger |

### Saving
- JSON sidecar keyed by PDF URI via `AnnotationRepository`
- On reopen: read strokes and re-draw

---

## Phase 4+ (NOT CURRENT FOCUS)

- Phase 4 — Setlist manager
- Phase 5 — Per-song memory
- Phase 6 — Metronome
- Phase 7 — Transpose overlay
- Phase 8 — Performance mode
- Phase 9 — Bluetooth pedal
- Phase 10 — Export annotated PDFs

---

## 🐛 Known Issues

### Resolved
- StackOverflow on play — fixed via `isAnimating` flag
- Zoom snap-back — solved by removing per-page zoom
- Back button blank screen — fixed via `OnBackPressedDispatcher`

### Current / Open
- AndroidX annotation UX refinement ongoing
- External annotate chooser must never reappear

---

## 📝 Agent Workflow

1. **Before modifying:** read the file first
2. **Before annotation changes:** verify on-device or via logs
3. **After code change:** run `.\\gradlew.bat clean assembleDebug`
4. **If same fix 3+ times:** STOP and write summary

---

## ✅ Runtime Verification (Phase 3)

After annotation changes, MUST verify on Samsung Galaxy Tab:
- [ ] Phone: NO annotation button/overlay
- [ ] Tablet: annotation button appears
- [ ] Stylus draws visible ink
- [ ] Finger scrolls/zooms, does NOT draw
- [ ] Auto-scroll pauses while stylus drawing
- [ ] NO external annotate chooser

If any fail, feature is NOT done.