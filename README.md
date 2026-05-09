# PdfScrollReader 🎼

A musician-focused Android PDF reader optimized for live performance. Features dynamic time-based auto-scrolling, stylus-driven vector annotations, and permanent PDF flattening.

## 📱 About The Project

As a musician, I needed a way to read sheet music and chord charts hands-free while performing, and a way to take quick, permanent notes during rehearsals. I did not found any existing apps for such purpose, some apps either lacked smooth auto-scroll, or had poor stylus support. 

I built **PdfScrollReader** to solve this. It allows users to import PDFs, calculate precise auto-scroll speeds based on song duration, draw high-fidelity annotations using a stylus, and export those annotations permanently burned into the PDF.

### ✨ Key Features
*   **Semantic Auto-Scroll:** Dynamically calculates scroll speed based on physical page rendering and user-defined song duration, utilizing simulated touch events to break through Android's native lazy-loading boundaries.
*   **Stylus & Touch Annotation:** Custom `InkCanvasView` overlay supporting Pens, Highlighters (with MULTIPLY blend modes), Rectangles, and precision Bezier-curve Ellipses.
*   **True Vector Export (Flattening):** Uses `pdfbox-android` to draw UI strokes directly into the background PDF Content Stream, ensuring annotations are permanently flattened with 100% vector crispness and alpha-transparency fidelity.
*   **Setlist Management:** Organize PDFs into custom setlists for quick access during live gigs.
*   **Persistent State:** Utilizes `Gson` and Kotlin Coroutines for safe, lifecycle-aware background saving of UI coordinates and tool preferences.

## 📸 Screenshots
![image alt](https://github.com/itssanjog1nonly-dotcom/And_app_PDF/blob/913a658d8d257a9d3ec47b4ce960605e02219e1d/SS1.jpg)
![image alt](https://github.com/itssanjog1nonly-dotcom/And_app_PDF/blob/913a658d8d257a9d3ec47b4ce960605e02219e1d/SS2.jpg)
![image alt](https://github.com/itssanjog1nonly-dotcom/And_app_PDF/blob/913a658d8d257a9d3ec47b4ce960605e02219e1d/SS3.jpg)
![image alt](https://github.com/itssanjog1nonly-dotcom/And_app_PDF/blob/913a658d8d257a9d3ec47b4ce960605e02219e1d/SS4.jpg)
![image alt](https://github.com/itssanjog1nonly-dotcom/And_app_PDF/blob/913a658d8d257a9d3ec47b4ce960605e02219e1d/SS5.jpg)
![image alt](https://github.com/itssanjog1nonly-dotcom/And_app_PDF/blob/913a658d8d257a9d3ec47b4ce960605e02219e1d/SS6.jpg)

## 🛠 Built With
*   **Kotlin** - Primary programming language.
*   **Android SDK (API 31-35)** - Native UI and lifecycle management.
*   **AndroidX PDF (Experimental)** - Core PDF rendering engine.
*   **PDFBox-Android** - Advanced document manipulation and vector graphics export.
*   **Kotlin Coroutines & Dispatchers** - Asynchronous file I/O and non-blocking UI updates.
*   **Gson** - Complex data serialization for sidecar annotation storage.

## 🚀 Technical Highlights for Developers
*   **Z-Order & Alpha Blending:** Solved complex Canvas vs PDF coordinate mapping issues (flipping Y-axis origin from Top-Left to Bottom-Left) and prevented double-alpha overlapping on shape borders.
*   **Lifecycle Persistence:** Engineered a `GlobalScope` + `NonCancellable` coroutine fallback to guarantee file writes complete successfully even when the Android OS aggressively destroys the Fragment lifecycle on app exit.
*   **Type-Safe Deserialization:** Overcame standard JSON limitations by implementing explicit `TypeToken` deserialization to preserve integer-based Map keys for page-indexed annotations.
