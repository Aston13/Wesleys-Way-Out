package mazegame;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * Main game class — owns the JFrame, delegates gameplay to {@link GameLoop}, input to {@link
 * InputHandler}, rendering to {@link Renderer}, and menu screens to {@link MenuManager}.
 *
 * <p>A single JFrame is reused for the lifetime of the application; screens are swapped via content
 * pane replacement rather than disposing and recreating frames.
 */
public class MazeGame extends JFrame implements GameLoop.Callbacks, InputHandler.Listener {

  private static final int TILE_SIZE = 100;
  private static final int TILE_BORDER = 0;
  private static final int MOVEMENT_SPEED = 8;
  private static final int INITIAL_GRID_SIZE = 10;
  private static final int PAUSE_TITLE_FONT_SIZE = 40;

  private final GamePanel gameView = new GamePanel();
  private final int windowWidth;
  private final int windowHeight;
  private final AssetManager assetManager;
  private final GameSettings settings;
  private final AudioManager audioManager;
  private final MenuManager menuManager;
  private final InputHandler inputHandler;

  private volatile boolean gameInProgress;
  private volatile boolean paused;
  private volatile String pauseAction;
  private Rectangle resumeBtn;
  private Rectangle restartBtn;
  private Rectangle menuBtn;

  /** Index of the currently hovered pause-overlay button (0=resume, 1=restart, 2=menu, -1=none). */
  private volatile int hoveredPauseBtn = -1;

  /** Current D-pad direction pressed on touch overlay (-1=none). */
  volatile int touchDir = TouchDpad.NONE;

  /** Whether to show on-screen touch controls (browser / mobile). */
  private final boolean showTouchControls;

  private Player player;
  private Renderer renderer;
  private JPanel pane = newPane();
  private int levelCount = 1;
  private int rowColAmount;
  private String stateChange;
  private String[] levelData;
  private GameLoop gameLoop;
  private boolean closingListenerAdded;
  private boolean splashShown;

  /** Invisible cursor applied during gameplay so the mouse pointer doesn't distract. */
  private final Cursor blankCursor;

  // Always render to an offscreen buffer at the logical resolution, then scale to the canvas.
  private BufferedImage offscreenBuffer = null;

  /**
   * Creates a new game instance.
   *
   * @param windowHeight the window height in pixels
   * @param windowWidth the window width in pixels
   * @param rowColAmount the initial maze grid size
   */
  public MazeGame(int windowHeight, int windowWidth, int rowColAmount) {
    this.windowWidth = windowWidth;
    this.windowHeight = windowHeight;
    if (rowColAmount % 2 == 0) {
      rowColAmount += 1;
    }
    this.rowColAmount = rowColAmount;
    this.assetManager = new AssetManager();
    try {
      assetManager.preloadImages();
    } catch (IOException e) {
      System.err.println("Failed to preload images: " + e.getMessage());
    }
    this.settings = new GameSettings();
    this.audioManager = new AudioManager();
    audioManager.setMusicMuted(settings.isMusicMuted());
    audioManager.setMusicVolume(settings.getMusicVolume());
    this.menuManager = new MenuManager(this, this);
    this.inputHandler = new InputHandler(this);
    // Touch controls disabled until mobile performance & viewport issues are resolved.
    // Original: "true".equals(System.getProperty("cheerpj.browser"))
    this.showTouchControls = false;
    this.blankCursor =
        Toolkit.getDefaultToolkit()
            .createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
    load(false);
    setCurrentLevel(-1);
  }

  // ---------------------------------------------------------------------------
  // Level / save management
  // ---------------------------------------------------------------------------

