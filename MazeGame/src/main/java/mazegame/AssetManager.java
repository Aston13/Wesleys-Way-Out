package mazegame;

import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;
import javax.swing.Timer;

/**
 * Manages loading, caching, and retrieval of all game assets including images, sprite animation
 * frames, and level data persistence.
 */
public class AssetManager {

  private static final String LEVEL_DATA_RESOURCE = "Assets/data/LevelData.txt";
  private static final String RESET_DATA_RESOURCE = "Assets/data/ResetData.txt";
  private static final String LEVEL_DATA_FILE = "LevelData.txt";

  private static final int KEY_FRAME_COUNT = 20;
  private static final int DOG_EAST_FRAME_COUNT = 7;
  private static final int DOG_WEST_FRAME_COUNT = 7;
  private static final int DOG_NORTH_FRAME_COUNT = 6;
  private static final int DOG_SOUTH_FRAME_COUNT = 6;
  private static final int GRASS_VARIANT_COUNT = 4;
  private static final int ANIMATION_TICK_MS = 100;

  private final HashMap<String, BufferedImage> preloadedImages = new HashMap<>();
  private final Timer animationTimer;
  private int keyFrameIndex;

  /** Creates a new AssetManager and starts the key animation timer. */
  public AssetManager() {
    Timer timer = new Timer(ANIMATION_TICK_MS, null);
    timer.addActionListener(
        (ActionEvent e) -> {
          keyFrameIndex++;
          if (keyFrameIndex >= KEY_FRAME_COUNT) {
            keyFrameIndex = 0;
            timer.restart();
          }
        });
    this.animationTimer = timer;
    animationTimer.start();
  }

