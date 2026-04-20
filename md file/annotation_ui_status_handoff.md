# Annotation UI Status Handoff

## 1. Current Project State

*   **Annotation Features**:
    *   In-app stylus-based annotation using `InkCanvasView` is fully functional.
    *   Tools: Pen (opaque), Highlighter (semi-transparent), Eraser (Stroke/Area modes), Undo, Redo, and "Clear All".
    *   Mode switching (Reader vs. Annotation) is stable via the floating action button.
*   **Toolbar Features**:
    *   Custom top-docked toolbar for annotation tools with semi-transparent background.
    *   Responsive to window insets (status bar / display cutout).
    *   Auto-scroll controls (play/pause, timer adjustment) remain functional and stable in the bottom container.
*   **No-Draw Zone Behavior**:
    *   `InkCanvasView` successfully receives a list of "No-Draw" regions via `setNoDrawRegions(List<Rect>)`.
    *   The toolbar and bottom controls are correctly identified as exclusion zones where ink strokes should be clipped.
*   **Stability**:
    *   **Crash-Free**: Resolved a `NullPointerException` related to `OnGlobalLayoutListener` accessing view binding after fragment destruction.
    *   Lifecycle-safe listener management and nullable binding checks are implemented.

## 2. Current Unresolved Issues

*   **Toolbar Placement**: The annotation toolbar is currently positioned slightly too high, occasionally overlapping system content or feeling "cramped" against the status bar area despite inset handling.
*   **Visual Transparency**: While the PDF text is visible behind the toolbar (desired), the background alpha is currently set to `#99` (~60%), which is a work-in-progress value for finding the perfect "Samsung Notes" translucent feel.
*   **Stroke Clipping Bug**: **CRITICAL** - Despite the `no-draw` regions being correctly calculated and passed to the view, custom blue annotation strokes are still being rendered *on top* of the toolbar background instead of being clipped/hidden beneath it.
*   **Visibility**: The toolbar and PDF content behind it are visible, but the strokes fail to respect the exclusion boundary.

## 3. Current Architecture

*   **Hosting**: `PdfViewerFragment` hosts the AndroidX `PdfViewerFragment` in a `FragmentContainerView`.
*   **Overlay**: `InkCanvasView` is a sibling to the PDF container, layered on top.
*   **Projection**: Uses a `PageDelegate` with reflection-based access to the internal `PdfView` to project screen coordinates to normalized page coordinates, ensuring strokes stay pinned to the PDF content during scroll/zoom.
*   **No-Draw Computation**: `PdfViewerFragment` uses `OnGlobalLayoutListener` to compute the global bounds of the toolbar and controls, converts them to local coordinates of the `InkCanvasView`, and passes them down.
*   **Clipping Logic**: `InkCanvasView` is intended to use `Canvas.clipOutRect` (or similar) during its `onDraw` pass to exclude the provided `noDrawRegions`.

## 4. Recent Attempted Fixes

*   **Toolbar Spacing**:
    *   *Intent*: Increase/Decrease gap between toolbar and top of screen.
    *   *Result*: Successfully reduced gap from 28dp to 16dp. Insets are respected, but vertical centering/centering feels slightly off.
*   **Toolbar Clipping**:
    *   *Intent*: Hide strokes under the toolbar.
    *   *Result*: **FAILED**. The strokes are still visible over the semi-transparent toolbar. The coordinate conversion or the `onDraw` clipping implementation requires investigation.
*   **Constraint Changes**:
    *   *Intent*: Fix "invisible toolbar" by correcting conflicting constraints.
    *   *Result*: **SUCCESSFUL**. Toolbar is now anchored correctly to the top of the container.
*   **Lifecycle/Binding Crash Fix**:
    *   *Intent*: Stop NPE when layout listeners trigger after `onDestroyView`.
    *   *Result*: **SUCCESSFUL**. Implemented `_binding` safety and explicit listener removal.

## 5. Desired Final Behavior

*   **Placement**: Toolbar should sit at a "natural" height (not too high, not too low) below the status bar/cutout.
*   **Translucency**: PDF content should be faintly visible through the toolbar, giving a modern overlay feel.
*   **Perfect Clipping**: Ink strokes must be strictly clipped at the toolbar's edge. They should appear to pass *under* the toolbar, or simply not exist in that rectangular band.
*   **Interaction**: Stylus taps on the toolbar must change tools and NOT trigger an ink point at the same location.
*   **Stability**: Zero crashes during rotation, backgrounding, or exiting the viewer.

## 6. Files Likely Involved

*   `app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/PdfViewerFragment.kt` (Logic & Coordination)
*   `app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkCanvasView.kt` (Drawing & Clipping logic)
*   `app/src/main/res/layout/fragment_pdf_viewer.xml` (UI Structure & Constraints)

## 7. Strict Next-Step Guidance

*   **FOCUS ONLY** on `InkCanvasView.onDraw` to fix the stroke clipping issue.
*   **REFINE** the toolbar top-margin/padding in `PdfViewerFragment`'s inset listener.
*   **DO NOT** modify fragment lifecycle, backstack, or PDF rendering logic.
*   **DO NOT** attempt a large refactor of the coordinate system.
