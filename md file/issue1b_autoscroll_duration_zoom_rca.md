# RCA & Solution Design: Auto-Scroll Duration Calibration (Zoom Accuracy)

## 1. Why Duration is Wrong (The Coordinate Mismatch)
The auto-scroll duration is inaccurate because of a fundamental mismatch between the **Speed Calculation Space** and the **Execution Space**.

*   **Speed Calculation Space (Zoomed Viewport)**: The current code calculates distance using `currentPageLocations`. These values are in **viewport pixels**, which scale linearly with zoom. At 0.4x zoom, the perceived document height is only 40% of its physical content height.
*   **Execution Space (Unzoomed Content)**: The `rvInternal.scrollBy(0, delta)` command operates on the internal `RecyclerView`. In the AndroidX `PdfView` implementation, this `RecyclerView` holds page views. Crucially, calling `scrollBy(100)` moves the viewport by 100 **content pixels** (unzoomed), regardless of the current zoom level.

**The Math of Failure:**
1.  Request: 10s duration.
2.  Reality: Document is 10,000 unzoomed pixels tall.
3.  State: Zoomed out to 0.5x.
4.  Buggy Calculation: Perceived distance = 5,000px. Speed = 5,000 / 10 = 500 px/s.
5.  Buggy Execution: `scrollBy(500)` moves 500 content pixels per second.
6.  Result: $10,000 / 500 = 20$ seconds. (Exactly $1/Zoom$ error).

## 2. Identified Metrics
*   **Distance Source**: `getTrueVerticalScrollRange()` (was using zoomed `currentPageLocations`).
*   **Speed Source**: `scrollSpeedPxPerMs` (calculated once at `startAutoScroll`).
*   **Execution**: `rvInternal.scrollBy(0, deltaToScroll)`.

## 3. Divergence Analysis
The largest time error occurs at **heavy zoom-out**. 
*   When zoomed out, the "zoomed distance" is very small. 
*   This results in a very slow calculated speed.
*   But since the physical distance to travel (unzoomed) remains constant, the slow speed results in a massive duration overrun.

## 4. Candidate Speed Models

| Model | Logic | Pros | Cons |
| :--- | :--- | :--- | :--- |
| **1. Viewport-Relative** | `Speed = ZoomedDist / Time` | Matches "visual" pixel speed. | **FAILS**. Requires `scrollBy` to be zoom-aware (it isn't). |
| **2. Content-Relative (Fixed)** | `Speed = UnzoomedDist / Time` | Calibration is perfect at start. | Fails if user zooms *during* playback (time is lost/gained). |
| **3. Time-Remaining Dynamic** | `Speed = RemainingUnzoomedDist / RemainingTime` | **WINNER**. Self-correcting. | Slightly more complex; requires smoothing to avoid speed jumps. |

## 5. Recommended Robust Model: "Unzoomed Time-Remaining Sync"
We should use a dynamic model that calculates how many **unzoomed content pixels** are left between the current viewport top and the document bottom, then divides that by the **remaining clock time**.

### The Formula:
$$Speed_{ms} = \frac{TotalUnzoomedHeight - CurrentUnzoomedOffset - ViewportHeight_{unzoomed}}{TotalDuration_{ms} - ElapsedTime_{ms}}$$

*   $TotalUnzoomedHeight$: $PageCount \times (AvgPageHeight_{zoomed} / Zoom + Gap)$.
*   $CurrentUnzoomedOffset$: `rvInternal.computeVerticalScrollOffset()`.
*   $ViewportHeight_{unzoomed}$: `rvInternal.height / Zoom`.

## 6. Speed Control Strategy: Dynamic with Smoothing
Speed should be **dynamically corrected** every tick (or every 500ms) to account for:
1.  Initial zoom level.
2.  User manual scrolling (skipping ahead or back).
3.  User zooming in/out during performance.

**Smoothing**: To avoid jerky motion if the zoom changes abruptly, we will use a simple linear interpolation or a low-pass filter on the calculated speed.

## 7. Implementation Plan
1.  **Metric Correction**: Ensure `getTrueVerticalScrollRange` and `getVerticalScrollOffset` both return values in the **same unzoomed content space**.
2.  **State Tracking**: 
    *   `startTimeMs`: The moment play was pressed.
    *   `initialTotalUnzoomedDist`: Distance from 0 to bottom at start.
3.  **Dynamic Speed Update**: 
    *   In `scrollRunnable`, calculate `remainingMs = totalDurationMs - (now - startTimeMs)`.
    *   Calculate `remainingDist = totalUnzoomedHeight - currentOffset - (viewportHeight / zoom)`.
    *   Update `scrollSpeedPxPerMs = remainingDist / remainingMs`.
4.  **Safeguards**: Clamp speed to prevent backward scrolling or extreme "catch-up" bursts if the user scrolls too far manually.

## 8. Verification Logs
*   `CALIB_START: reqDuration=${durationSeconds}s, totalUnzoomedHeight=$range`
*   `CALIB_TICK: elapsed=${now-start}ms, zoom=$zoom, currentOffset=$offset, targetSpeed=$speed px/ms`
*   `CALIB_END: actualDuration=${finalElapsed}s, error=${finalElapsed - reqDuration}s`

## 9. Final Recommendation
Implement the **Dynamic Time-Remaining Sync** model. It is the only way to guarantee the song finishes exactly when the timer hits zero, regardless of how the user interacts with zoom or manual scroll during the performance.
