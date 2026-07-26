# References

Project decisions and user-interface behavior are specified in the sibling
`references` repository:

- `outline.md` — product scope and web/native boundary
- `ui-spec.md` — standalone user-interface specification
- `obf_.obz Open Board File Formats.md` — local format reference

Platform sources:

- [Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [WebViewAssetLoader](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader)
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Android Gradle Plugin release notes](https://developer.android.com/build/releases/gradle-plugin)

The web application is sourced from the sibling `libreaac` repository and is
embedded as a pinned, checksummed release—not loaded from a live site.
