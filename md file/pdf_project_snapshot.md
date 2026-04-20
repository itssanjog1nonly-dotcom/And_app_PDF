# Project Snapshot: PdfScrollReader

## 1. Project Overview
PdfScrollReader is a specialized Android application for musicians, designed to facilitate hands-free reading of PDF chord sheets and lyrics. The app's core value proposition is providing a smooth, distraction-free reading experience with precise auto-scrolling tailored to song durations.

**Key Features:**
- **High-Fidelity PDF Rendering:** Leverages `androidx.pdf:pdf-viewer-fragment` for sharp, tiled rendering.
- **Duration-Based Auto-Scroll:** Users set a song duration (e.g., 3:30), and the app calculates the necessary scroll speed to reach the end of the document exactly on time.
- **Stylus & Touch Separation:** Hardware detection of stylus (S Pen) support, intended for future in-app annotations.
- **Bluetooth Pedal Support:** Partial implementation for hands-free page turning via Bluetooth MIDI/HID pedals (e.g., `BluetoothPedalManager`).
- **External Intent Delegation:** Current (temporary) solution for annotations by launching system-level editors.
- **Home Screen Persistence:** `FilePickerFragment` remains the base of the navigation stack for easy file switching.

## 2. Library and Tool Stack
- **`androidx.pdf:pdf-viewer-fragment:1.0.0-alpha17`**: Primary PDF rendering engine.
- **`androidx.activity:activity-ktx:1.9.3`**: Modern Activity APIs.
- **`androidx.fragment:fragment-ktx:1.8.5`**: Fragment management.
- **`com.google.android.material:material:1.12.0`**: Material 3 UI components.
- **`com.google.code.gson:gson:2.11.0`**: JSON handling for settings and potential annotation storage.
- **ViewBinding**: Used throughout the project for safe view interaction.

### Module-level build.gradle.kts (Dependencies)
```kotlin
dependencies {
    implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-alpha17")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.0.0-alpha03")
}
```

## 3. File Structure (High-Level)
```text
app/src/main/java/com/sanjog/pdfscrollreader/
├── MainActivity.kt                # Main host; manages Fragment backstack
├── data/
│   ├── bluetooth/
│   │   └── BluetoothPedalManager.kt# Key event handling for pedals
│   └── repository/
│       └── DisplayModeRepository.kt# Persistent settings (pedal codes, etc.)
├── ui/
│   ├── fragment/
│   │   ├── FilePickerFragment.kt  # Home screen; list of local PDF files
│   │   └── PdfViewerFragment.kt   # Wrapper for the PDF viewer + scroll controls
│   └── util/
│       ├── InputCapabilityUtils.kt# Stylus detection and event filtering
│       └── AnimationUtils.kt      # UI transition helpers
└── res/
    ├── layout/
    │   ├── activity_main.xml       # Contains Toolbar and fragment_container
    │   ├── fragment_file_picker.xml# List of PDF files
    │   └── fragment_pdf_viewer.xml # PDF container + auto-scroll UI
```

## 4. PDF Screen Architecture
The PDF experience is managed by a nested fragment architecture.

- **Main Screen:** `PdfViewerFragment` (local class).
- **Layout:** `fragment_pdf_viewer.xml` defines the UI, including a `FrameLayout` with ID `pdf_viewer_container`.
- **Hosting logic:** `PdfViewerFragment` creates an instance of `androidx.pdf.viewer.fragment.PdfViewerFragment` (aliased as `AndroidXPdfFragment`) and injects it into `pdf_viewer_container` using a `childFragmentManager` transaction.
- **Creation:** Triggered in `MainActivity.showPdf(uri)` which replaces the `R.id.fragment_container` and adds the transaction to the backstack with the tag `"pdf_viewer"`.

## 5. Fragment & Intent Usage Details
- **`PdfViewerFragment` (local)**:
  - Manages `AndroidXPdfFragment` lifecycle.
  - Implements `launchExternalEditor()` which uses `android.intent.action.ANNOTATE` and `Intent.ACTION_EDIT`.
- **`AndroidXPdfFragment` (library)**:
  - Used for rendering via `fragment.documentUri = it`.
  - Properties like `isTextSearchActive` are used for search functionality.
