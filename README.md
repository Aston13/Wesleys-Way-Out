# Wesley's Way Out

<img width="500" height="500" alt="wesley-pixel" src="https://github.com/user-attachments/assets/13775c56-f662-4f57-90a5-334c4ac140c8" />

A Java maze game built with Swing/AWT where you guide Wesley the dog through procedurally generated mazes, collecting keys to unlock the exit before time runs out.

**[▶ Play in Browser](https://Aston13.github.io/Wesleys-Way-Out/)** — no download or Java install required (powered by CheerpJ)

## Gameplay

- Arrow keys or **WASD** to move (N/E/S/W)
- Collect all keys to unlock the exit door
- Keys disappear over time — if too many are lost, the level fails
- 30 progressively larger levels with best-time tracking
- Press **Space** to continue, **Esc** to pause/return to menu
- While paused: **R** to restart, **Space/Esc** to resume

## Screenshots

*650×650 window with a top-down maze, animated dog sprite, and key collectibles.*

## Prerequisites

- **Java 21+** — if not installed locally, Gradle will auto-download it via the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains)
- No Gradle installation needed (the included Gradle Wrapper handles it)

## Quick Start

```bash
cd MazeGame

# Build & test
./gradlew build        # Linux/macOS
.\gradlew.bat build    # Windows

# Run the game
./gradlew run          # Linux/macOS
.\gradlew.bat run      # Windows
```

## Build Commands

| Command | Description |
|---------|-------------|
| `gradlew build` | Compile, test, and package into a JAR |
| `gradlew run` | Launch the game |
| `gradlew test` | Run JUnit 5 tests only |
| `gradlew jar` | Build the JAR (output: `build/libs/MazeGame-<version>.jar`) |
| `gradlew browserJar` | Build Java-8 JAR for CheerpJ browser play |
| `gradlew runBrowser` | Build browser JAR, copy to `docs/`, and start local HTTP server |
| `gradlew jpackage` | Create a native app image with bundled JRE (no Java needed to run) |
| `gradlew clean` | Delete all build artifacts |

## Running the JAR Directly

```bash
java -jar MazeGame/build/libs/MazeGame-<version>.jar
```

## Project Structure

```
MazeGame/
├── build.gradle                      # Gradle build config (Java 21, JUnit 5)
├── settings.gradle                   # Project name + Foojay toolchain resolver
├── gradlew / gradlew.bat             # Gradle wrapper scripts
├── src/
│   ├── main/java/mazegame/
│   │   ├── Start.java                # Entry point
│   │   ├── MazeGame.java             # JFrame owner, pause screen
│   │   ├── GameLoop.java             # Fixed-timestep game loop
│   │   ├── GamePanel.java            # Canvas JPanel for rendering
│   │   ├── InputHandler.java         # Keyboard & mouse input
│   │   ├── Renderer.java             # Maze/player rendering, HUD, collision
│   │   ├── Player.java               # Player position & movement state
│   │   ├── RecursiveBacktracker.java  # Maze generation algorithm
│   │   ├── Tilemap.java              # Grid data structure
│   │   ├── Tile.java                 # Tile interface
│   │   ├── TileWall.java             # Impassable wall tile
│   │   ├── TilePassage.java          # Passable tile (can hold key items)
│   │   ├── TileExit.java             # Exit tile (lockable/unlockable)
│   │   ├── AssetManager.java         # Image loading & level data I/O
│   │   ├── AudioManager.java         # Synthesised sound effects
│   │   ├── GameSettings.java         # User preferences (skin, mute)
│   │   ├── MenuManager.java          # Screen navigation (menu/results)
│   │   ├── MainMenuPanel.java        # Custom-painted main menu
│   │   ├── LevelSelectionPanel.java   # Level selection grid
│   │   ├── SettingsPanel.java        # Skin & audio settings
│   │   ├── ResultOverlayPanel.java   # Game-over / level-complete overlay
│   │   └── UI.java                   # Swing component factory
│   ├── main/resources/mazegame/Assets/
│   │   ├── data/                     # LevelData.txt, ResetData.txt
│   │   ├── items/keys/               # Key animation frames (20 PNGs)
│   │   ├── skins/wesley/             # Wesley sprite frames
│   │   ├── skins/sasso/              # Sasso sprite frames
│   │   ├── tiles/walls/              # 16 wall variants (NESW combos)
│   │   ├── tiles/passages/           # 4 grass passage variants
│   │   ├── tiles/exits/              # Locked & unlocked exit sprites
│   │   └── ui/                       # wesley-pixel.png (menu decoration)
│   └── test/java/mazegame/
│       ├── AssetManagerTest.java
│       ├── MazeGameStateTest.java
│       ├── PlayerTest.java
│       ├── RecursiveBacktrackerTest.java
│       ├── TileTest.java
│       └── TilemapTest.java
docs/                                  # GitHub Pages (CheerpJ browser player)
```

