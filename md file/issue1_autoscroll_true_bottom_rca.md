# RCA & Solution Design: Auto-Scroll "True Bottom" Reachability

## 1. Issue Definition
The auto-scroll feature fails to reach the absolute visual end of the PDF document. 

**Distinctions in "End States":**
*   **Last Page Visible**: The final page (e.g., Page 20) has entered the viewport at the bottom.
*   **Last Page Partially Visible**: Page 20 is being displayed, but its lower portion (or the white margin at the end) is still "below the fold."
*   **Last Page Fully Visible**: The entire rectangle of the final page is within the viewport. (Often impossible on tablets if the page is taller than the screen).
*   **Visual Bottom Reached**: The bottom edge of the final page (including document-end margins) is aligned with or has passed above the bottom edge of the viewport. **This is the required success criterion.**

**Current Symptom:**
The scroller terminates when the view *thinks* it is done or when a naive index-based check is met, often leaving the bottom 10-20% of the last page (or even entire later pages) un-scrolled.

## 2. Current Implementation
*   **Scroll Target**: `androidx.pdf.view.PdfView` (specifically its internal `RecyclerView` discovered via reflection).
*   **Scrolling Method**: `rvInternal.scrollBy(0, delta)`.
*   **Stop Condition (Current)**: 
    ```kotlin
    val canScrollMore = rvInternal.canScrollVertically(1)
    if (!canScrollMore) {
        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
        val trulyAtEnd = lastVisible >= pageCount - 1
        if (trulyAtEnd) stopAutoScroll()
    }
    ```
*   **Metrics**: Relies on `RecyclerView` range/offset/extent and `LayoutManager` positions.

## 3. Verified Evidence
*   **Screenshot Truth**: Screenshots show the page indicator at `5/6` or `6/6`, yet musical notation is still clearly visible extending off the bottom of the screen.
*   **Log Truth (from `RCA_for_scroll.md`)**: `offset=8474, range=10073, extent=1599, canScroll=false`. Here `offset + extent == range`, so the `RecyclerView` reports it cannot scroll more. However, the `range` (10073) is an underestimate for a multi-page document.
*   **Stop Logic Flaw**: `findLastCompletelyVisibleItemPosition` is used as a proxy for "document end". If the last page is taller than the viewport, it will **never** be "completely visible," causing the logic to fall back to the previous page or trigger prematurely on a "best-fit" guess.

## 4. Why the Current Logic Fails
1.  **Stop at 5/6 (Early Limit)**: The `RecyclerView` reports `canScrollVertically(1) == false` because it hasn't pre-measured the 6th page yet. The `scrollRunnable` sees this, checks `findLastCompletelyVisibleItemPosition()`. If Page 5 is completely visible, and the code incorrectly thinks index 4 is "close enough" or the `pageCount` is temporarily misreported, it stops.
2.  **Stop at 6/6 (Page Boundary vs Document End)**: Even if it reaches Page 6, the `RecyclerView` may consider its job "done" as soon as Page 6 is anchored at the top, or when its *estimated* range is hit. It does not wait for the visual bottom of Page 6 to reach the viewport bottom.
3.  **Estimated Range Problem**: `getTrueVerticalScrollRange` currently only scales the **speed**. It does not override the `RecyclerView`'s internal knowledge of its scrollable bounds. If the view thinks it can only scroll 10,000 pixels, it will stop there regardless of the speed.

## 5. Coordinate Model for the Correct Solution
To achieve a "True Bottom" stop, we must ignore the `RecyclerView`'s metadata and look at the **literal pixels** of the rendered pages.

*   **Source of Truth**: `currentPageLocations: SparseArray<RectF>`. 
    *   This is populated by `onViewportChanged` in `androidx.pdf.view.PdfView`.
    *   The `RectF` values are in the coordinate space of the `PdfView` viewport (pixels).
