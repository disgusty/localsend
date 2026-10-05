## OldySend 1.0.0

OldySend is a fork of [LocalSend](https://github.com/localsend/localsend) that runs on **every Android version from 1.0 to 16**.
It speaks LocalSend protocol v2.2, so it sends files and messages to and from the official LocalSend apps on Android, iOS,
Windows, macOS and Linux, without internet access or an account.

### Download

- **`OldySend-1.0.0.apk`**: install this one. One APK for all Android versions.
- `OldySend-1.0.0-debug.apk`: debug build, for bug reports only.
- `SHA256SUMS.txt`: checksums of both files.

The APK is signed by **CN=disgusty**. Certificate SHA-256:
`89:F9:A9:2D:27:08:13:A5:2E:A0:60:1E:C5:D4:35:26:A0:A1:18:1C:AA:4C:3E:7E:24:C8:E7:E1:21:37:3A:FE`

To install, allow "Unknown sources" (Android 1.0–7.1) or "Install unknown apps" (Android 8+).

### Features

- Everything the LocalSend mobile app does: discovery, favorites, sending files, folders, text, apps and the clipboard,
  one or several recipients, share/receive via link (with QR code), PIN, quick save, history, device verification.
- Four interface styles, switchable in the settings: **Classic** (Android 1.0–2.3), **Holo** (3.0–4.4),
  **Material Design** (5–11) and **Material You** (12+, with system colors). The app icon follows the style.
- All LocalSend languages, plus Aramaic, LOLCAT and Cute Engwish.

### Good to know

- **Encryption:** Android 5.0+ uses HTTPS like LocalSend. Android 1.0–4.4 cannot use LocalSend's encryption: these devices receive
  from everyone, but to send *to* another device, turn off "Encryption" in that device's LocalSend settings.
- On Android 1.0–1.5 multicast is often blocked; use the "Search devices" button if a device does not appear.
- Tested on Android 2.3, 4.2, 4.4, 5.0 and 15 (emulators). Android 1.0–1.6 was not tested on a real device.
  Reports are welcome.
- The Aramaic translation is new; corrections by native speakers are welcome.

---

## OldySend 1.0.0 (по-русски)

OldySend — форк [LocalSend](https://github.com/localsend/localsend), который работает на **всех версиях Android с 1.0 по 16**.
Он говорит на протоколе LocalSend v2.2 и обменивается файлами и сообщениями с официальным LocalSend на Android, iOS,
Windows, macOS и Linux. Интернет и учётная запись не нужны.

### Что скачать

- **`OldySend-1.0.0.apk`** — устанавливать этот файл. Один APK для всех версий Android.
- `OldySend-1.0.0-debug.apk` — отладочная сборка, только для сообщений об ошибках.
- `SHA256SUMS.txt` — контрольные суммы.

Подпись: **CN=disgusty**, SHA-256 сертификата указан выше.

Для установки разрешите «Неизвестные источники» (Android 1.0–7.1) или «Установку неизвестных приложений» (Android 8+).

### Возможности

- Всё, что умеет мобильный LocalSend: поиск устройств, избранное, отправка файлов, папок, текста, приложений и буфера обмена,
  одному или нескольким получателям, отправка и приём по ссылке (с QR-кодом), PIN, быстрое сохранение, история,
  проверка устройства.
- Четыре стиля интерфейса, переключаются в настройках: **Классический** (Android 1.0–2.3), **Holo** (3.0–4.4),
  **Material Design** (5–11) и **Material You** (12+, с системными цветами). Значок приложения меняется вместе со стилем.
- Все языки LocalSend, а также арамейский, LOLCAT и Cute Engwish.

### Важно знать

- **Шифрование:** на Android 5.0+ используется HTTPS, как в LocalSend. Android 1.0–4.4 не поддерживает шифрование LocalSend:
  такие устройства принимают файлы от всех, а чтобы отправить файл *на* другое устройство, выключите на нём «Шифрование»
  в настройках LocalSend.
- На Android 1.0–1.5 multicast часто заблокирован: если устройство не появляется, нажмите «Поиск устройств».
- Проверено на Android 2.3, 4.2, 4.4, 5.0 и 15 (эмуляторы). На Android 1.0–1.6 на реальном устройстве не проверялось —
  будем рады отчётам.
- Арамейский перевод новый; исправления от носителей языка приветствуются.
