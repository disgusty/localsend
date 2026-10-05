# AOSP-derived resources in OldySend

The files listed below were copied unmodified (apart from file renaming, and the
generated mdpi fallbacks noted at the end) from the Android Open Source Project
(https://source.android.com), repository `platform/frameworks/base` on
https://android.googlesource.com.

Copyright (C) The Android Open Source Project.
Licensed under the Apache License, Version 2.0 (the "License"); you may not use these
files except in compliance with the License. You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed
to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

## Classic (Android 2.3.7) - tag `android-2.3.7_r1`

- `core/res/res/drawable-mdpi/*.png` -> `app/src/main/res/drawable/cl_<name>`
- `core/res/res/drawable-hdpi/*.png` -> `app/src/main/res/drawable-hdpi/cl_<name>`
- `core/res/res/values/{colors,dimens,styles,themes}.xml`,
  `core/res/res/drawable/*.xml` (selected), `core/res/res/color/*.xml`,
  `core/res/res/layout/*.xml` (selected) -> `third_party/aosp-ref/classic-2.3.7/`
- `data/fonts/DroidSans.ttf`, `data/fonts/DroidSans-Bold.ttf` -> `app/src/main/assets/fonts/`

## Holo (Android 4.4.4) - tag `android-4.4.4_r2.0.1`

- `core/res/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi}/*.png` ->
  `app/src/main/res/drawable{,-hdpi,-xhdpi,-xxhdpi}/ho_<name>`
- `core/res/res/values/{colors,colors_holo,dimens,styles,themes}.xml`,
  `core/res/res/drawable/*.xml` (selected), `core/res/res/color/*.xml`,
  `core/res/res/layout/*.xml` (selected) -> `third_party/aosp-ref/holo-4.4.4/`
- `data/fonts/Roboto-Regular.ttf`, `data/fonts/Roboto-Bold.ttf` -> `app/src/main/assets/fonts/`

## Roboto Medium

- `platform/frameworks/base`, tag `android-5.0.0_r1`, `data/fonts/Roboto-Medium.ttf`
  -> `app/src/main/assets/fonts/Roboto-Medium.ttf`

All fonts (Droid Sans, Roboto) are distributed by AOSP under the Apache License 2.0.

## Modifications

- Files were renamed with a `cl_` / `ho_` prefix.
- Where a drawable had no mdpi original, a fallback was placed in the unqualified
  `drawable/` folder. All such files existed only in drawable-xxhdpi: 9-patches are
  unchanged copies of the xxhdpi file; plain PNGs were downscaled from xxhdpi
  (factor 1/3) with Pillow (LANCZOS).

## Noto Sans Syriac

`app/src/main/assets/fonts/NotoSansSyriac-Regular.ttf` — Noto Sans Syriac by Google (notofonts project),
SIL Open Font License 1.1 (https://openfontlicense.org). Used for the Aramaic translation on Android versions
whose system has no Syriac font. Unmodified.
