# Space System: a runnable teaching example

This is a small, standalone Java project that rebuilds the core ideas of the space system in
**DMZ: Dawnbreak** (a Minecraft mod), so you can teach them without Minecraft, Forge or any
setup beyond Java.

It goes with the guide in `guide/space-guide.html` (open it in any browser). Each lesson in the
guide has a demo here that prints what the code does, with real numbers.

## Running it

You need **Java 17 or newer** (`java -version` to check).

| System | Every lesson | One lesson |
|---|---|---|
| Windows | `run.bat` | `run.bat 4` |
| Mac / Linux | `./run.sh` | `./run.sh 4` |
| By hand | `javac -d out src/space/*.java` then `java -cp out space.Main` | `java -cp out space.Main 4` |

There are demos for lessons 2 to 9.

## What each lesson shows

| Lesson | Demo | Files to read |
|---|---|---|
| 02 Describing a solar system | The Vegeta system built from records, with defaults filled in from the planet type; units converted at the border | `Body.java`, `Orbit.java`, `Handmade.java`, `data/vegeta.json` |
| 03 Making things orbit | Vegeta and its moon over one 70-minute year, plus a text map of the system | `Orbit.java`, `StarSystem.java` (`bodyPosition`), `AsciiMap.java` |
| 04 An infinite galaxy from one number | A patch of the galaxy grid, proof that two separate computers generate identical systems, one generated system on a map, and the cache | `SystemGenerator.java`, `Galaxy.java`, `Seeds.java` (`mix`), `StarClass.java` |
| 05 Syncing | Server and client agree from one seed. Then the mod's real weakness: the seed it sends can be reversed to the world seed, and the one-way hash that fixes it | `Seeds.java` |
| 06 Planets as places | Planets landing in shared worlds, each in its own slot; giants can't be landed on | `Slots.java`, `Body.java` (`landingDimension`), `BodyType.java` |
| 07 Travel | A player leaves Vegeta, gets pushed off a gas giant, lands on Vampa, bumps into a moon, and lands back home exactly where they left | `Transit.java` |
| 08 Gravity and air | The attribute-modifier maths for gravity and slowdown, and how long a full air bar lasts | `Main.java` (`lesson08`) |
| 09 Drawing the sky | Why compressed distance plus shrinking looks exactly right, and which level of detail each distance gets | `Sky.java` |

Lessons 00, 01, 10 and 11 are reading-only (the big picture, the space dimension's JSON,
asteroid generation, and the recap).

## How this differs from the real mod

It keeps the real algorithms and simplifies everything around them:

- **No Minecraft classes.** `Vec3` and `Random` stand in for Minecraft's `Vec3` and `RandomSource`.
  So the generated systems are similar, but not identical, to the ones the mod makes from the same seed.
- **Hand-made systems are written in code** (`Handmade.java`) instead of JSON read by a Codec, so no
  libraries are needed. `data/vegeta.json` is the mod's real file to compare against. The example
  leaves out atmosphere and gravity on purpose, to show the defaults.
- **No rendering, textures, rings, rotation or ships.** Lesson 09 shows the maths the renderer uses.
- **Travel is a simulation.** `Transit.java` keeps the real rules (exit height, landing order, gas
  giants, cooldown, the queue, the once-a-second nearby list) but moves a pretend player.
- **Slots aren't saved** to disk here; in the mod they are (`SavedData`).

Classroom exercises are in `EXERCISES.md`, with answers in `SOLUTIONS.md`.
