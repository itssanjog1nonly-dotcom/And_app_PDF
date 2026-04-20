# PdfScrollReader — App Context

## 1. App Overview
- **App Name**: PdfScrollReader
- **Package Name**: `com.sanjog.pdfscrollreader`
- **Minimum SDK**: 31 (Android 12)
- **Target SDK**: 35
- **Purpose**: A musician-focused PDF reader optimized for live performance, featuring auto-scroll, setlist management, and stylus-driven annotations.
- **Core Flows**: 
  1. **Launch**: Starts at `SplashActivity` then `MainActivity` showing `FilePickerFragment`.
  2. **Open PDF**: User selects a PDF from local storage or "Recently Opened" list.
  3. **Read**: View PDF with auto-scroll support (calibrated by duration).
  4. **Annotate**: On stylus-supported tablets, users can draw ink, shapes, and highlight.
  5. **Save**: Annotations are saved as sidecar JSON files and can be exported (flattened) back into the PDF.

## 2. Project Structure

### Kotlin Source Files (`app/src/main/java/com/sanjog/pdfscrollreader/`)

#### **UI (Activities & Fragments)**
- [MainActivity.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/MainActivity.kt): Main entry point handling navigation between fragments.
- [SplashActivity.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/SplashActivity.kt): Splash screen shown on launch.
- [PdfViewerFragment.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/PdfViewerFragment.kt): Main PDF viewing and annotation logic (~380 lines).
- [AppEditablePdfViewerFragment.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AppEditablePdfViewerFragment.kt): Custom subclass of AndroidX `EditablePdfViewerFragment` exposing internal `PdfView`.
- [AnnotationSyncManager.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AnnotationSyncManager.kt): Bridges the UI annotation models and data persistence models; handles injection into the native PDF editor.
- [FilePickerFragment.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/FilePickerFragment.kt): Home screen for choosing files and viewing recently opened documents.
- [SetlistFragment.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/SetlistFragment.kt): Management of song setlists.
- [SetlistDetailFragment.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/SetlistDetailFragment.kt): View and edit contents of a specific setlist.

#### **Custom Views & Logic**
- [InkCanvasView.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkCanvasView.kt): Custom overlay view for drawing ink and shapes (~315 lines).
- [InkCanvasModels.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkCanvasModels.kt): Definitive UI-layer definitions for `Stroke`, `ShapeAnnotation`, and coordinate delegates.
- [InkTouchHandler.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkTouchHandler.kt): Handles touch events for drawing and selection logic.
- [InkRenderer.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkRenderer.kt): Responsible for drawing strokes and shapes on the `InkCanvasView`.
- [InkSelectionHandler.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkSelectionHandler.kt): Manages selection, moving, and deleting of annotations.
- [InkSelectionTransform.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkSelectionTransform.kt): Applies move/resize transforms to selected annotations.

#### **Data Models**
- [AnnotationData.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/data/model/AnnotationData.kt): Persistence model for annotations (JSON sidecars).
- [Stroke.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/data/model/Stroke.kt): Data-layer representation of an ink stroke.
- [ShapeAnnotation.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/data/model/ShapeAnnotation.kt): Data-layer representation of a shape.

#### **Repositories & Utils**
- [AnnotationRepository.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/data/repository/AnnotationRepository.kt): CRUD operations for sidecar annotation files.
- [RecentlyOpenedRepository.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/data/repository/RecentlyOpenedRepository.kt): Manages the list of recently opened PDFs.
- [AutoScrollManager.kt](file:///d:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AutoScrollManager.kt): Encapsulates auto-scroll logic, timing, and UI updates.

### XML Layouts (`app/src/main/res/layout/`)
- `fragment_pdf_viewer.xml`: Overlay layout containing `PdfView` (in container), `InkCanvasView`, and the annotation toolbar.
- `fragment_file_picker.xml`: Home screen layout.

## 3. Architecture & Refactoring
- **Model Separation**: The project strictly separates UI-layer models (in `ui.view`) from Data-layer models (in `data.model`). `AnnotationSyncManager` acts as the mapper between them.
- **Componentized Logic**: UI logic is broken down from the massive `PdfViewerFragment` into specialized handlers like `AutoScrollManager`, `AnnotationSyncManager`, and `InkTouchHandler`.
- **Coordinate Mapping**: 
  - `InkCanvasView.PageDelegate` interface handles translation between "Screen Coordinates" (pixels) and "Normalized PDF Coordinates" (0.0 to 1.0).
  - Implementation is provided by `PdfViewerFragment` using the AndroidX `PdfView`'s `getPageLocationOnScreen` methods.

## 4. Key Features
- **Stylus Annotations**: `InkCanvasView` handles stylus-only drawing (PEN, HIGHLIGHTER, RECT, ELLIPSE, FREEFORM).
- **Selection System**: Marquee, Lasso, and Tap-to-select support. Users can move, duplicate, or delete selected annotations.
- **Auto-Scroll**: Smooth scrolling with configurable duration and pause-on-touch behavior.
- **Undo/Redo**: Full history support for all annotation actions.
- **Export**: Ability to flatten and export annotations back into a standard PDF file.

## 5. Build & Environment
- **Critical Command**: `.\gradlew.bat clean assembleDebug`
- **Device**: Samsung Galaxy Tab (Landscape orientation, S Pen focus).
- **Tooling**: Reflection is used to access internal AndroidX PDF APIs where public access is restricted.
