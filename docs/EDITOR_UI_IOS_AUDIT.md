# Editor UI audit — iOS → Android (2026-08-26)

**Sources:** `homedesign-ios` `EditorDeckChrome.swift` (live `EditorDock`), `RootView.editorTopBar`, `HDGlassBackground`; Android `EditorScreen.kt` + `LiquidGlass.kt` (Kyant Backdrop).  
**Goal:** Full visual port of editor chrome using android-liquidglass; Layer-2 glass only.

## Live iOS truth (what ships)

| Surface | iOS | Android today | Gap |
|---------|-----|---------------|-----|
| Top strip | Glass island r18–20; back; serif title; units; mode; share/more | Glass r20; SF chevron/share/ellipsis; serif title; units; SF mode icons; share; more | Minor type/crowding |
| Dock | **7 slots:** Edit⇄Measure, Add▾, Catalog, Sketch+AI, Walk, Undo, Redo | Same 7 slots with SF icons (cursorarrow/ruler/plus/chair/viewfinder/walk/uturn) | — |
| Glass | ultraThin + shine + rule + shadow | Kyant blur/lens + border; soft elevation | Morph glass out of scope |
| Add sheet | Sectioned List OPENINGS/FURNITURE/STRUCTURE/ANNOTATE | Sectioned rows + SF icons (door/window/sofa/…/chevron.right) | — |
| Props | Peek detents (~96 wall / 0.4) | Phone ModalBottomSheet + wall 96dp peek (length/Edit/Delete); wide side panel unchanged | Fine-tune detent animation |
| Overlays | Plan2DChips, draw options bar, delete toast pill, right-edge circles | Plan2D chip + Ortho/Int/Ext; DeleteToastPill; PlanToolRail (hinge/swing/paint/fit/ortho/draw) | — |

## Implementation order

1. Dock = 7 iOS slots; Ortho + Int/Ext → floating context row (draw / always for Ortho above dock). **Done**
2. Dock button metrics: 40×30 icon tile r9, mono 8 labels, Add chevron, Sketch AI badge, disabled 0.35. **Done**
3. Top strip: serif title + mono saved; tighten mode/units; glass shadow. **Done** (floors via overflow)
4. Plan2DChips + draw thickness capsule. **Done** (Plan2D chip + Ortho/Int/Ext context row)
5. Add sheet sections matching iOS. **Done** (WALLS/OPENINGS/FURNITURE/STRUCTURE/ANNOTATE)
6. Delete toast pill above dock. **Done** (`DeleteToast` + `DeleteToastPill`, Undo → VM.undo, 5s auto-dismiss)
7. SF Symbol icons 1:1. **Done** (`HdSfIcons` + `SfIcon`; editor chrome/dock/modes/add/rail)
8. Right-edge tool rail. **Done** (`PlanToolRail` ivory circles)
9. MCP screenshot compare. **Done** (`artifacts/ui-port-editor-v3.png`, Add sheet verified)

*Out of scope this pass:* iOS 26 morph glass animations; catalog mesh art; designer pixel zip beyond this audit.
