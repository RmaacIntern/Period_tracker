# DESIGN-STANDARD.md — Period Tracker
> **Day-Zero Design Reference.** Read this before drawing any layout, writing any XML, or calling any `GradientDrawable`. Every value here is extracted directly from the live codebase. Do not invent new values — pick from this document.

---

## 1. Design Philosophy

The app uses a **soft feminine palette** — pale lavender/blush backgrounds with rose-pink and purple accents. The feeling should be **calm, clean, and warm** — not clinical, not garish. Layouts are built in XML (no Compose). All rounded corners, gradients, and tints are applied programmatically in Kotlin via `GradientDrawable`, not via XML shape files (except a few drawable resources).

**Three rules that never break:**
1. No hard white backgrounds as the page root — always use the blush base `#FBF5F8`.
2. No raw `android:background` hex in XML for cards — set card backgrounds in Kotlin with `roundedBg()`.
3. No new colours — use only the tokens in Section 2.

---

## 2. Colour Tokens

### 2.1 Primary Palette

| Token Name | Hex | Usage |
|---|---|---|
| `color_primary_text` | `#2D1B33` | All headings, bold values, primary body text |
| `color_secondary_text` | `#8A7A8F` | Labels, subtitles, hint text, disabled state text |
| `color_pink` | `#EC4899` | Period accent, active state, primary CTA sub-labels |
| `color_purple` | `#A855F7` | Fertile window accent, secondary CTA |
| `color_deep_purple` | `#7C3AED` | Ovulation accent, deep purple tags |
| `color_green` | `#3B7A57` | Logged/confirmed state, fertile window dot |
| `color_orange` | `#E66A28` | Ovulation dot on calendar |
| `color_gold` | `#E4A850` | Primary action button fill (`btncolor`) |
| `color_blue` | `#197AEF` | Info links |

### 2.2 Background Palette

| Token Name | Hex | Usage |
|---|---|---|
| `bg_page` | `#FBF5F8` | Root background for all fragments |
| `bg_hero` | `#FDF0F5` | Hero / header section backgrounds |
| `bg_white` | `#FFFFFF` | Card backgrounds, bottom sheets |
| `bg_chip_default` | `#F9F3FB` | Unselected chip background |
| `bg_chip_section` | `#F5EEF8` | Chip group section background |
| `bg_purple_tint` | `#F3EDFF` | Purple-tinted icon backgrounds |
| `bg_pink_tint` | `#FFF0F7` | Pink-tinted icon backgrounds |
| `bg_rose_tint` | `#FDE2E9` | Deep rose tag backgrounds |
| `bg_lavender_tint` | `#F1E7FB` | Lavender tag / badge backgrounds |
| `bg_fertile_card` | `#F8F0FF` | Fertile window prediction card |
| `bg_period_card` | `#FFF5F7` | Period prediction card |
| `bg_info_banner` | `#F3EEFF` | Info/tip banners |
| `bg_divider` | `#EDE6F0` | Horizontal dividers, separator lines |
| `bg_border` | `#C5B8CC` | Input borders, subtle outlines |

### 2.3 Gradient Backgrounds (Splash + Heroes)

Always use `GradientDrawable` set in Kotlin code, never in XML attributes.

| Location | Orientation | Start → End |
|---|---|---|
| Splash screen root | `TL_BR` | `#F0E6FA` → `#FDE8F3` → `#FFFFFF` |
| CTA button (gradient) | `LEFT_RIGHT` | `#EC4899` → `#A855F7` |
| Progress bar fill | `LEFT_RIGHT` | `#E63A5E` → `#D63F7A` → `#C2185B` |
| Cycle banner (active period) | `LEFT_RIGHT` | `#EC4899` → `#A855F7` |

### 2.4 Cycle Phase Colour Map

| Phase / State | Colour |
|---|---|
| Period (logged) | `#C2185B` / `#EC4899` |
| Predicted period | `#FBCFE8` (soft pink dot) |
| Fertile window | `#3B7A57` (green) |
| Ovulation day | `#E66A28` (orange) |
| Low fertility / Safe | muted / grey |
| Late period | `#EC4899` (same as period, flashing) |

