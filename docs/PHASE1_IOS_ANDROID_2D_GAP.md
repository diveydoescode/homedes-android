# Phase 1 — iOS ↔ Android 2D editor gap matrix

**Goal:** No functional difference in 2D editing, sketch-to-plan, PDF, or DXF vs iOS.  
**Inventories:** 2026-08-26 explore of `homedesign-ios` + `homedes-android`.  
**Out of Phase 1:** AR, Filament lighting polish, Stage room chrome, catalog mesh pack, live Vercel sketch ops deploy, UI pixel chrome (designer zip).

Status key: **Done** / **Partial** / **Missing** (Android relative to iOS).

---

## Editor tools

| Capability | Android | Notes / fix |
|---|---|---|
| Wall draw + chain + thickness + joins + T-heal | Done | Device QA still listed |
| Curve / bow / multi-span | Done | |
| Endpoint move | Done | |
| Wall body drag move | Done | SelectDragKind.WallBody → previewWallMove + T-heal commit |
| Wall endpoint drag | Done | SelectDragKind.WallEndpoint → SnapEngine + previewEndpointMove |
| Typed wall length | Done | |
| Format painter (walls) | Done | |
| Ortho lock | Done | Dock Ortho chip + DataStore; constrainToOctant on wall draw + endpoint drag |
| Wall squaring offer | Missing | Port `WallSquaring` |
| Filleted corners toggle | Missing | Low priority visual |
| Draw rectangle room + detect | Done | |
| Openings place/slide/resize/hinge/swing/symbols | Done | Device QA |
| Opening dressings UI | Missing | Mostly 3D; schema passthrough only |
| Furniture place/stamp/move/rotate/snap | Done | |
| Draw custom furniture box | Done | Add → Custom furniture; AABB drag + name dialog (default “Custom”) |
| Multi-select / align / distribute / group | Done | Marquee may be Missing (long-press only) |
| Structures + labels place | Done | Label drag (SelectDragKind.LabelMove) + sheet rename |
| Pegman walk-here | Done | |
| Dimensions measure / exterior / typed / drag | Done | |
| Wall-sheet one-tap dimension | Done | `DimensionMutation.dimensions(forWall)` + sheet "Add dimension" |
| Trace underlay | Done | |
| Level ghost | Done | |
| Clipboard furniture | Done | |
| Clipboard openings + wall rebind | Done | `PlanClipboard` encode/paste/rebind + opening sheet |
| Undo/redo + autosave | Done | |
| Sketch flow (in-app + mock) | Done | Live Vercel blocked externally |

## Export (must match iOS)

| Capability | Android | Notes / fix |
|---|---|---|
| PDF A4 landscape, title, 1 m bar | Done | |
| PDF walls mitered / curved footprint | Done | `miteredPoints` / `ArcWallGeometry.footprint` solid fill (iOS PlanPDFRenderer) |
| PDF plan labels (`PlanLabel`) | Done | pitch≈0 labels drawn |
| PDF openings | Done (iOS match) | No separate opening strokes — solid walls like iOS PDF |
| DXF cm→mm ×10, Y-flip | Done | |
| DXF R2018 / AC1032 + INSUNITS | Done | Shell fragments in `resources/dxf_shell/` |
| DXF mitered walls broken at openings | Done | `wallDrawOps` + cutouts; curved via `ArcWallGeometry` |
| DXF `WALL_HATCH` ANSI31 | Done | |
| DXF furniture WYSIWYG / symbol art | Partial | Procedural `FurnitureSymbols` + path flatten; SVG-backed kinds still footprint until SVG wired into export |
| DXF opening BLOCK+INSERT | Done | Bound openings as BLOCK+INSERT; unbound ticks |
| DXF plan labels `A-NOTES` | Done | pitch≈0 only |
| DXF shelves | Done | JsonElement `shelfUnits` parsed best-effort; skip if unparsed |
| DXF aligned DIMENSION entities | Done | `addAlignedDimension` |

## P0 implementation order

1. PDF → iOS `PlanPDFRenderer` geometry (mitered/curved walls + labels).
2. DXF → port web/iOS `PlanDXFRenderer` + `DXFWriter` (+ R2018 shell) behavior. **Done** (SVG furniture art still Partial).
3. Opening clipboard + rebind. **Done** (`PlanClipboard`).
4. Remaining editor Missing rows (squaring, marquee). Furniture box + label move + ortho **Done**. Wall-sheet dim **Done**.
5. MCP device QA of Phase 1 flows.

---

*Update this file as gaps close.*
