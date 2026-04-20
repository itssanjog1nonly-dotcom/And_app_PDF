# RCA for Scroll Early-Stop Bug

## 1. Active Scroll Target
- **Exact Class**: `androidx.pdf.view.PdfView`
- **Assignment Path**: `AndroidXPdfFragment._pdfView` (Reflection) -> `setupPageIndicator()` -> `pdfScrollableView`.
- **Proof**: `AutoScroll_Diag: Scroll Target Class: androidx.pdf.view.PdfView`

## 2. Runtime Metrics at Failure
| Metric | Value |
| :--- | :--- |
| **Offset** | 8474 |
| **Range** | 10073 |
| **Extent** | 1599 |
| **Delta** | 10.014728 |
| **canScrollVertically(1)** | **false** |

## 3. Stop Condition Analysis
- **Is `canScrollVertically(1)` true?** No. 
- **Log Evidence**: `Tick: offset=8474, range=10073, extent=1599, delta=10.014728, canScroll=false`
- **Conclusion**: The view itself believes it has reached the physical end of the scrollable content, even though the PDF document has more pages.

## 4. Target Resolution
The target is **PdfView**. While it contains a `RecyclerView`, the current `pdfScrollableView` reference is the `PdfView` wrapper.

## 5. Range Estimation Behavior
- **Start Range**: 10073
- **End Range**: 10073
- **Observation**: The range does not dynamically expand to the full 20 pages. It stays locked to the "estimated" height of the first few pages. This is a classic `RecyclerView` issue where `computeVerticalScrollRange` is based on the average height of *measured* items multiplied by total items, but the underlying library is likely reporting a truncated "total items" or zero-height for unvisited pages to the LayoutManager.

## 6. Proven Statement
**B. Underestimated range is the main bug.**

The bug is caused by trusting `computeVerticalScrollRange` on a view that uses lazy-loading/estimation. The scroller stops because the math `offset >= range - extent` becomes true at the end of the "estimated" range.
