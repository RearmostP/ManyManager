# ManyManager UI reference

A source based, interactive reconstruction of the current Android interface. The Android app and its data are untouched.

## Run

From the repository root:

```sh
python -m http.server 8000 --directory ui-reference
```

Open http://localhost:8000/ . The entry point is `index.html`. ES modules and fetched locale JSON require an HTTP server; direct `file://` loading is not supported.

## Included

The home, expense summary, settings, and budget management screens; the side menu; the new expense sheet; summary tabs; home budget expansion; settings expansions; budget month controls; and language and theme choices. Escape closes an overlay or returns to the previous page. The sheet can be dismissed by dragging its handle downward at least 64 CSS px. Drawer and sheet edges align with the app viewport.

Initial state matches the Android source: Hebrew, RTL, light theme, home screen, current month, zero expenses, no categories or payment methods, no budget sources. Language, theme, and draft field values live only in JavaScript memory on the currently loaded page. They reset on reload or tab reopening. The disabled Add and Save controls mirror the Android code. Summary month buttons are disabled in Android; budget month buttons work.

## Viewport and fidelity

The layout is capped at 440 CSS px on desktop and fills the width of a narrow phone browser. The source uses a 440dp cap, with a 25dp page inset, 66dp top area, and system insets supplied by Android. Visual checks used a 1280 × 956 CSS px desktop viewport and a 390 × 844 CSS px phone viewport. A 440 × 956 CSS px viewport is also useful for a direct phone width comparison. CSS px are not physical screenshot pixels.

The Android app uses the platform `sans` font, normally Roboto on Android, and resource vector icons. Browser rendering uses locally available Roboto or system fallback. The icon paths are reproduced as inline SVG from the Android vectors. Font metrics, native EditText behavior and IME, system bars, ripple effects, and window insets can differ. The web version does not draw fake system bars.

Verified through the local HTTP server in a browser after the file split: all four screens loaded; summary tabs, budget and settings navigation, settings expansions, theme and language changes worked; Hebrew was RTL and English/Russian were LTR. On desktop, the Hebrew drawer met the app's right edge and the English drawer met its left edge. On a 390px viewport, the Russian drawer met the left edge and the Hebrew drawer met the right edge. The expense sheet matched the app width; its close button, backdrop, and downward drag beyond the handle dismissed it. Clicking inside the drawer and sheet kept them open. No browser console errors were reported.

A connected device showed another application, so no screenshot from a running ManyManager build was obtained. Visual equivalence against Android rendering has not been confirmed. The reconstruction was also checked against Android XML layouts, Kotlin behavior, color resources, and strings.