### 2.5 Switch / Toggle Colours

| State | Thumb | Track |
|---|---|---|
| ON | `#EC4899` | `#FBCFE8` |
| OFF | `#D0C8D5` | `#EDE6F0` |

### 2.6 Chip Colours

| State | Background | Stroke | Text |
|---|---|---|---|
| Unselected | `#F9F3FB` | `#C5B8CC` (1–2dp) | `#2D1B33` |
| Selected | gradient `#EC4899`→`#A855F7` or solid `#2D1B33` | none | `#FFFFFF` |
| Ripple | `#20EC4899` | — | — |

---

## 3. Typography

No custom font is loaded — all text uses the system default (Roboto on Android). All sizes are in `sp`. Bold is always `android:textStyle="bold"`. No italic is used anywhere in the app.

### 3.1 Text Size Scale

| Role | Size | Weight | Colour | Example Usage |
|---|---|---|---|---|
| Display / Hero | `32sp` | bold | `#2D1B33` | Splash app name |
| Screen title | `28sp` | bold | `#2D1B33` | Sheet date picker header |
| Section heading | `20sp` | bold | `#2D1B33` | "Period Tracker" top bar, bottom sheet titles |
| Card value large | `18sp` | bold | `#2D1B33` | Greeting "Hi there 👋", sheet sub-heads |
| Card value | `16sp` | bold | `#2D1B33` | Summary card values (Flow, Mood, Basal Temp) |
| Body / primary | `15sp` | normal | `#2D1B33` | Body copy, sheet body text |
| Body small | `14sp` | bold | `#2D1B33` | Section label headers ("Quick Actions", "Predictions") |
| Chip label | `13sp` | normal | `#2D1B33` | Chip text, list items, secondary values |
| Caption large | `12sp` | bold/normal | `#8A7A8F` | Step label, phase sub-heading |
| Caption | `11sp` | normal | `#8A7A8F` | Legend labels, calendar weekday headers |
| Micro label | `10sp` | bold | `#2D1B33` or `#8A7A8F` | Symptom section labels ("FLOW", "MOOD") |
| Tag / badge | `9sp` | bold | accent colour | "Logged", "Estimated" tags |
| Tiny | `8sp` | bold | `#8A7A8F` | Stat card category labels ("PERIOD", "FERTILE") |

### 3.2 Letter Spacing

Used only on **uppercase label text**:
- Section labels (e.g. "STEP 1 OF 8", "TODAY'S SUMMARY", "FLOW"): `letterSpacing="0.08"`
- Stat card labels ("PERIOD", "FERTILE", "OVULATION"): `letterSpacing="0.08"`
- Splash tagline: `letterSpacing="0.01"`
- All other text: no `letterSpacing` attribute.

### 3.3 Capitalisation Convention

| Type | Case |
|---|---|
| Section category labels | ALL CAPS — "FLOW", "MOOD", "STEP 1 OF 8" |
| Button text | Title Case — "Log Today's Symptoms", "Get Started" |
| Section headings | Title Case — "Quick Actions", "Predictions" |
| Body / chip text | Sentence case |

---

## 4. Spacing & Layout Grid

All values in `dp`. Pick from this list; do not invent new values.

### 4.1 Screen Horizontal Padding

| Context | Value |
|---|---|
| Standard fragment content | `paddingHorizontal="20dp"` |
| Bottom sheet content | `paddingHorizontal="24dp"` |
| Onboarding screens | `paddingHorizontal="40dp"` |
| Chip group rows | `paddingHorizontal="16dp"` |
| Top bar inside hero | `paddingHorizontal="20dp"` (inherited from hero) |

### 4.2 Vertical Rhythm (Margins)

