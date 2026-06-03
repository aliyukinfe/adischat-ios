# AdisChat — Habesha Emoji Pack: Asset & Tech Spec

A custom, uniquely-Ethiopian emoji/sticker system that sets AdisChat apart.
This doc is the single source of truth for **designers** (what to draw, sizes,
formats) and **developers** (how it slots into the app).

---

## 1. Asset formats & sizes

| Type | Format | Canvas | Export | Notes |
|------|--------|--------|--------|-------|
| Static emoji | **PNG** (also keep source SVG) | 512×512 master | 128×128 @1x, 192 @1.5x, 256 @2x, 384 @3x, 512 @4x | Transparent background |
| Static emoji (vector) | **SVG** | 512×512 viewBox | single file | Preferred — scales crisply, smaller APK |
| Animated emoji | **Lottie JSON** | 512×512 | one `.json` | 1.5–2.5s loop, <150 KB, 30–60 fps |
| Sticker (large) | PNG/Lottie | 1024×1024 | 512 display | For full-size sticker sends |

**Safe area:** keep art within the central **88%** (≈56px padding on a 512 master)
so nothing clips when rendered in a circle or small grid cell.

**Style guide:** rounded, friendly, flat-with-soft-shadow; consistent stroke
weight; AdisChat green `#18C964` as the recurring accent; Ethiopian flag
colors (green `#0F9D58`, yellow `#FCDD09`, red `#DA1212`) used tastefully.

---

## 2. Naming convention

All lowercase, `snake_case`, prefixed by pack and category. Android drawable
names **must** be lowercase letters/digits/underscores only.

```
hab_<category>_<name>          // static, e.g. hab_face_smile
hab_<category>_<name>_anim      // animated (Lottie), e.g. hab_culture_jebena_pour_anim
```

Categories: `face`, `expr` (Ethiopian expressions), `culture`, `food`,
`fashion`, `addis`, `mascot`.

---

## 3. Pack 1 — Launch set (12 items, already scaffolded in the app)

These map 1:1 to the `HabeshaPack` list in `ChatScreen.kt`. Filenames below are
what to ship; set each `drawableRes`/`lottie` once delivered.

| # | Label | Drawable name | Animated? | Brief |
|---|-------|---------------|-----------|-------|
| 1 | Smile | `hab_face_smile` | static | Curly-hair Habesha face, warm smile |
| 2 | Laugh | `hab_face_laugh` | static | Head-back laugh, joyful |
| 3 | Love | `hab_face_love` | static | Heart eyes, Ethiopian features |
| 4 | Thinking | `hab_face_thinking` | static | Hand on chin |
| 5 | Ayzoh | `hab_expr_ayzoh` | static | Comforting hug gesture ("it's okay") |
| 6 | Gobez | `hab_expr_gobez` | static | Flexed arm / thumbs-up ("well done!") |
| 7 | Eshi | `hab_expr_eshi` | static | Approving thumbs-up ("okay/agreed") |
| 8 | Injera | `hab_food_injera` | static | Rolled injera |
| 9 | Jebena Coffee | `hab_culture_jebena` | static | Traditional jebena + cup |
| 10 | Habesha Kemis | `hab_fashion_kemis` | static | Woman in white habesha kemis |
| 11 | Eskista | `hab_culture_eskista_anim` | **Lottie** | Shoulder-dance loop |
| 12 | Adis Lion | `hab_mascot_lion` | static (anim optional) | AdisChat lion mascot |

---

## 4. Pack 2+ — Full roadmap (produce after Pack 1)

**Faces & Reactions:** `hab_face_wink`, `hab_face_cry`, `hab_face_angry`,
`hab_face_cool`, `hab_face_blessing`, `hab_face_respect`.

**Ethiopian expressions:** `hab_expr_betam_konjo`, `hab_expr_yene_fikir`,
`hab_expr_abeg`, `hab_expr_woy_gud`, `hab_expr_endet_neh`.

**Culture:** `hab_culture_mesob`, `hab_culture_dabo`, `hab_culture_berbere`,
`hab_culture_tej`, `hab_culture_rekebot`.

**Food:** `hab_food_doro_wat`, `hab_food_kitfo`, `hab_food_tibs`,
`hab_food_shiro`, `hab_food_sambusa`, `hab_food_buna`, `hab_food_corn`.

**Fashion:** `hab_fashion_shamma_man`, `hab_fashion_wedding`,
`hab_fashion_graduation`, `hab_fashion_addis_youth`.

**Addis life:** `hab_addis_bajaj`, `hab_addis_taxi`, `hab_addis_skyline`,
`hab_addis_merkato`, `hab_addis_friendship_park`, `hab_addis_unity_lion`.

**Mascots:** `hab_mascot_lion`, `hab_mascot_qr`, `hab_mascot_wallet`,
`hab_mascot_bubble`, `hab_mascot_ai`.

**Animated premium (Lottie):** `hab_culture_jebena_pour_anim`,
`hab_food_injera_roll_anim`, `hab_culture_eskista_anim`,
`hab_mascot_lion_roar_anim`, `hab_expr_flag_heart_anim`,
`hab_addis_taxi_honk_anim`, `hab_mascot_wallet_receive_anim`,
`hab_mascot_qr_scan_anim`.

---

## 5. Where assets live in the project

```
app/src/main/res/drawable/        ← static PNG/SVG (vector drawables)
app/src/main/assets/habesha/      ← Lottie .json files
```

Static SVGs: import via Android Studio → New → Vector Asset (becomes a
`drawable` resource referenced as `R.drawable.hab_face_smile`).

---

## 6. Dev integration (Compose)

### 6.1 Model (already in `ChatScreen.kt`)
```kotlin
private data class HabeshaEmoji(
    val label: String,
    val glyph: String,            // Unicode fallback (used until art ships)
    val drawableRes: Int? = null, // e.g. R.drawable.hab_face_smile
    val lottieAsset: String? = null, // e.g. "habesha/hab_culture_eskista_anim.json"
)
```

### 6.2 Render rule in `EmojiBar`
Replace the placeholder `Text(e.glyph)` with:
```kotlin
when {
    e.lottieAsset != null -> {
        val comp by rememberLottieComposition(LottieCompositionSpec.Asset(e.lottieAsset))
        LottieAnimation(comp, iterations = LottieConstants.IterateForever, modifier = Modifier.size(34.dp))
    }
    e.drawableRes != null ->
        Image(painterResource(e.drawableRes), e.label, modifier = Modifier.size(34.dp))
    else ->
        Text(e.glyph, fontSize = 30.sp)   // fallback
}
```

### 6.3 Lottie dependency (only needed for animated)
`build.gradle` (app):
```
implementation "com.airbnb.android:lottie-compose:6.4.0"
```

### 6.4 Sending in messages
Two options:
- **Simple (now):** inserting the Unicode `glyph` into the text — works
  everywhere, zero backend change.
- **Rich (later):** send a shortcode token like `:hab_face_smile:` in the
  message text; render it as the custom asset when displaying a message.
  This needs a small lookup map (shortcode → asset) on the client and the
  message renderer to substitute tokens. No DB schema change required — the
  token rides inside the existing message `text` field.

---

## 7. Recommended delivery order
1. Pack 1 static (items 1–10, 12) as **SVG** — smallest, scales best.
2. Pack 1 Eskista as **Lottie**.
3. Then Pack 2 categories in the order above.

Deliver each batch as a zip following the naming convention; drop static into
`res/drawable`, Lottie into `assets/habesha/`, fill in `drawableRes`/`lottieAsset`
on the matching `HabeshaEmoji`, and the picker lights up automatically.