- **Intent Actions**:
  - `android.intent.action.ANNOTATE`: Primary intent for external markup.
  - `Intent.ACTION_EDIT`: Fallback intent for PDF modification.
- **Suppression Logic**:
  - `hideInternalEditButtons(view)`: A recursive function that scans the `AndroidXPdfFragment` view hierarchy to find and hide internal Material FABs with "Edit" content descriptions.

## 6. Current Annotation Behavior
Currently, the app relies on **External Intent Delegation**, which is a temporary state.

- **Non-stylus device:** The "Annotate" button is hidden.
- **Stylus device:** A dark-grey "Edit" button (pen icon) appears in the bottom control bar.
- **Tapping "Annotate":**
  1. Stops auto-scroll.
  2. Launches `android.intent.action.ANNOTATE` with `FLAG_GRANT_WRITE_URI_PERMISSION`.
  3. If no app handles it, falls back to `ACTION_EDIT`.
- **System Icons:** Any built-in "Edit file" FABs from the `androidx.pdf` library are hidden programmatically on view creation to avoid UI duplication.

## 7. Stylus vs Finger Handling
- **Detection:** `InputCapabilityUtils.isStylusSupported(context)` determines if the "Annotate" button should be shown.
- **Filtering:** `PdfViewerFragment.handleTouchInteraction(event)` uses `InputCapabilityUtils.isStylusEvent(event)` to detect stylus contact.
- **State:** Currently, there is no internal "Annotation Mode" active; the stylus event detection is present in `handleTouchInteraction` but primarily serves as a placeholder for the planned `InkCanvasView` overlay.

## 8. Auto-scroll and Interaction
- **Logic:** `startAutoScroll()` calculates speed as `(VerticalScrollRange - Height) / durationSeconds`.
- **Execution:** A `Handler(Looper.getMainLooper())` runs a `Runnable` every 16ms (approx 60fps), calling `scrollBy(0, delta)` on the internal scrollable view.
- **Discovery:** The app recursively searches the `AndroidXPdfFragment` view hierarchy for a `RecyclerView` or a view of class `PdfView` that reports a vertical scroll range.
- **Interruption:** Auto-scroll stops if the user touches the screen (detected via `isUserTouching` flag) or if the "Annotate" button is pressed.

## 9. Save / Persistence Behavior
- **Internal:** No internal saving of PDF data. Reflection audits confirmed that `saveDocument()` and `isDocumentModified` in `alpha17` are non-functional or stubs.
- **External:** Saving is entirely handled by the external application launched via Intent. Changes are saved directly back to the URI if the external editor supports it and has write permissions.

## 10. Gemini Changes So Far
- **Migration to AndroidX PDF:** Replaced old `android-pdf-viewer` implementation with `androidx.pdf:pdf-viewer-fragment:1.0.0-alpha17`.
- **UI Clean-up:** Implemented logic to hide the library's internal FABs.
- **External Intent Flow:** (Latest Change) Implemented delegation to external editors via `ANNOTATE`/`EDIT` intents.
- **Capability Audit:** Performed recursive reflection on `PdfViewerFragment` to confirm that `_toolboxView` is null and native editing is currently unavailable in the `alpha17` build.

**Current Intent Implementation:**
```kotlin
private fun launchExternalEditor() {
    val uri = pdfUri ?: return
    try {
        val intent = Intent("android.intent.action.ANNOTATE").apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivity(intent)
    } catch (e: Exception) {
        // Fallback to ACTION_EDIT...
    }
}
```

## 11. Open Questions / Uncertainties
- **Violation of AGENTS.md Rule #8:** The current implementation uses external intents, which violates the critical project rule: *"NEVER re-introduce any external Acrobat / Drive / system markup flow."* This was done because the library's internal tools are non-functional, but the planned fix is a **Custom Overlay Ink Layer** (Phase 3.1).
- **Coordinate Mapping:** Mapping screen coordinates from a custom overlay view to PDF page coordinates will be the primary challenge for the next phase.
- **Scrollable View Stability:** The method of finding the internal `RecyclerView` via reflection/recursion is effective but may be fragile if the library's internal structure changes in future alpha updates.
