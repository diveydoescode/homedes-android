# Editor UI audit — iOS → Android (2026-08-26)

**Sources:** `homedesign-ios` `EditorDeckChrome.swift` (live `EditorDock`), `RootView.editorTopBar`, `HDGlassBackground`; Android `EditorScreen.kt` + `LiquidGlass.kt` (Kyant Backdrop).  
**Goal:** Full visual port of editor chrome using android-liquidglass; Layer-2 glass only.

## Live iOS truth (what ships)

| Surface | iOS | Android today | Gap |
|---------|-----|---------------|-----|
| Top strip | Glass island r18–20; back; serif title; units; mode; share/more | Glass r20; back; sans title; units; mode; share; more; **floor chip** | Type (serif/mono); crowding; floor on strip vs 3D overlay |
| Dock | **7 slots:** Edit⇄Measure, Add▾, Catalog, Sketch+AI, Walk, Undo, Redo | Same + **Ortho** + DrawWall **Int/Ext** | Density — move Ortho/Int/Ext off persistent dock |
| Glass | ultraThin + shine + rule + shadow | Kyant blur/lens + border; weak shadow/shine | Add soft elevation + inset highlight |
| Add sheet | Sectioned List OPENINGS/FURNITURE/STRUCTURE/ANNOTATE | Flat card list | Sectioned inset rows + icons |
| Props | Peek detents (~96 wall / 0.4) | Capped 280dp ivory box | Closer peek / drag feel |
| Overlays | Plan2DChips, draw options bar, delete toast pill | Missing / tip banners only | Port overlays |

## Implementation order

1. Dock = 7 iOS slots; Ortho + Int/Ext → floating context row (draw / always for Ortho above dock). **Done**
2. Dock button metrics: 40×30 icon tile r9, mono 8 labels, Add chevron, Sketch AI badge, disabled 0.35. **Done**
3. Top strip: serif title + mono saved; tighten mode/units; glass shadow. **Done** (floors via overflow)
4. Plan2DChips + draw thickness capsule. **Done** (Plan2D chip + Ortho/Int/Ext context row)
5. Add sheet sections matching iOS. **Done** (WALLS/OPENINGS/FURNITURE/STRUCTURE/ANNOTATE)
6. Delete toast pill above dock. **Still open**
7. MCP screenshot compare. **Done** (`artifacts/ui-port-editor-v3.png`, Add sheet verified)

*Out of scope this pass:* iOS 26 morph glass animations; catalog mesh art; designer pixel zip beyond this audit.