| Gap | Value | Where |
|---|---|---|
| Between major sections | `24dp` – `28dp` | White section → Predictions, etc. |
| Between cards in a list | `10dp` – `12dp` | Prediction cards, quick action rows |
| Between label and value | `4dp` | All label→value pairs |
| Between related elements | `8dp` | Legend rows, chip rows |
| Top of hero content | `16dp` | Below top bar inside hero |
| Hero bottom padding | `24dp` | Below last element in hero |
| Screen bottom padding | `32dp` | Last element on scrollable screens |
| Above section heading | `20dp` – `28dp` | "Quick Actions", "Predictions" headings |
| Above progress bar | `14dp` | In step header |

### 4.3 Component Heights

| Component | Height |
|---|---|
| Custom bottom nav bar | `64dp` |
| Bottom nav tab tap area | `44dp` |
| Primary CTA button | `52dp` – `56dp` |
| Standard chip (`CycleChip`) | `34dp` min |
| Condition chip | `36dp` min |
| Legend card row | `36dp` |
| Stat mini-card row | `wrap_content` + `paddingVertical="12dp"` |
| Cycle ring | `280dp × 280dp` |
| Splash logo circle | `148dp × 148dp` |
| Onboarding back button | `35dp × 30dp` |
| Step progress bar track | `5dp` tall |
| Divider line | `1dp` |
| Dot indicator | `8dp × 8dp` |
| Bottom sheet handle | see §6 |

---

## 5. Corner Radius Tokens

All radii are set via the Kotlin helper `roundedBg(colorHex, radiusDp)`. Pick the right radius for the component type.

| Radius | Usage |
|---|---|
| `50f` dp (pill) | Circle icon buttons (history arrow), fully round small icons |
| `22f` dp | Large summary cards, chart cards, export card |
| `20f` dp | Stat cards row, nav row, calendar card, tag chips ("Logged", "Estimated") |
| `16f` dp | Today's tracking card, daily tip card, CTA button on some screens |
| `14f` dp | Last period card, next period card, basal temp card, BBT card, icon badges (export) |
| `12f` dp | Cervical row background, info banner, small icon badge backgrounds |
| `10dp` | Progress bar track and fill (via `progress_bar_custom.xml`) |
| `4dp` | Bottom sheet handle pill (`sheet_handle.xml`) |
| `100dp` | `CycleChip` (fully rounded pill in XML style) |
| `20dp` | `ConditionChip` in XML style |

---

## 6. Component Patterns

### 6.1 Cards

Cards are **not** `CardView`. They are `ConstraintLayout` or `LinearLayout` with a `roundedBg()` background set in Kotlin.

```kotlin
// Standard card setup
binding.myCard.background = roundedBg("#FFFFFF", 22f)
// Tinted card
binding.lastPeriodCard.background = roundedBg("#FFF5F7", 14f)
```

Card internal padding: `14dp` – `16dp` all sides.
Cards never have a visible stroke border.
Cards never have `elevation` unless they are bottom-nav or shimmer overlays.

### 6.2 Chips

Two chip styles defined in `themes.xml`:

**`CycleChip`** (for flow, mood, symptoms, cervical fluid, LH):
- Background: `#F9F3FB` (unselected) → gradient `#EC4899`→`#A855F7` or solid fill when selected
- Stroke: `2dp`, `@color/chip_stroke_selector`
- Corner radius: `100dp` (full pill)
- Min height: `34dp`
- Horizontal padding: `12dp`
- Text: `12sp`, `@color/chip_text_selector`
- Checked icon: hidden
- Ripple: `#20EC4899`

**`ConditionChip`** (for health conditions in onboarding/profile):
- Background: `@color/chip_background_selector`
- Stroke: `1dp`, `@color/chip_stroke_selector`
- Corner radius: `20dp`
- Text: `13sp`, `#2D1B33`
- Checked icon: hidden

Never mix these two styles on the same screen.

### 6.3 Primary CTA Button