*   **The Math**:
    *   `viewportBottom = pdfScrollableView.height` (typically ~1600-2000px).
    *   `lastPageBounds = currentPageLocations.get(pageCount - 1)`.
    *   If `lastPageBounds == null`, we definitely haven't reached the end.
    *   If `lastPageBounds.bottom > viewportBottom`, the document end is still below the screen.
    *   If `lastPageBounds.bottom <= viewportBottom`, the visual bottom of the document has been reached.

**Zoom Independence**: Because `onViewportChanged` provides bounds in current viewport pixels (already scaled by zoom), this logic works perfectly whether zoomed in or out.

## 6. Candidate Fix Strategies

| Strategy | Truth Source | Pros | Cons |
| :--- | :--- | :--- | :--- |
| **A. Coordinate-Based (Recommended)** | `lastPageBounds.bottom` | 100% robust; zoom-independent; matches user visual. | Requires reliable `pageCount` and `RectF` lookup. |
| **B. Force Scroll Fallback** | `scrollBy` regardless of `canScroll` | May bypass "soft" limits in `RecyclerView`. | Risk of infinite loop; "fighting" the view; jittery at end. |
| **C. Offset vs True Range** | `getTrueVerticalScrollRange` | Mathematical simplicity. | Still relies on the view accepting the `scrollBy` command. |

## 7. Best Robust Solution
**Coordinate-Based Bottom Detection** is the only foolproof method.

**Refined Logic:**
1.  In `scrollRunnable`, calculate `deltaToScroll`.
2.  Attempt `rvInternal.scrollBy(0, deltaToScroll)`.
3.  **Stop Check**:
    *   Get `lastPageIndex = getPdfPageCount(rv) - 1`.
    *   Lookup `lastPageBounds = currentPageLocations.get(lastPageIndex)`.
    *   `val isVisualBottomReached = lastPageBounds != null && lastPageBounds.bottom <= rvInternal.height`.
    *   If `isVisualBottomReached`, then `stopAutoScroll()`.
4.  **Anti-Stuck Safeguard**: If `isVisualBottomReached` is false BUT `offset` hasn't changed for 10 consecutive ticks despite `delta > 0`, it may be a hard limit (end of file). Stop to prevent battery drain.

## 8. Minimal Implementation Plan
1.  **`PdfViewerFragment.kt`**:
    *   In `startAutoScroll()`, ensure `pageCount` is accurately fetched once at start and monitored.
    *   In `scrollRunnable.run()`:
        *   Remove `canScrollVertically(1)` check as the primary terminator.
        *   Remove `findLastCompletelyVisibleItemPosition` check.
        *   Add `lastPageBounds.bottom` check.
    *   Add high-fidelity logs: `EndCheck: lastPage=${lastPageBounds?.bottom}, viewport=${rv.height}, reached=${isVisualBottomReached}`.

## 9. Verification Plan
1.  **Landscape Test**: Open a 6-page score. Auto-scroll. Verify it doesn't stop until the white margin below the last line of Page 6 is at the bottom of the screen.
2.  **Zoomed-In Test**: Zoom in 3x on Page 6. Verify it continues scrolling until the actual bottom of the content is reached.
3.  **Fast-Duration Test**: Set duration to 15s. Verify it doesn't "slam" into the stop or cut off early.
4.  **6/6 Validation**: Ensure that even if the indicator says `6/6`, the scroll only stops when the pixels match the viewport bottom.

## 10. Remaining Unknowns
*   **Last Page Discovery**: If the `RecyclerView` is extremely aggressive with recycling, `currentPageLocations` might not have the last page until we are very close. **Mitigation**: The code should not stop if the last page is missing from the locations array.

## 11. Final Recommendation
Replace the `RecyclerView` internal state checks (`canScrollVertically`, `lastVisiblePosition`) with a **Coordinate-Based Visual End Check** using the last page's `RectF.bottom`. This is the only way to satisfy the "True Bottom" requirement across all zoom levels and PDF dimensions.

**Next Step**: Implement the coordinate-based `trulyAtEnd` check in `PdfViewerFragment.kt`.
