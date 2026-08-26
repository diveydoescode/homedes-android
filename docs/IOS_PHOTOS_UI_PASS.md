# iOS device photo UI pass (2026-08-26)

**Source:** `C:\webapp_android\ios-photos-ui\` (copied to `artifacts/ios-photos-ui/`)

## Photo → surface map

| Photo | Surface | Visual notes for Android |
|-------|---------|--------------------------|
| IMG_9190 | Dashboard | Italic Designs; glass search/sort; SHOWCASE; Continue + › same row; ink FAB + terracotta ring |
| IMG_9191–92 | Editor split | Glass strip; title+saved; mm\|cm\|ft; modes; share/…; 3D↑2D↓; blue **G** floor on 3D; glass dock 7 |
| IMG_9193 | Catalog place | Sofa selected; Catalog dock active; Measure blue |
| IMG_9194 | New design | “New” + italic “design”; Three ways in.; paper icon tiles; AI pill; › |
| IMG_9195 | Furniture sheet | MEASURE eyebrow; steppers; materials; Duplicate/Delete |
| IMG_9196 / 9201 | Add Furniture | Title + Cancel; category chips |
| IMG_9197 | Lighting | LIGHTING; sun; Outdoor/Night; Add light |
| IMG_9198–99 | Furniture move | Floating sheet + 3D sync |
| IMG_9200 | Empty place mode | Tip: Tap to place · Drag to move |
| IMG_9202–03 | Catalog categories | Sofas / Beds; Cancel |
| IMG_9204 | Wall peek | Wall · length · Edit · Delete |
| IMG_9205–06 | More editor | Same chrome language |

## Implementation targets

1. [x] New design typography + paper icon tiles — serif “New ” + italic “design”; ivory rows with paper icon tiles (MCP `photo-pass-02-new.png`)  
2. [x] Continue hero arrow row — Continue LEFT + `arrow.right` RIGHT via `SpaceBetween`  
3. [x] Floor chip on 3D pane (not top strip) — blue **G** top-trailing (`photo-pass-05-3d.png`)  
4. [x] Catalog “Add Furniture” + Cancel (`photo-pass-04-catalog.png`)  
5. [x] Furniture MEASURE / SELECTION eyebrow + Lighting sheet from sun chip (`photo-pass-06-lighting.png`)  
6. [ ] Remaining polish: materials chips on furniture props; place-mode tip copy; catalog sort as overflow icon (iOS menu) vs inline chips  

*Updated as items land.*