Implemented as `GradientButton` (custom widget) or a `TextView`/`View` with a programmatic gradient:
- Background gradient: `#EC4899` → `#A855F7`, `LEFT_RIGHT`
- Corner radius: `16f` dp
- Text: white, `16sp`, bold
- Height: `52dp` – `56dp`
- Horizontal margin: none (full width) or `20dp` inset

### 6.4 Section Labels (Category Headers)

Pattern used everywhere above a data section:
```xml
android:text="TODAY'S SUMMARY"
android:textSize="10sp"
android:textStyle="bold"
android:letterSpacing="0.08"
android:textColor="#8A7A8F"
```
Always ALL CAPS, `#8A7A8F`, `10sp`, `bold`, letter-spaced.

### 6.5 Prediction / Info Cards (Icon + Text + Tag)

Layout pattern used in Home predictions section:
```
[36dp emoji/icon] [label bold 11sp #2D1B33] [tag pill right-aligned]
                   [value bold 13sp accent]
                   [detail 10sp #8A7A8F]
```
- Icon area: `36dp × 36dp`, centred vertically
- Label-to-value margin: `2dp`
- Value-to-detail margin: `2dp`
- Tag: `9sp` bold, accent colour, `paddingHorizontal="10dp"`, `paddingVertical="4dp"`, `roundedBg(…, 20f)`
- Card padding: `14dp`

### 6.6 Bottom Nav Bar

Custom `LinearLayout`, height `64dp`, elevation `12dp`, white background.
Each tab is a horizontal `LinearLayout`, height `44dp`, weight `1`, centred:
- Icon: `20dp × 20dp`
- Label: `12sp`, bold, `marginStart="5dp"`, **`visibility="invisible"`** (never GONE — reserves space)
- Active state: icon tinted `#EC4899`, label turns visible with colour `#EC4899`
- Inactive state: icon tinted `#8A7A8F`, label invisible

### 6.7 Onboarding Step Header (`view_step_header.xml`)

- Back arrow: `35dp × 30dp` image, `marginStart="12dp"`, `marginTop="8dp"`
- Step label: `"STEP X OF 8"`, `12sp`, bold, `letterSpacing="0.08"`, `#8A7A8F`, centred horizontally
- Progress bar: `FrameLayout` track `5dp` tall, `marginHorizontal="16dp"`, `marginTop="14dp"`
  - Track fill: `GradientDrawable` `#F5D6E0`
  - Progress fill: gradient `#E63A5E` → `#D63F7A` → `#C2185B`, radius `10dp`

### 6.8 Bottom Sheet Handle

```xml
<!-- drawable/sheet_handle.xml -->
<shape android:shape="rectangle">
    <solid android:color="#E0D8E8" />
    <corners android:radius="4dp" />
</shape>
```
Width: `40dp`, height: `4dp`, centred at top of sheet, `marginTop="12dp"`.

### 6.9 Dividers

```xml
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="#EDE6F0" />
```
Vertical separators between stat card columns: `1dp` wide, `40dp` tall, `#EDE6F0`, centred.

### 6.10 Avatar / Profile Circle

`TextView` with letter initial, programmatic `GradientDrawable` shape `OVAL`:
- Gradient: `#FFD6EA` → `#F0E4F5`, `LEFT_RIGHT`
- Shape: `OVAL`
- Text: initial letter, `22sp` bold, `#2D1B33`

---

## 7. Elevation

Used sparingly:

| Component | Elevation |
|---|---|
| Custom bottom nav bar | `12dp` |
| Splash logo circle | `8dp` |
| Collapsible banner ad | `6dp` |
| All cards | `0dp` (no elevation — shadow replaced by tinted backgrounds) |

---

## 8. Icon Sizes & Navigation Icons

| Icon | Size |
|---|---|
| Bottom nav icons | `20dp × 20dp` |
| Prediction card emoji/icon | `36dp × 36dp` (`textSize="16sp"` for emoji) |
| Legend dot | `8dp × 8dp`, shape `OVAL` |
| Quick action card icon | `28dp × 28dp` |
| Back button (onboarding) | `35dp × 30dp` |
| Sheet close/confirm icons | `24dp × 24dp` |

