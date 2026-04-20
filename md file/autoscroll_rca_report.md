# Root Cause Analysis: Auto-Scroll Early-Stop Bug

## 1. Verified Symptom
- **Description**: The auto-scroll feature terminates prematurely, often failing to reach the final pages of the PDF.
- **Zoom Dependence**: The bug is highly sensitive to the zoom level. When "zoomed out" (Fit to Width), the scroll typically stops around page 5 or 6. When "highly zoomed in", the scroll progresses much further or reaches the actual end.
- **Significance**: This indicates that the "end-of-document" detection logic is relying on a measurement (Range) that is volatile, estimated, or scale-dependent, rather than a fixed document boundary.

## 2. Actual Current Auto-Scroll Architecture
- **Trigger**: `PdfViewerFragment.startAutoScroll()`
- **Execution**: A `scrollRunnable` (anonymous `Runnable`) is posted to `scrollHandler` (main thread) every 16ms (~60 FPS).
- **Target View**: `pdfScrollableView`. 
- **View Identification**:
  - In `setupPdfFragment()`, the library's internal `androidx.pdf.view.PdfView` is extracted via reflection from the `_pdfView` field of `AndroidXPdfFragment`.
  - This `PdfView` instance is passed to `setupPageIndicator()` and assigned to `pdfScrollableView`.
- **Internal Hierarchy**: `PdfView` (FrameLayout) -> `ZoomView` (Custom Layout) -> `PdfRecyclerView` (RecyclerView).
- **Control**: The code calls `rvInternal.scrollBy(0, delta.toInt())` directly on the `PdfView` instance.

## 3. Exact Current Stop Condition
The termination logic is located in the `run()` method of the `scrollRunnable` inside `startAutoScroll()`:

```kotlin
val currentOffset = getVerticalScrollOffset(rvInternal)
val currentRange = getVerticalScrollRange(rvInternal)
val currentExtent = rvInternal.height

if (currentOffset + delta >= currentRange - currentExtent) {
    // ... logic to perform final small scroll and stop
    stopAutoScroll()
}
```

### Analysis of Measurement Methods:
- **`getVerticalScrollRange`**: Uses reflection to call the protected `View.computeVerticalScrollRange()`. For `PdfView`, this likely returns the range of the internal `ZoomView`.
- **`getVerticalScrollOffset`**: Uses reflection to call `View.computeVerticalScrollOffset()`.
- **The Flaw**: `computeVerticalScrollRange()` on a `RecyclerView` (which `PdfView` uses internally) is often an **estimation** based on currently bound ViewHolders. If the document has 20 pages but only 6 are currently "realized" or measured by the layout manager, the range will be reported as much smaller than the actual document height.

## 4. Runtime Measurement Model
- **Initial Speed Calculation**:
  - `range` is measured **once** at `startAutoScroll()`.
  - `maxScroll = range - extent`.
  - `speed = maxScroll / totalDurationMs`.
- **Dynamic Termination**:
  - `currentRange` is re-measured **every tick** (16ms).
- **The Mismatch**: 
  - If the initial `range` was based on an estimate of 6 pages, the `speed` is too slow for a 20-page document.
  - However, if `currentRange` *stays* at that 6-page estimate (because subsequent pages haven't been scrolled into view yet), the `offset` will hit that target quickly, causing the "Early Stop" log.

## 5. Root Cause Analysis
The root cause is a **Measurement Coordinate and Lifecycle Mismatch** between the wrapper `PdfView` and its internal `RecyclerView`.

1. **Estimated Range (Primary)**: `androidx.pdf.view.PdfView` delegates scroll metrics to an internal `RecyclerView`. `RecyclerView` uses `LinearLayoutManager.computeVerticalScrollRange()`, which estimates total height as `(average_item_height) * (total_items)`. At low zoom (zoomed out), if the first few pages are small or the library hasn't fully calculated the dimensions of all 20 pages, the "total_items" or "average_height" used for the range is truncated.
2. **Zoom-Scale Disparity**: When "zoomed out", the content scale is low. The physical scrollable distance (`range - extent`) is compressed. If the `PdfView` reports `range` in "content pixels" (unscaled) but `offset` is tracked in "view pixels" (scaled), or if the `ZoomView` transformation is not consistently applied to both metrics, the termination condition is met mathematically long before the visual end.
3. **Reflection Fallback**: If the reflection call to `computeVerticalScrollRange` fails on the `PdfView` wrapper, the code falls back to `view.getChildAt(0).height`. In `PdfView`, child 0 is the `ZoomView`. If the `ZoomView` is not yet expanded to the full document height (due to lazy page loading), it returns only the height of the current viewport or the loaded pages.

## 6. Robust Fix Options

### Option 1: Page-Count Based Range (Recommended)
Instead of trusting `computeVerticalScrollRange`, determine the absolute document height using the library's `PdfDocument` metadata (via reflection) or the `currentPageLocations` `SparseArray`. 
- **Pros**: Zoom-independent; accurate to the pixel; ignores `RecyclerView` estimation errors.
- **Cons**: Requires stable reflection to get total page count and page dimensions.

### Option 2: Internal RecyclerView Targeting
Re-implement the recursive view scanner to find the actual `PdfRecyclerView` inside the `PdfView` hierarchy.
- **Pros**: `RecyclerView` metrics are more "honest" about what is actually scrollable.
- **Cons**: Still subject to `RecyclerView` estimation inaccuracies at the start.

### Option 3: Dynamic Speed Correction
Re-calculate `scrollSpeedPxPerMs` every time `currentRange` changes.
- **Pros**: Self-correcting as more pages load.
- **Cons**: Resulting speed will be inconsistent (starts slow, speeds up), which is bad for musicians.

## 7. Minimal Code Change Plan
1. **Restore `findBestScrollableRecyclerView`**: Update it to search the `AndroidXPdfFragment` view hierarchy specifically for the `RecyclerView` (class name contains "PdfRecyclerView").
2. **Improve Metric Helpers**: Update `getVerticalScrollRange` and `getVerticalScrollOffset` to prioritize the found `RecyclerView`.
3. **Pre-Calculate Absolute Height**: In `startAutoScroll`, iterate through all pages (using `PdfView.getPageDimensions` or `currentPageLocations`) to sum the "True Range" of the document. This ensures the speed is correct regardless of lazy loading.
4. **Coordinate Normalization**: Ensure `delta` and `currentOffset` are in the same scale as `currentRange`.

## 8. Verification Plan
1. **Zoomed-Out Test**: Open a long (15+ page) PDF. Zoom out fully. Start auto-scroll. Ensure it reaches page 15.
2. **Zoomed-In Test**: Zoom in 3x. Start auto-scroll. Ensure it doesn't stop halfway.
3. **Duration Accuracy**: Time the scroll from start to finish. For a 60-second setting, the document should reach the bottom at ~60s (+/- 1s).
