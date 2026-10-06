# Changelog

All notable changes to Readora are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - first public release

### Added - create and edit PDFs
- **New PDF**: create a blank document (A4 / Letter / Legal / A3 / A5, portrait or landscape, 1-500 pages) that
  opens straight in the editor. Available from Home, Tools and the viewer.
- **Editor** for any PDF: text boxes (Helvetica / Times / Courier, bold, italic, size, colour, automatic
  wrapping), pictures (PNG, JPEG, GIF, BMP), rectangles, ellipses, lines, a highlighter and a freehand pen.
  Select, move, resize, duplicate, delete, arrow-key nudging, undo/redo, tool shortcuts.
- Edits are written as real page content (selectable text, vector shapes, embedded pictures) and are placed
  correctly on pages rotated 90/180/270 degrees and on pages with a crop box.
- Text the standard fonts can't show (Cyrillic, Greek, CJK...) uses an installed Unicode font; if none is
  installed the characters become "?" and you are told.
- **Safe saving**: Save replaces the file only after a successful write; Save a copy leaves the original
  untouched; unsaved edits prompt on close, open, reload and exit (including the window's close button).
- **Add blank page** after the current page.

### Added - opening files
- Readora appears in the file manager's **"Open with"** list: the installers register the association (the
  Linux entry passes the file to the app, which jpackage does not do by default), and Settings can add or
  remove it per user for the portable build (Windows registry / Linux desktop entry).
- Open from the command line (`Readora file.pdf`) and via the macOS "open file" event.
- **Drag a PDF onto the window anywhere**, including over tables, lists and text fields, which previously
  swallowed drops. Non-PDF drops get an explanation instead of doing nothing.

### Changed
- **All icons are SVG.** A small dependency-free SVG renderer (`ui/Svg.java`) draws the interface icons
  (Lucide, ISC licence, see THIRD_PARTY_NOTICES.md) in the theme's colours and the logo as vectors at every
  size. The logo was traced from the new artwork; installer icons (`packaging/logo.png`, `logo.ico`) are
  generated from it with `./build.sh icons`. The Unicode/emoji glyphs used as icons (arrows, trash, bulb...)
  are gone, so nothing renders as an empty box on systems without an emoji font.
- **New app icon** used for the window, title bars, dialogs and installers.
- **One custom theme in two variants, replacing the 17 third-party IntelliJ themes.** *Readora Dark* and
  *Readora Light* use the icon's palette (near-black navy, blue -> violet -> magenta accent). The Settings
  theme picker applies instantly. The bundled `flatlaf-intellij-themes` jar (and its size) is gone; a
  previously saved theme name falls back to dark.
- **Readora's own dialogs everywhere.** `JOptionPane`, `JFileChooser` and `JColorChooser` are replaced by
  `ui.Dialogs`, `ui.AppFileChooser` and `ui.AppColorChooser` (same method signatures, so call sites only
  changed class name): themed title bar and icon, gradient message badges, wrapped text, and the gradient
  primary button. Window title bars now follow the theme instead of the operating system.
- Tools and Settings now sit in the same rounded, bordered scroll card as the Recent table; the Tools header
  stays fixed above it instead of scrolling away.
- Sidebar label "Setting" is now "Settings"; the Page Organizer subtitle no longer says "Visual Studio".
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
- **Closing the window with its X button quit instantly**, ignoring "Confirm before exit"; every way of
  quitting now goes through one path that also offers to save unsaved edits.
- **Saving a password-protected PDF failed** with PDFBox 3 unless security was set explicitly, so Edit
  Metadata (and any edit) on an encrypted file threw. Protection is now re-applied with the file's permission
  bits, and you are told which password it uses.
- **Password, permission and metadata edits no longer risk losing the original file.** The old code
  deleted the original *before* renaming the new file. Edits now go to a temporary file and replace
  the original only after a successful save; on failure the original and the open document are untouched.
- Edit Metadata no longer writes a fixed `temp.pdf` next to your file and now works on encrypted PDFs.
- Watermarking, export, split, search, print and the Page Organizer now work on their own copy of the
  document. Previously they used the document the renderer was using (not thread-safe) and watermarking
  left the open document modified in memory.
- Page Organizer refuses to save over the PDF it is reading and asks before replacing other files.
- **Settings > Clear Cache** no longer deletes your settings and Starred list.

### Fixed - found in the final audit
- Closing the Page Organizer while its thumbnails were still rendering closed the document under the render
  thread, logging dozens of "file already closed" errors. The thumbnail thread now stops first.
- `build.bat` failed when the repository path contained a space (as `javac @file` splits on spaces). It now
  writes quoted, forward-slash paths.
- Stale comments (default theme, class names) corrected.

### Added - packaging
- `scripts/package.sh` and `scripts/package.bat` build the installers/portable zips with jpackage, reading the
  version from `AppInfo.java`. A trimmed runtime (~80 MB instead of ~170 MB) containing only the modules
  Readora needs. The Linux `.deb` was installed, launched and uninstalled to verify it.

### Fixed - other
- **IMG to PDF ignored its fit-to-page sizing** and drew every image at its raw pixel size, so large photos
  ran off the page. Images are now scaled to fit and centred.
- Merge PDF and IMG to PDF now ask before replacing an existing file, and Merge refuses an output name equal
  to one of its inputs (which would corrupt the result).
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