  /**
   * Sets the current level. Pass {@code -1} to auto-detect the first incomplete level from saved
   * data.
   *
   * @param level the level number, or -1 for auto-detect
   */
  public void setCurrentLevel(int level) {
    if (level == -1) {
      for (int i = 1; i < levelData.length; i++) {
        String[] lineWords = levelData[i].split(",");
        if (lineWords[1].equalsIgnoreCase("incomplete")) {
          levelCount = i;
          int rc = INITIAL_GRID_SIZE + ((i - 1) * 2);
          if (rc % 2 == 0) {
            rc += 1;
          }
          rowColAmount = rc;
          break;
        }
      }
    } else {
      levelCount = level;
      int rc = INITIAL_GRID_SIZE + ((level - 1) * 2);
      if (rc % 2 == 0) {
        rc += 1;
      }
      rowColAmount = rc;
    }
  }

  /** Saves current level progress to disk. */
  public void save() {
    try {
      assetManager.saveLevelData(levelData);
    } catch (IOException ex) {
      System.err.println("Save failed: " + ex.getMessage());
    }
  }

  /** Loads level data from disk or classpath resource. */
  public void load(boolean reset) {
    try {
      levelData = assetManager.loadLevelData(reset);
    } catch (IOException ex) {
      System.err.println("Load failed: " + ex.getMessage());
    }
  }

  /** Returns the current level data array. */
  public String[] getLevelData() {
    return levelData;
  }

  /** Returns true if the player has completed at least one level. */
  public boolean hasProgress() {
    if (levelData == null) return false;
    for (int i = 1; i < levelData.length; i++) {
      if (levelData[i].contains("completed")) return true;
    }
    return false;
  }

  /** Returns the shared asset manager. */
  public AssetManager getAssetManager() {
    return assetManager;
  }

  /** Returns the game settings (skin, preferences). */
  public GameSettings getSettings() {
    return settings;
  }

  /** Returns the audio manager for sound effects. */
  public AudioManager getAudioManager() {
    return audioManager;
  }

  /**
   * Returns the stored personal-best time for a level, or {@code -1} if the level has not been
   * completed before.
   *
   * @param level the 1-based level number
   * @return best time in seconds, or -1 if no prior completion
   */
  public double getBestTime(int level) {
    if (level < 1 || level >= levelData.length) return -1;
    String[] parts = levelData[level].split(",");
    return Double.parseDouble(parts[2]);
  }

  /** Records a level completion, updating best time if improved. Preserves bone status. */
  public void recordLevelCompletion(int level, double timeTaken) {
    String[] lineWords = levelData[level].split(",");
    double bestTime = Double.parseDouble(lineWords[2]);
    if (timeTaken < bestTime || bestTime == -1) {
      bestTime = timeTaken;
    }
    String bone = lineWords.length >= 4 ? lineWords[3] : "0";
    levelData[level] = level + ",completed," + bestTime + "," + bone;
    save();
  }

  // ---------------------------------------------------------------------------
  // Bone collectibles
  // ---------------------------------------------------------------------------

  /**
   * Returns whether the bone for a given level has been collected.
   *
   * @param level the level number (1-based)
   * @return true if the bone is recorded as collected
   */
  public boolean isBoneCollected(int level) {
    if (level < 1 || level >= levelData.length) return false;
    String[] parts = levelData[level].split(",");
    return parts.length >= 4 && "1".equals(parts[3]);
  }

  /** Records a bone collection for the current level and saves. */
  public void onBoneCollected() {
    recordBoneCollection(levelCount);
  }

  /**
   * Records a bone collection for a specific level.
   *
   * @param level the level number (1-based)
   */
  public void recordBoneCollection(int level) {
    if (level < 1 || level >= levelData.length) return;
    String[] parts = levelData[level].split(",");
    String status = parts.length >= 2 ? parts[1] : "incomplete";
    String time = parts.length >= 3 ? parts[2] : "-1";
    levelData[level] = level + "," + status + "," + time + ",1";
    save();
  }

  /**
   * Returns the total number of bones collected across all levels.
   *
   * @return the total bone count
   */
  public int getTotalBones() {
    int count = 0;
    for (int i = 1; i < levelData.length; i++) {
      if (isBoneCollected(i)) count++;
    }
    return count;
  }

  /** Advances to the next level (up to 30). */
  public void increaseLevel() {
    if (levelCount < 30) {
      levelCount += 1;
      rowColAmount += 2;
    }
  }

