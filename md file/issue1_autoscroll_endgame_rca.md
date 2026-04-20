# RCA & Redesign: Auto-Scroll "Endgame" Robustness

## 1. Why the Previous Principle Failed
The "Coordinate-Based" approach (`lastPageBounds.bottom <= viewportHeight`) failed because it assumed the **Viewport Reality** and the **Scroll-Host Reality** were perfectly synchronized and continuously updated.

**Failure Modes:**
1.  **Event Starvation**: `currentPageLocations` is updated via `onViewportChanged`. If the `RecyclerView` (scroll host) decides it cannot move further (due to internal range limits), `scrollBy` becomes a no-op. If the viewport doesn't move, `onViewportChanged` is never called. The data becomes stale, and the scroller "waits" forever or triggers a fallback incorrectly.
2.  **Lazy Measurement**: `PdfView` is extremely lazy. It may not even "know" the bounds of Page 6 until Page 5 is nearly finished. If the `RecyclerView` hits an estimated range limit at the end of Page 5, it stops.
3.  **The "5/6" Stop Paradox**: Current logic uses `findLastCompletelyVisibleItemPosition()`. In a `RecyclerView` where items (pages) are tall, "completely visible" is a high bar. If Page 6 is only 10% on screen, `findLastCompletelyVisible` returns Page 5. If the `RecyclerView` hits a hard wall there, the scroller sees `lastVisible = 4` (Page 5) and `pageCount = 6`, concludes it's not at the end, but can't move further.

## 2. The 3 Realities of the Viewer
| Reality | Source | Nature |
| :--- | :--- | :--- |
| **Document Geometry** | PDF File | The "Absolute Truth". N pages, fixed dimensions. |
| **Scroll-Host Movement** | `RecyclerView` | The "Physical Truth". Handles `scrollBy`, reports `offset`. Often uses **estimates** for range. |
| **Viewport State** | `PdfView` | The "Visual Truth". Reports which pages are currently rendered and where they sit. |

**The Problem**: Auto-scroll currently treats "Visual Truth" as the primary driver, but "Physical Truth" (the `RecyclerView`) is the actual bottleneck that stops moving.

## 3. Robust Terminal-State Algorithm: The "Endgame" Design
Instead of a single boolean check, we move to a **Stall-Detection and Confirmation** model.

### Phase 1: Forward Progress Monitoring
The scroller should not care about page indices or coordinates while it is successfully moving.
*   **Scalar**: `currentOffset = rv.computeVerticalScrollOffset()`.
*   **Progress**: If `currentOffset > lastOffset`, we are moving. Reset `stallCounter`.

### Phase 2: Endgame Entry (Stall Detection)
If `deltaToScroll > 0` but `currentOffset <= lastOffset` for **20 consecutive ticks** (~320ms):
*   We have hit a "Physical Wall".
*   The `RecyclerView` is refusing to scroll further.

### Phase 3: Confirmation & Resolution
When a Physical Wall is hit, we evaluate if we are at the **Document End**.
1.  **Check 1 (Index Match)**: `val lastVisible = lm.findLastVisibleItemPosition()`. If `lastVisible >= pageCount - 1`, we are truly at the end. **STOP.**
2.  **Check 2 (Coordinate Proxy)**: If `lastPageBounds.bottom` is within a tolerance (e.g., 5px) of `rv.height`, we are at the end. **STOP.**
3.  **Check 3 (Fake End/Lazy Loading)**: If we are stalled but `lastVisible < pageCount - 1`:
    *   The `RecyclerView` is stuck (likely due to an incorrect estimated range).
    *   **Action**: Issue a "Wake Up" command: `rv.scrollToPosition(lastVisible + 1)`.
    *   Reset `stallCounter` and wait for loading.

## 4. Forward-Progress Scalar Comparison
| Candidate | Pros | Cons |
| :--- | :--- | :--- |
| **Scroll Offset** | Authoritative on movement. | Subject to `RecyclerView` estimation jitter. |
| **Last Page Bottom** | Matches visual reality. | Stalls when movement stalls (circular dependency). |
| **canScrollVertically** | Simple API. | Often lies (returns false when more content exists). |

**Winner**: **Scroll Offset** (as a delta check) combined with **Stall Counter**.

## 5. Recommended Algorithm (Implementation Detail)

```kotlin
// State Variables (Member of Runnable)
private var lastKnownOffset = -1
private var stallCount = 0
private val STALL_LIMIT = 20 // ~320ms at 60fps

// In run():
val currentOffset = getVerticalScrollOffset(rvInternal)

if (currentOffset > lastKnownOffset) {
    lastKnownOffset = currentOffset
    stallCount = 0
} else if (!isUserTouching) {
    stallCount++
}

if (stallCount >= STALL_LIMIT) {
    val pageCount = getPdfPageCount(rvInternal)
    val lm = (rvInternal as? RecyclerView)?.layoutManager as? LinearLayoutManager
    val lastVisible = lm?.findLastVisibleItemPosition() ?: -1
    
    // LOG: "Stall detected at offset $currentOffset, lastVisible=$lastVisible, total=$pageCount"
    
    if (lastVisible >= pageCount - 1 || pageCount <= 0) {
        // True physical and logical end
        stopAutoScroll()
        return
    } else {
        // Stuck at a "fake" end. Force a jump to the next page to trigger layout.
        rvInternal.scrollToPosition(lastVisible + 1)
        stallCount = 0 // Give it a chance to recover
        // Log: "Attempting to break stall by scrolling to index ${lastVisible + 1}"
    }
}
```

## 6. Required Verification Logs
Add these specific logs to prove the model:
1.  `PROGRESS: offset=$currentOffset, delta=${currentOffset - lastKnownOffset}`
2.  `STALL: count=$stallCount, limit=$STALL_LIMIT`
3.  `ENDGAME: wall_hit=true, last_vis=$lastVisible, count=$pageCount`
4.  `WAKEUP: jumping to ${lastVisible + 1}`

## 7. Minimal Implementation Plan
1.  **Update `PdfViewerFragment.kt`**:
    *   Add `lastKnownOffset` and `stallCount` to the `scrollRunnable`.
    *   Replace `canScrollVertically` logic with the `stallCount` check.
    *   Implement the `scrollToPosition` "wake up" fallback for fake ends.
    *   Ensure `pageCount` is fetched dynamically from the adapter during the stall check.
2.  **Add Tolerance**: Use a small pixel tolerance (e.g. 2px) for the offset delta to account for rounding errors.

## 8. Final Recommendation
Stop trying to predict the end using coordinates. Instead, **observe the movement**. If the view stops moving, check if it *should* have stopped. If it shouldn't, kick it (scrollToPosition). If it should, stop the timer. This is the only way to handle the asynchronous, lazy-loading nature of `androidx.pdf.view.PdfView`.
