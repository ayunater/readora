# Changelog

All notable changes to Readora are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - first public release

### Changed
- **Viewer split into focused classes** (new `viewer` and `viewer.ops` packages) in place of a single
  5,300-line `pdfViewer` class: `PdfSession`, `RenderService`, `DocumentView`, `PageCanvas`,
  `ThumbnailPanel`, `ViewerHeader`, `ViewerControls`, plus one operations class per concern
  (`InfoOps`, `ExportOps`, `WatermarkOps`, `PageOps`, `SecurityOps`, `SearchOps`, `PrintOps`).
- **One consistent UI.** A shared `ui.Ui` class defines corner radii, control height, fonts, cards,
  headers and the bottom button bar; Home, Recent, Starred, Tools, Settings and the Viewer all use it.
  The viewer now has the same header, rounded cards and bottom button bar as the other screens.
- Recent and Starred are thin subclasses of one `FileListScreen` (previously two ~425-line copies).
- Tools: "Export Text (3 option available)" is now "Export Text".

### Fixed - rendering
- Pages are rendered at true 96-DPI scale (previously 72 DPI, which looked blurry), and HiDPI aware.
- Pages render lazily, on a background thread, into a size-bounded cache. Previously every page was
  rendered at once and kept in memory, so large PDFs could run out of memory and every zoom step
  re-rendered the whole document. A 300-page PDF now stays around 35 MB of heap.
- Visible pages render before prefetched pages and thumbnails; the old stack rendered in request order.
- A page that fails to render shows a placeholder instead of stopping all later pages.
- Mixed page sizes and rotated pages lay out correctly (fit-width previously used page 1 only).
- Very large pages are rendered at a capped resolution instead of exhausting memory.
- The first page is no longer rendered on the UI thread; system fonts are warmed up in the background.
- Zoom keeps the page you are looking at in view.

### Fixed - data safety
- **Password, permission and metadata edits no longer risk losing the original file.** The old code
  deleted the original *before* renaming the new file. Edits now go to a temporary file and replace
  the original only after a successful save; on failure the original and the open document are untouched.
- Edit Metadata no longer writes a fixed `temp.pdf` next to your file and now works on encrypted PDFs.
- Watermarking, export, split, search, print and the Page Organizer now work on their own copy of the
  document. Previously they used the document the renderer was using (not thread-safe) and watermarking
  left the open document modified in memory.
- Page Organizer refuses to save over the PDF it is reading and asks before replacing other files.
- **Settings > Clear Cache** no longer deletes your settings and Starred list.

### Fixed - other
- The project did not compile: `openPageOrganizer()` was defined twice. About 700 lines of dead code
  (`extractPages` and three `legacy*` page methods) were removed.
- Light themes crashed the sidebar colour calculation (unclamped RGB values above 255).
- Recent and Home lists were empty until Refresh was pressed; they now reload when shown.
- File names or paths containing commas broke Recent/Starred, and Home dropped the time. Lists are now
  stored in a tab-separated UTF-8 file; existing `recent.csv` / `starred.csv` are migrated automatically.
- Text export is always UTF-8 (previously the platform default charset).
- Split PDF no longer crashes on a one-page PDF and no longer accumulates file names between runs;
  it now takes a comma-separated list such as `3, 7`.
- Search no longer throws on ligatures or accented text, and jumping to a hit on a page that has not
  been rendered yet works. All hits are highlighted and the results list stays open while you step through.
- Settings: the theme button was labelled `applyTheme` and the theme was applied twice per click.
- Image extraction now also finds images inside form XObjects.
- Add / Change password reopen the result immediately instead of prompting for the password you just set.

### Added
- Drag and drop a PDF onto the window to open it.
- Page navigation box, editable zoom box, Ctrl + wheel zoom, drag-to-pan, keyboard shortcuts.
- Star button toggles (Star / Unstar).
- Build scripts (`build.sh`, `build.bat`), automated smoke tests and a GitHub Actions workflow.
