# Quarkus Sonify

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.sonify/quarkus-sonify?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.sonify/quarkus-sonify-parent) <!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->[![All Contributors](https://img.shields.io/badge/all_contributors-1-orange.svg?style=flat-square)](#contributors-)<!-- ALL-CONTRIBUTORS-BADGE:END -->

Hear what your Quarkus application is doing in dev mode.

Sonify plays a short sound on the machine that runs the application: one when an HTTP request arrives (by method), one
when its response is sent (by status code), and any custom sound you ask for.

**Dev mode by default.** Everything that makes a sound lives in a dev-only artifact that is not part of a production
application. In production and in tests the API does nothing, unless the application
[opts in](#production-and-tests).

> **This is just for fun.** It may well have no serious use case at all. Nobody should feel productive
> because of it.

## Installation

Add the extension to your Quarkus application. With Maven, add the following dependency to
your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.sonify</groupId>
    <artifactId>quarkus-sonify</artifactId>
    <version>${quarkus-sonify.version}</version>
</dependency>
```

With Gradle, add to your `build.gradle`:

```groovy
implementation("io.quarkiverse.sonify:quarkus-sonify:${quarkusSonifyVersion}")
```

or `build.gradle.kts`:

```kotlin
implementation("io.quarkiverse.sonify:quarkus-sonify:$quarkusSonifyVersion")
```

Replace the version placeholder with the latest release from
[Maven Central](https://central.sonatype.com/artifact/io.quarkiverse.sonify/quarkus-sonify).

There is nothing to remove before going to production: by default, the extension only makes sounds in dev mode.

## Run the demo

Requirements: JDK 21+, Maven 3.9+, and speakers or headphones on the machine that runs the application.

1. Build the extension and the demo:

   ```sh
   mvn install -DskipTests
   ```

2. Start the demo in dev mode. You should hear a three-note chime when it is up:

   ```sh
   mvn -f demo/pom.xml quarkus:dev
   ```

3. Open http://localhost:8080/. The page has a link for every HTTP method against every status code, and for the
   custom sounds. Each click sends one request, and the application plays the sounds.

4. Edit `demo/src/main/resources/application.properties` (try `quarkus.sonify.theme=bells`) or
   `DemoSounds.java`, then click a link again: the change is heard after the live reload.

Press `q` in the terminal to stop. From the command line, any request works too:

```sh
curl -X DELETE http://localhost:8080/status/404
```

## What you hear

Out of the box:

| Event | Sound |
|---|---|
| Request | a motif of taps per HTTP method (GET one tap, POST two rising, PUT two equal, PATCH three, DELETE two falling) |
| Response | the status class picks the kind of sound (2xx clear, 3xx gliding, 4xx hollow, 5xx thud), the code picks its pitch |
| Start and live reload | a three-note chime |

No audio device means one log line and silence. No admin rights are needed.

## Configuration

```properties
quarkus.sonify.enabled=true
quarkus.sonify.volume=0.4
quarkus.sonify.response-gap=350ms
# taps or bells
quarkus.sonify.theme=taps

# a sound file (WAV or MIDI) or "off", per HTTP method, per status code or class, and per custom sound
quarkus.sonify.method.options=off
quarkus.sonify.status."418"=sounds/whistle.wav
quarkus.sonify.status."202"=sounds/arpeggio.mid
quarkus.sonify.status."5xx"=off
quarkus.sonify.sound."order-placed"=sounds/coin.wav

# the instruments of the MIDI sounds: a SoundFont 2 (.sf2) or DLS file
quarkus.sonify.soundfont=sounds/piano.sf2
```

Files are looked up in the resources of the application first, then on the file system, and are cut at two seconds.
The configuration is read again on every live reload.

## Custom sounds

On a method of a CDI bean, or on a bean class:

```java
@PlaySound("order-placed")
public int place() { ... }
```

From anywhere in code:

```java
@Inject
SoundPlayer soundPlayer;

soundPlayer.play("audit");
```

A name without configuration gets a short sound of its own, derived from the name.

## Sounds defined in code

```java
@ApplicationScoped
public class MySounds implements SonifyCustomizer {

    @Override
    public void customize(SoundRegistry sounds) {
        // wave, frequency in hertz, start in ms, duration in ms
        sounds.sound("shipped")
                .tone(Wave.SINE, 392, 0, 150)
                .tone(Wave.SINE, 523, 120, 300);
        sounds.status(404).off();
        sounds.statusClass(5).file("sounds/alarm.wav");
        sounds.method("POST").samples(myPcm, 44100);
        // notes, as a javax.sound.midi.Sequence
        sounds.status(204).midi(chord);
    }
}
```

A `quarkus.sonify.*` property for the same sound wins over the customizer.

MIDI is played by the synthesizer of the JDK, with its default instruments or the ones of
`quarkus.sonify.soundfont`. See [MIDI](docs/modules/ROOT/pages/custom-sounds.adoc#midi).

## Production and tests

An application that runs with someone in front of it, such as a desktop application or a kiosk, can make sounds in
production too. It takes two things, and either one alone does nothing:

```xml
<dependency>
    <groupId>io.quarkiverse.sonify</groupId>
    <artifactId>quarkus-sonify-dev</artifactId>
    <version>${quarkus-sonify.version}</version>
</dependency>
```

```properties
quarkus.sonify.production.enabled=true
# and, for the tests of the application
quarkus.sonify.test.enabled=true
```

This works in a native executable too. On Windows, ship `jsound.dll` next to the executable (the build puts it there),
and on Linux, the machine needs ALSA (`libasound.so.2`). See the
[documentation](docs/modules/ROOT/pages/outside-dev-mode.adoc).

## Modules

| Module | Artifact | Content |
|---|---|---|
| `runtime` | `quarkus-sonify` | the API and the configuration, no audio code |
| `runtime-dev` | `quarkus-sonify-dev` | the synthesizer and its hooks, a conditional dev dependency |
| `deployment` | `quarkus-sonify-deployment` | the build steps |
| `integration-tests` | `quarkus-sonify-integration-tests` | checks that a packaged application is silent and has no audio code |
| `integration-tests-production` | `quarkus-sonify-integration-tests-production` | checks that a packaged application that opts in makes sounds, native included |
| `demo` | `quarkus-sonify-demo` | a page with a link for every case |
| `docs` | `quarkus-sonify-docs` | the documentation |

## Documentation

The documentation is in [`docs/`](docs/modules/ROOT/pages/index.adoc): getting started, custom sounds, production and
tests, and the configuration reference.

## Tests

```sh
mvn install
```

The tests are silent and need no audio output: they check that every event reaches the synthesizer in dev mode and in
an application that opts in, and that nothing does anywhere else. What the sounds actually sound like is not tested.

## Status

Experimental. The sounds are tuned by guesswork.

## License

[Apache License 2.0](LICENSE)

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/en/reference/emoji-key/)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://fouad.io"><img src="https://avatars.githubusercontent.com/u/1194488?v=4&size=100" width="100px" height="100px" alt="Fouad Almalki"/><br /><sub><b>Fouad Almalki</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-sonify/commits?author=Eng-Fouad" title="Code">💻</a> <a href="#maintenance-Eng-Fouad" title="Maintenance">🚧</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!