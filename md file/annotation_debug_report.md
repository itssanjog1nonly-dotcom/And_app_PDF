# Annotation Debug Report

This report documents the current state of the PDF annotation system in the PdfScrollReader project.

---

## 1. Runtime Fragment Class
- **Exact class name**: `com.sanjog.pdfscrollreader.ui.fragment.AppEditablePdfViewerFragment`
- **Instantiation location**: `app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/PdfViewerFragment.kt` at line 1376.
- **Creation method**: Instantiated via Fragment constructor and added to the UI via `childFragmentManager` transaction.
- **Instantiation code snippet**:
  ```kotlin
  // Line 1376
  val fragment = AppEditablePdfViewerFragment()
  androidXPdfFragment = fragment
  
  // Lines 1482-1492
  childFragmentManager.beginTransaction()
      .replace(R.id.pdf_viewer_container, fragment, "pdf_viewer_fragment_tag")
      .runOnCommit {
          pdfUri?.let {
              fragment.documentUri = it
              restoreLastPosition()
          }
      }
      .commit()
  ```

## 2. Save Method Audit
- **Method called**: `applyDraftEdits()`
- **Type**: Direct API call.
- **Class being called ON**: `AppEditablePdfViewerFragment` (via `androidXPdfFragment` property).
- **Trigger locations**:
  - `PdfViewerFragment.kt:149` (triggered during the export flow inside `performExport`).
  - *Note: Manual saves also occur via `saveAnnotations()` to the sidecar JSON repository on every stroke change.*

## 3. Export Flow Audit
- **Export file path**: The `destUri` provided by the system's `CreateDocument` ActivityResultLauncher.
- **Order of operations**: `injectAnnotationsToNative()` is called BEFORE `applyDraftEdits()`, and the file write occurs in the success callback.
- **Code order snippet**:
  ```kotlin
  // PdfViewerFragment.kt:143
  lifecycleScope.launch {
      // BUG 4 Fix: Inject our annotations into the native layer before saving
      injectAnnotationsToNative()
      
      // Store pending export URI and trigger native save
      pendingExportUri = destUri
      androidXPdfFragment?.applyDraftEdits()
  }
  
  // Callback (Line 155):
  private fun handleApplyEditsSuccess(handle: androidx.pdf.PdfWriteHandle) {
      // ...
      requireContext().contentResolver.openFileDescriptor(exportUri, "w")?.use { pfd ->
          handle.writeTo(pfd)
      }
  }
  ```
- **Source file path**: `pdfUri` (retrieved from fragment arguments).

## 4. Annotation Storage Audit
- **Custom JSON system active**: Yes.
- **Files involved**: 
  - `app/src/main/java/com/sanjog/pdfscrollreader/data/repository/AnnotationRepository.kt`
  - `app/src/main/java/com/sanjog/pdfscrollreader/ui/view/InkCanvasView.kt`
- **Usage**: Used for storing freehand ink strokes and shape annotations (vector data) for real-time editing and session persistence.
- **Unique path?**: No. The system uses a hybrid approach: sidecar JSON for editing state and the AndroidX PDF native layer for flattening/persisting during export.

## 5. isEditFabVisible Audit
- **Where set**: `PdfViewerFragment.kt` at line 1100.
- **Type**: Reflection.
- **Instance**: `AppEditablePdfViewerFragment` (local `fragment` instance in `setAnnotationUiState`).
- **Code snippet**:
  ```kotlin
  methods.find { it.name == "setEditFabVisible" || it.name == "setIsEditFabVisible" }?.let {
      it.isAccessible = true
      it.invoke(fragment, false)
      Log.e("FRAGMENT_AUDIT", "Invoked ${it.name}(false)")
  }
  ```

## 6. isToolboxVisible Audit
- **Where set**: `PdfViewerFragment.kt` at line 1094.
- **Type**: Reflection.
- **Class**: `AppEditablePdfViewerFragment`.
- **Code snippet**:
  ```kotlin
  methods.find { it.name == "setToolboxVisible" || it.name == "setIsToolboxVisible" }?.let {
      it.isAccessible = true
      it.invoke(fragment, visible)
      Log.e("FRAGMENT_AUDIT", "Invoked ${it.name}($visible)")
  }
  ```

## 7. Full list of all references to PdfViewerFragment
- `app/src/main/java/com/sanjog/pdfscrollreader/ui/MainActivity.kt`: Lines 17, 103, 117 (Imports and instantiation)
- `app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/PdfViewerFragment.kt`: Multiple lines (Class definition, companion newInstance, internal logs)
- `app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AppEditablePdfViewerFragment.kt`: The file exists in the same package but refers to the base `EditablePdfViewerFragment`.

## 8. Full list of all references to EditablePdfViewerFragment
- `app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/PdfViewerFragment.kt`:
  - Line 56: `import androidx.pdf.ink.EditablePdfViewerFragment as AndroidXPdfFragment`
  - Line 1397: String literal `"androidx.pdf.ink.EditablePdfViewerFragment"`
  - Line 1398: String literal `"androidx.pdf.viewer.fragment.EditablePdfViewerFragment"`
  - Line 1399: String literal `"androidx.pdf.EditablePdfViewerFragment"`
- `app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AppEditablePdfViewerFragment.kt`:
  - Line 5: `import androidx.pdf.ink.EditablePdfViewerFragment`
  - Line 11: `class AppEditablePdfViewerFragment : EditablePdfViewerFragment()`

## 9. AndroidX PDF library version
- **Version**: `1.0.0-alpha17`
- **Declarations**: 
  - `implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-alpha17")`
  - `implementation("androidx.pdf:pdf-ink:1.0.0-alpha17")`

## 10. Any known limitations or TODOs in annotation code
- **Strokes Injection**: `// BUG 4 Fix: Inject our annotations into the native layer before saving` (Line 144)
- **Visibility Reflection**: `// Fallback to reflection for now since Direct API failed to compile` (Line 1092)
- **Duplicate Prevention**: `// 3. Clear existing drafts in editor to avoid duplicates during export sync` (Line 211)
- **Shape Mapping**: `// Map shape to a path or simple stamp / For simplicity, we can use a PathPdfObject that draws the shape` (Line 259-260)
- **Internal Button Hiding**: `// Durable enforcement: hide rogue buttons once after layout stabilizes` (Line 1459)
