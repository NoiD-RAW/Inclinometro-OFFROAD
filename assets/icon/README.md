# App icons

The icon uses an orange background that reaches every edge. Do not add a gray
surround, metallic border, or baked corner mask; the operating system and stores
apply their own masks.

- `ios-1024.png`: opaque 1024 × 1024 iOS source.
- `android-legacy.png`: 192 × 192 Android fallback.
- `android-fg.png` and `android-bg.png`: matching 432 × 432 adaptive layers;
  keep essential lettering inside the central 264 × 264 safe area.
- `../../store/google-play/icono-512.png`: 512 × 512 Google Play listing icon.

The orange artwork was edited with the built-in image generation tool. The final
edit prompt was:

> Remove the entire metallic border, black beveled rim, and exterior corners.
> Continue the existing orange texture to all four square edges and corners.
> Preserve the van, mountains, road, typography, and text “INCLINÓMETRO / OFFROAD”.
> No frame, margins, transparency, or baked rounding; the operating system applies
> the corner shape.

Platform exports use deterministic resizing and PNG encoding. Check both circular
and rounded-square Android masks whenever changing the adaptive layers.
