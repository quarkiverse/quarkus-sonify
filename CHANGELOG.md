# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

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