## How the Maze Works

The maze is generated using a **recursive backtracker** algorithm:

1. Start at a random odd-coordinate cell
2. Randomly choose an unvisited neighbour, carve a passage to it
3. Recurse from the new cell; backtrack when stuck
4. The exit is placed at the furthest reachable point from the start
5. Keys are scattered randomly across passage tiles

Each level increases the grid size by 2 (starting at 11×11), making mazes progressively harder.

## Tech Stack

- **Java 21** (LTS)
- **Swing / AWT** — windowing, rendering, input
- **Gradle 9.3.1** — build system
- **JUnit 5** — testing

## Development

### IDE Setup

The repo includes a `.vscode/` workspace configuration. Open the `MazeGame/` folder (or the repo root) in VS Code with the [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) and [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) extensions installed. Recommended extensions are listed in `.vscode/extensions.json`.

An `.editorconfig` file enforces consistent formatting (4-space indent for Java, 2-space for JSON/YAML/HTML, UTF-8, LF line endings, trim trailing whitespace).

### Building & Testing

```bash
cd MazeGame

# Full build (compile + test + JAR)
./gradlew build

# Run tests only
./gradlew test

# Launch the desktop game
./gradlew run
```

### Native Installer (jpackage)

Create a standalone desktop app that bundles a private JRE — recipients **don't need Java installed**.

```bash
# Build an app image (folder with .exe / .app)
./gradlew jpackage
# Output: build/installers/WesleysWayOut/

# Build a Windows MSI installer (requires WiX Toolset 3.x)
./gradlew jpackage -PinstallerType=msi
```

The task recompiles sources automatically, so the output always reflects the latest code.

### Browser Build (CheerpJ)

The game runs in the browser via [CheerpJ 3.0](https://cheerpj.com/), which requires Java 8 bytecode.

```bash
# Build the Java-8-compatible JAR for CheerpJ
./gradlew browserJar
# Output: build/libs/MazeGame-browser-<version>.jar

# One-step local testing: builds JAR, copies to docs/, starts HTTP server
./gradlew runBrowser
# Open http://localhost:8080 in your browser (Ctrl+C to stop)
```

### Releasing

Releases are automated via GitHub Actions. Push a version tag to trigger the workflow:

```bash
git tag v1.2.0
git push origin v1.2.0
```

The workflow (`.github/workflows/release.yml`) will:
1. Run all tests (`./gradlew test`)
2. Build the desktop JAR and browser JAR
3. Update the GitHub Pages JAR in `docs/`
4. Create a GitHub Release with both JARs attached

### Source Layout

| Path | Content |
|------|--------|
| `MazeGame/src/main/java/mazegame/` | Java sources |
| `MazeGame/src/main/resources/mazegame/Assets/` | Sprites, animation frames, level data (loaded via classpath) |
| `MazeGame/src/test/java/mazegame/` | JUnit 5 tests |
| `docs/` | GitHub Pages site (CheerpJ browser player) |

## License

All Rights Reserved. See [LICENSE](LICENSE) for details.