  // ---------------------------------------------------------------------------
  // Frame setup
  // ---------------------------------------------------------------------------

  /** Configures the JFrame: resizable, exit-on-close, centred. */
  public void setUpFrame() {
    boolean inBrowser = "true".equals(System.getProperty("cheerpj.browser"));
    setTitle(inBrowser ? "" : Messages.get("title.game_name"));
    if (inBrowser && !isDisplayable()) {
      setUndecorated(true);
    }
    setResizable(true);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // Set custom app icon (Wesley sprite)
    java.awt.image.BufferedImage icon = assetManager.getPreloadedImage("wesleyPixel");
    if (icon != null) {
      setIconImage(icon);
    }

    // Stop audio and clean up resources before the JVM exits
    if (!closingListenerAdded) {
      addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
              if (audioManager != null) {
                audioManager.stopMusic();
              }
            }
          });
      closingListenerAdded = true;
    }

    setContentPane(pane);
    pack();
    setLocationRelativeTo(null);
    setVisible(true);
  }

  // ---------------------------------------------------------------------------
  // State management
  // ---------------------------------------------------------------------------

  /**
   * Sets the game state and reason for the state change.
   *
   * @param inProgress true if a level is actively being played
   * @param reason the reason for the state change
   */
  public void setGameState(boolean inProgress, String reason) {
    gameInProgress = inProgress;
    stateChange = reason;
  }

  public boolean getGameState() {
    return gameInProgress;
  }

  // ---------------------------------------------------------------------------
  // Level launching (reuses single JFrame — no dispose/recreate)
  // ---------------------------------------------------------------------------

  /**
   * Starts (or restarts) a level. Reuses this JFrame — stops any running game loop, sets up the
   * canvas, generates the maze, and starts a new loop.
   */
  public void startLevel() {
    // Stop any existing game loop
    if (gameLoop != null) {
      gameLoop.stop();
      gameLoop.join();
    }

    // Reset pause state
    paused = false;
    pauseAction = null;

    // Set up content
    pane = newPane();
    setUpFrame();

    inputHandler.bindMovementKeys(pane);
    inputHandler.installGlobalDispatcher();

    pane.add(gameView);
    gameView.setFocusable(true);
    gameView.setCursor(blankCursor);
    validate(); // Force layout so gameView has non-zero dimensions before first render

    renderer =
        new Renderer(
            windowWidth,
            windowHeight,
            rowColAmount,
            TILE_SIZE,
            assetManager,
            audioManager,
            settings,
            this,
            isBoneCollected(levelCount));
    renderer.generateMaze(TILE_SIZE, TILE_BORDER);
    renderer.centerMaze();
    player = new Player(renderer.getStartingX(), renderer.getStartingY(), TILE_SIZE);
    renderer.beginTimer();
    renderer.triggerStartQuip();

    // Switch to in-game music
    audioManager.playIngameMusic(levelCount);

    setGameState(true, "");
    render();

    inputHandler.bindPauseMouseClicks(gameView);
    inputHandler.bindTouchControls(gameView);

    gameLoop = new GameLoop(this);
    gameLoop.setOnComplete(
        () -> {
          inputHandler.removeGlobalDispatcher();
          cleanUpGameView();
          handleLevelEnd();
        });
    gameLoop.start();
  }

  /** Starts a specific level from the level-selection screen. */
  public void playSelectedLevel() {
    startLevel();
  }

  private void cleanUpGameView() {
    gameView.setCursor(Cursor.getDefaultCursor());
    renderBackground();
    if (renderer != null) {
      renderer.stopTimer();
    }
  }

  private void handleLevelEnd() {
    if ("Level Failed".equalsIgnoreCase(stateChange)) {
      audioManager.play(AudioManager.Sound.LEVEL_FAILED);
      menuManager.showGameOverScreen(levelCount);
    } else if ("Next Level".equalsIgnoreCase(stateChange)) {
      audioManager.play(AudioManager.Sound.LEVEL_COMPLETE);
      double timeInMs = renderer.getTimeTaken();
      menuManager.showCompletionScreen(levelCount, timeInMs);
    } else if ("Restart".equalsIgnoreCase(stateChange)) {
      startLevel();
    } else if ("Menu".equalsIgnoreCase(stateChange)) {
      menuManager.showMainMenu();
    }
  }

  // ---------------------------------------------------------------------------
  // Menu delegation
  // ---------------------------------------------------------------------------

  /** Shows the main menu (reuses this frame). */
  public void runMenu() {
    // Stop any running game loop
    if (gameLoop != null) {
      gameLoop.stop();
      gameLoop.join();
    }

    try {
      super.remove(gameView);
      pane.removeAll();
    } catch (Exception e) {
      // Ignore cleanup errors
    }

    pane = newPane();
    pane.setBackground(new Color(10, 10, 10));
    setUpFrame();
    if (!splashShown) {
      splashShown = true;
      menuManager.showSplash();
    } else {
      menuManager.showMainMenu();
    }
  }

  /** Shows the level-selection screen. */
  public void runLevelSelection() {
    menuManager.showLevelSelection();
  }

  // ---------------------------------------------------------------------------
  // GameLoop.Callbacks implementation
  // ---------------------------------------------------------------------------

  @Override
  public void onUpdate() {
    update();
  }

  @Override
  public void onRender() {
    render();
  }

  @Override
  public void onAnimationTick() {
    renderer.updateFrames();
  }

  @Override
  public boolean isGameInProgress() {
    return gameInProgress;
  }

  @Override
  public boolean isPaused() {
    return paused;
  }

  @Override
  public boolean handlePauseFrame() {
    String action = pauseAction;
    if ("resume".equals(action)) {
      pauseAction = null;
      paused = false;
      renderer.beginTimer();
      return true;
    } else if ("restart".equals(action)) {
      pauseAction = null;
      setGameState(false, "Restart");
      return true;
    } else if ("menu".equals(action)) {
      pauseAction = null;
      setGameState(false, "Menu");
      return true;
    } else {
      renderPauseScreen();
      return false;
    }
  }

  // ---------------------------------------------------------------------------
  // InputHandler.Listener implementation
  // ---------------------------------------------------------------------------

  @Override
  public void onPauseRequested() {
    paused = true;
    if (renderer != null) {
      renderer.stopTimer();
    }
    if (player != null) {
      player.setMoveN(false);
      player.setMoveE(false);
      player.setMoveS(false);
      player.setMoveW(false);
    }
  }

  @Override
  public void onResumeRequested() {
    pauseAction = "resume";
  }

  @Override
  public void onRestartRequested() {
    pauseAction = "restart";
  }

  @Override
  public void onMenuRequested() {
    pauseAction = "menu";
  }

  @Override
  public Player getPlayer() {
    return player;
  }

  @Override
  public Rectangle getResumeBtn() {
    return resumeBtn;
  }

  @Override
  public Rectangle getRestartBtn() {
    return restartBtn;
  }

  @Override
  public Rectangle getMenuBtn() {
    return menuBtn;
  }

  @Override
  public void setHoveredPauseBtn(int index) {
    hoveredPauseBtn = index;
  }

  @Override
  public int getGameWidth() {
    return windowWidth;
  }

  @Override
  public int getGameHeight() {
    return windowHeight;
  }

  @Override
  public void onTouchDirection(int dir) {
    touchDir = dir;
  }

  @Override
  public boolean showTouchControls() {
    return showTouchControls;
  }

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  /**
   * Returns graphics for the offscreen buffer at the fixed logical resolution. All game rendering
   * happens at this resolution; the result is scaled to the canvas in {@link #showBuffer()}.
   */
  private Graphics getGameGraphics() {
    if (offscreenBuffer == null
        || offscreenBuffer.getWidth() != windowWidth
        || offscreenBuffer.getHeight() != windowHeight) {
      offscreenBuffer = new BufferedImage(windowWidth, windowHeight, BufferedImage.TYPE_INT_ARGB);
    }
    return offscreenBuffer.getGraphics();
  }

  /**
   * Pushes the offscreen buffer to the game panel and triggers an immediate repaint. Since the game
   * loop now runs on the EDT via {@link javax.swing.Timer}, we can call {@code paintImmediately}
   * directly for reliable rendering in CheerpJ.
   */
  private void showBuffer() {
    if (offscreenBuffer == null) return;
    gameView.setBuffer(offscreenBuffer);
    int w = gameView.getWidth();
    int h = gameView.getHeight();
    if (w > 0 && h > 0) {
      gameView.paintImmediately(0, 0, w, h);
    }
  }

  /**
   * Converts canvas-space coordinates to logical-space coordinates, accounting for the current
   * scale factor and letterbox offset.
   */
  public int[] toLogicalCoords(int canvasX, int canvasY) {
    int cw = gameView.getWidth();
    int ch = gameView.getHeight();
    if (cw <= 0 || ch <= 0) return new int[] {canvasX, canvasY};

    double scale = Math.min((double) cw / windowWidth, (double) ch / windowHeight);
    int scaledW = (int) (windowWidth * scale);
    int scaledH = (int) (windowHeight * scale);
    int offsetX = (cw - scaledW) / 2;
    int offsetY = (ch - scaledH) / 2;

    return new int[] {(int) ((canvasX - offsetX) / scale), (int) ((canvasY - offsetY) / scale)};
  }

  public void update() {
    // Check if confetti delay has elapsed → trigger level complete
    renderer.checkPendingCompletion(this);
    // Block movement while confetti plays
    if (renderer.isPendingLevelComplete()) return;

    int halfPlayer = player.getSize() / 2;
    Graphics g = getGameGraphics();
    if (g == null) return;

    if (player.getMoveN()) {
      int[] nextTile =
          renderer.getTile(
              player.getX(),
              player.getY() - (halfPlayer + 1),
              player.getSize(),
              TILE_SIZE,
              TILE_BORDER);
      if (renderer.checkCollision(nextTile, this)) {
        renderer.moveMazeY(rowColAmount, MOVEMENT_SPEED);
        player.setY(player.getY() - MOVEMENT_SPEED);
      }
    }
    if (player.getMoveE()) {
      int[] nextTile =
          renderer.getTile(
              player.getX() + (halfPlayer + 1),
              player.getY(),
              player.getSize(),
              TILE_SIZE,
              TILE_BORDER);
      if (renderer.checkCollision(nextTile, this)) {
        renderer.moveMazeX(rowColAmount, -MOVEMENT_SPEED);
        player.setX(player.getX() + MOVEMENT_SPEED);
      }
    }
    if (player.getMoveS()) {
      int[] nextTile =
          renderer.getTile(
              player.getX(),
              player.getY() + (halfPlayer + 1),
              player.getSize(),
              TILE_SIZE,
              TILE_BORDER);
      if (renderer.checkCollision(nextTile, this)) {
        renderer.moveMazeY(rowColAmount, -MOVEMENT_SPEED);
        player.setY(player.getY() + MOVEMENT_SPEED);
      }
    }
    if (player.getMoveW()) {
      int[] nextTile =
          renderer.getTile(
              player.getX() - (halfPlayer + 1),
              player.getY(),
              player.getSize(),
              TILE_SIZE,
              TILE_BORDER);
      if (renderer.checkCollision(nextTile, this)) {
        renderer.moveMazeX(rowColAmount, MOVEMENT_SPEED);
        player.setX(player.getX() - MOVEMENT_SPEED);
      }
    }
    g.dispose();
  }

  public void render() {
    Graphics g = getGameGraphics();
    if (g == null) return;
    renderer.renderBackground(g);
    renderer.renderMaze(g, TILE_SIZE);
    renderer.renderPlayer(g, TILE_SIZE);
    renderer.renderHUD(g, levelCount);
    if (showTouchControls) {
      Graphics2D g2 = (Graphics2D) g;
      TouchDpad.drawDpad(g2, windowWidth, windowHeight, touchDir);
      TouchDpad.drawPauseIcon(g2, windowWidth);
    }
    g.dispose();
    showBuffer();
  }

  public void renderBackground() {
    Graphics g = getGameGraphics();
    if (g == null) return;
    renderer.renderBackground(g);
    g.dispose();
    showBuffer();
  }

  private void renderPauseScreen() {
    // Render the game frame underneath so the overlay shows through
    Graphics g = getGameGraphics();
    if (g == null) return;
    renderer.renderBackground(g);
    renderer.renderMaze(g, TILE_SIZE);
    renderer.renderPlayer(g, TILE_SIZE);
    renderer.renderHUD(g, levelCount);

    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    // Semi-transparent gradient overlay matching Wesley palette
    g2.setPaint(
        new GradientPaint(
            0, 0, new Color(26, 26, 26, 210), 0, windowHeight, new Color(34, 30, 28, 210)));
    g2.fillRect(0, 0, windowWidth, windowHeight);

    // Subtle grid decoration
    g2.setColor(new Color(255, 255, 255, 6));
    for (int x = 0; x < windowWidth; x += 40) {
      g2.drawLine(x, 0, x, windowHeight);
    }
    for (int y = 0; y < windowHeight; y += 40) {
      g2.drawLine(0, y, windowWidth, y);
    }

    // Title with shadow
    Font titleFont = new Font("Dialog", Font.BOLD, PAUSE_TITLE_FONT_SIZE);
    g2.setFont(titleFont);
    FontMetrics fmTitle = g2.getFontMetrics();
    String title = Messages.get("screen.paused");
    int titleX = (windowWidth - fmTitle.stringWidth(title)) / 2;
    int titleY = windowHeight / 4;
    g2.setColor(new Color(80, 60, 40));
    g2.drawString(title, titleX + 2, titleY + 2);
    g2.setColor(new Color(240, 236, 232));
    g2.drawString(title, titleX, titleY);

    // Rounded buttons matching main menu style (via shared UiTheme)
    int btnW = UiTheme.STD_BTN_WIDTH;
    int btnH = UiTheme.STD_BTN_HEIGHT;
    int btnX = (windowWidth - btnW) / 2;
    int gap = btnH + UiTheme.STD_BTN_GAP;

    int resumeY = windowHeight / 2 - btnH - gap / 2;
    int restartY = resumeY + gap;
    int menuY = restartY + gap;

    resumeBtn = new Rectangle(btnX, resumeY, btnW, btnH);
    restartBtn = new Rectangle(btnX, restartY, btnW, btnH);
    menuBtn = new Rectangle(btnX, menuY, btnW, btnH);

    BufferedImage keyFrame = assetManager.getKeyFrame();
    UiTheme.paintStdButton(
        g2,
        btnX,
        resumeY,
        Messages.get("button.resume"),
        Messages.get("hint.space_or_esc"),
        hoveredPauseBtn == 0,
        keyFrame);
    UiTheme.paintStdButton(
        g2,
        btnX,
        restartY,
        Messages.get("button.restart_level"),
        Messages.get("hint.r"),
        hoveredPauseBtn == 1,
        keyFrame);
    UiTheme.paintStdButton(
        g2, btnX, menuY, Messages.get("button.main_menu"), "", hoveredPauseBtn == 2, keyFrame);

    g2.dispose();
    showBuffer();
  }

  // ---------------------------------------------------------------------------
  // Backwards-compat helpers used by UI (level selection)
  // ---------------------------------------------------------------------------

  public void setLevel(int level) {
    levelCount += level - 1;
    rowColAmount += ((level - 1) * 2);
  }

  /**
   * Creates a new content pane with an explicit preferred size matching the logical game
   * resolution. This ensures {@code pack()} sizes the frame's content area correctly on all
   * platforms (fixes black bars caused by content pane reporting 0×0 before layout).
   */
  private JPanel newPane() {
    JPanel p = new JPanel(new GridLayout());
    p.setPreferredSize(new Dimension(windowWidth, windowHeight));
    return p;
  }
}