Icon drawables follow the naming pattern: `ic_nav_home`, `ic_nav_calendar`, `ic_nav_insights`, `ic_nav_profile`, `ic_notification`.

---

## 9. Screen Backgrounds

Every fragment and activity uses one of these backgrounds — never plain white, never a random colour.

| Screen | Background method |
|---|---|
| All fragments (root) | `android:background="#FBF5F8"` in XML |
| Hero / header section | `android:background="#FDF0F5"` in XML |
| White content section | `android:background="#FFFFFF"` in XML |
| Splash | `GradientDrawable TL_BR #F0E6FA→#FDE8F3→#FFFFFF` set in Kotlin |
| Bottom sheets | White (`#FFFFFF`) with handle on top |
| Dialogs | White card, `roundedBg("#FFFFFF", 20f)` |

---

## 10. Edge-to-Edge & Window Insets

Every Activity calls `enableEdgeToEdge()` or `WindowCompat.setDecorFitsSystemWindows(window, false)`.

Every root view applies `ViewCompat.setOnApplyWindowInsetsListener` and sets padding:
```kotlin
v.setPadding(
    systemBars.left,
    systemBars.top,
    systemBars.right,
    systemBars.bottom   // or imeHeight when keyboard visible
)
```
`android:windowTranslucentStatus` and `android:windowTranslucentNavigation` are both `false` in the theme — window decorations are not translucent; we handle insets manually.

---

## 11. Programmatic Helpers (Copy These Exactly)

Two helper functions exist in multiple fragments. Copy them into any new Fragment/Activity that needs backgrounds:

```kotlin
private fun roundedBg(colorHex: String, radiusDp: Float): GradientDrawable {
    val scale = resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.parseColor(colorHex))
        cornerRadius = radiusDp * scale
    }
}

private fun filledCircle(colorHex: String) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(Color.parseColor(colorHex))
}
```

For gradient buttons and banners:
```kotlin
GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#A855F7"))
).apply {
    cornerRadius = 16f * resources.displayMetrics.density
}
```

---

## 12. What NOT to Do

| ❌ Don't | ✅ Do instead |
|---|---|
| Use `CardView` | Use `ConstraintLayout` + `roundedBg()` |
| Use `android:background="#FFFFFF"` on a card in XML | Set card background in Kotlin with `roundedBg()` |
| Use the system `BottomNavigationView` | Use the custom `custom_bottom_nav.xml` layout |
| Use `elevation` on cards | Use tinted backgrounds for depth perception |
| Add a new hex colour | Pick from Section 2 |
| Use `sp` values not in the scale | Pick the nearest size from Section 3.1 |
| Set `visibility="gone"` on nav labels | Use `visibility="invisible"` (reserves space) |
| Use `android:windowTranslucentStatus=true` | Handle insets manually in `OnApplyWindowInsetsListener` |
| Compose | XML Views + ViewBinding |
| Load a custom font | Use system default (Roboto) |
| Use `theme_night` colours beyond the stub | Light mode only for now — dark mode is not implemented |

---

## 13. Known Inconsistencies to Fix (Not Introduce More Of)

These exist in the current code and should be cleaned up, not copied:

- `values-night/themes.xml` still references `Base.Theme.LunaCycle` — rename to `Base.Theme.PeriodTracker`.
- Some screen backgrounds are set via `android:background="#FBF5F8"` in XML (Home) and some via Kotlin `applyBackgrounds()` (Calendar, Insights) — pick one pattern per screen, prefer Kotlin for dynamic state-dependent colours.
- `#4C4C4C` (from `colors.xml` as `card_text` / `hintTextColor`) and `#8A7A8F` are both used as secondary text — standardise on `#8A7A8F` going forward.
- Progress bar track colour `#F5D6E0` in `progress_bar_custom.xml` is slightly different from the chip section tint `#F5EEF8` — these are intentionally different; don't merge them.