  /**
   * Saves level progress data to an external file. In CheerpJ (browser) this silently fails —
   * progress is not persisted.
   *
   * @param lines array of level data lines to write
   * @throws IOException if writing fails on desktop
   */
  public void saveLevelData(String[] lines) throws IOException {
    try (BufferedWriter writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(LEVEL_DATA_FILE)))) {
      for (String line : lines) {
        writer.write(line);
        writer.newLine();
      }
    } catch (Exception e) {
      System.out.println("Save not supported in this environment.");
    }
  }

  /**
   * Loads level data, preferring an external save file over the classpath resource.
   *
   * @param reset if true, loads the default reset data instead of saved progress
   * @return array of level data lines
   * @throws IOException if the resource cannot be found or read
   */
  public String[] loadLevelData(boolean reset) throws IOException {
    if (!reset) {
      File external = new File(LEVEL_DATA_FILE);
      if (external.exists()) {
        try {
          return readLines(new FileInputStream(external));
        } catch (Exception e) {
          // Fall through to classpath resource
        }
      }
    }

    String resource = reset ? RESET_DATA_RESOURCE : LEVEL_DATA_RESOURCE;
    InputStream is = getClass().getResourceAsStream(resource);
    if (is == null) {
      throw new IOException("Resource not found: " + resource);
    }
    return readLines(is);
  }

  private String[] readLines(InputStream is) throws IOException {
    ArrayList<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    }
    return lines.toArray(new String[0]);
  }

  /**
   * Returns a preloaded image by its cache key.
   *
   * @param key the image cache key
   * @return the cached image, or {@code null} if not found
   */
  public BufferedImage getPreloadedImage(String key) {
    return preloadedImages.get(key);
  }

  /**
   * Returns the current key animation frame based on the internal timer.
   *
   * @return the current key frame image
   */
  public BufferedImage getKeyFrame() {
    if (keyFrameIndex >= KEY_FRAME_COUNT) {
      keyFrameIndex = 0;
    }
    return preloadedImages.get("Key_" + keyFrameIndex);
  }

  /**
   * Returns the current key animation frame on even ticks (blinking effect), or {@code null} on odd
   * ticks.
   *
   * @return the key frame image on even ticks, {@code null} on odd ticks
   */
  public BufferedImage getBlinkingKeyFrame() {
    if (keyFrameIndex >= KEY_FRAME_COUNT) {
      keyFrameIndex = 0;
    }
    if (keyFrameIndex % 2 == 0) {
      return preloadedImages.get("Key_" + keyFrameIndex);
    }
    return null;
  }

  /**
   * Preloads all game images into the cache. Must be called before rendering.
   *
   * @throws IOException if any image resource cannot be loaded
   */
  public void preloadImages() throws IOException {
    ImageIO.setUseCache(false);

    // Grass passage variants
    for (int i = 0; i < GRASS_VARIANT_COUNT; i++) {
      loadImage("GrassPassage_" + i, "Assets/tiles/passages/GrassPassage_" + i + ".png");
    }

    // Exit tiles
    loadImage("Locked Exit", "Assets/tiles/exits/ExitLocked.png");
    loadImage("Open Exit", "Assets/tiles/exits/ExitUnlocked.png");

    // Key animation frames
    for (int i = 0; i < KEY_FRAME_COUNT; i++) {
      loadImage("Key_" + i, "Assets/items/keys/Key_" + i + ".png");
    }

    // Dog animation frames (Sasso skin — original)
    for (int i = 0; i < DOG_NORTH_FRAME_COUNT; i++) {
      loadImage("sassoNorth" + i, "Assets/skins/sasso/north_" + i + ".png");
    }
    for (int i = 0; i < DOG_EAST_FRAME_COUNT; i++) {
      loadImage("sassoEast" + i, "Assets/skins/sasso/right_" + i + ".png");
    }
    for (int i = 0; i < DOG_SOUTH_FRAME_COUNT; i++) {
      loadImage("sassoSouth" + i, "Assets/skins/sasso/south_" + i + ".png");
    }
    for (int i = 0; i < DOG_WEST_FRAME_COUNT; i++) {
      loadImage("sassoWest" + i, "Assets/skins/sasso/left_" + i + ".png");
    }

    // Dog animation frames (Wesley skin — new default)
    for (int i = 0; i < DOG_NORTH_FRAME_COUNT; i++) {
      loadImage("wesleyNorth" + i, "Assets/skins/wesley/north_" + i + ".png");
    }
    for (int i = 0; i < DOG_EAST_FRAME_COUNT; i++) {
      loadImage("wesleyEast" + i, "Assets/skins/wesley/right_" + i + ".png");
    }
    for (int i = 0; i < DOG_SOUTH_FRAME_COUNT; i++) {
      loadImage("wesleySouth" + i, "Assets/skins/wesley/south_" + i + ".png");
    }
    for (int i = 0; i < DOG_WEST_FRAME_COUNT; i++) {
      loadImage("wesleyWest" + i, "Assets/skins/wesley/left_" + i + ".png");
    }

    // Wall variants — all 16 NESW neighbour combinations
    for (int n = 0; n <= 1; n++) {
      for (int e = 0; e <= 1; e++) {
        for (int s = 0; s <= 1; s++) {
          for (int w = 0; w <= 1; w++) {
            String id = "wall_" + n + e + s + w;
            loadImage(id, "Assets/tiles/walls/" + id + ".png");
          }
        }
      }
    }

    // Wesley pixel art (used as menu decoration)
    loadImage("wesleyPixel", "Assets/ui/wesley-pixel.png");

    // Splash screen mask image
    loadImage("splashMask", "Assets/ui/splash_mask.png");
  }

  /**
   * Loads a single image from the classpath and caches it.
   *
   * @param cacheKey the key to store the image under
   * @param resourcePath the classpath-relative resource path
   * @throws IOException if the image cannot be read
   */
  private void loadImage(String cacheKey, String resourcePath) throws IOException {
    BufferedImage img = ImageIO.read(getClass().getResourceAsStream(resourcePath));
    preloadedImages.put(cacheKey, img);
  }
}
