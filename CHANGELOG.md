# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

### Added

- Notes written as text, such as `notes:[tempo=140] C5/8 E5/8 G5+C6/4.`, in the configuration and
  with `SoundDefinition.notes(String)` (#14). They are played as MIDI, with the soundfont if there
  is one.
- `quarkus.sonify.max-sound-length`: how long a sound may last, 2 seconds by default and up to 10
  (#14). A sound that is still playing is not started again, so long sounds do not pile up.
- MIDI: a MIDI file works wherever a sound file does, and `SoundDefinition.midi(Sequence)` defines a
  sound as notes in code (#13). MIDI is played by the synthesizer of the JDK.
- `quarkus.sonify.soundfont`: a SoundFont 2 (`.sf2`) or DLS file whose instruments play the MIDI
  sounds (#13). The extension asks Quarkus to export the internal JDK package this needs.
- Sounds in production and in tests, off by default: an application opts in with a dependency on
  `quarkus-sonify-dev` and the build-time property `quarkus.sonify.production.enabled` or
  `quarkus.sonify.test.enabled`. Either one alone does nothing, and the build logs a warning.
  This also works in a native executable, which plays sounds through the audio library of the JDK:
  `jsound.dll` next to the executable on Windows, and ALSA on Linux.

### Changed

- Quarkus 4: built with Quarkus 4.0.0.Beta3, and needs Java 21 or later. The engine is configured
  at startup by a service (`ServiceRegistrar`) instead of a bytecode recorder:
  `io.quarkiverse.sonify.runtime.dev.SonifyRecorder` is replaced by
  `io.quarkiverse.sonify.runtime.dev.SonifyStartup`.

## 0.1.0 - 2026-11-10

### Added

- A sound for every HTTP request, by method, and for every response, by status code: the class of
  the status picks the kind of sound and the code picks its pitch. A three-note chime marks the
  start of the application and every live reload.
- Custom sounds by name, with `@PlaySound` on a method or a class of a CDI bean, and with the
  injectable `SoundPlayer`.
- Configuration: `quarkus.sonify.enabled`, `quarkus.sonify.volume`, `quarkus.sonify.response-gap`
  and `quarkus.sonify.theme` (`taps` or `bells`).
- Any sound can be replaced with a WAV file or turned `off`, per HTTP method
  (`quarkus.sonify.method.*`), per status code or class (`quarkus.sonify.status.*`) and per custom
  sound (`quarkus.sonify.sound.*`).
- `SonifyCustomizer` beans define sounds in code, from synthesized tones, WAV files or computed
  samples. A property for the same sound wins over the customizer.
- Dev mode only: everything that makes a sound lives in `quarkus-sonify-dev`, a conditional dev
  dependency that is never part of a production application, where the API does nothing.
