package mazegame;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;

/**
 * Manages all non-gameplay screens (main menu, level selection, game over, level completion).
 * Reuses a single JFrame — panels are swapped via {@link JFrame#setContentPane} instead of creating
 * new frames.
 */
public class MenuManager {

  private final JFrame frame;
  private final MazeGame game;

  public MenuManager(JFrame frame, MazeGame game) {
    this.frame = frame;
    this.game = game;
  }

  /** Shows the splash screen, then transitions to the main menu. */
  public void showSplash() {
    java.awt.image.BufferedImage maskImg = game.getAssetManager().getPreloadedImage("splashMask");
    SplashPanel splash = new SplashPanel(maskImg, this::showMainMenu);
    swapContent(splash);
    splash.requestFocusInWindow();
  }

  /** Shows the main menu with custom-painted panel. */
  public void showMainMenu() {
    game.getAudioManager().playMenuMusic();
    MainMenuPanel menuPanel = new MainMenuPanel(game.getAudioManager(), game.getAssetManager());

    // Use the Wesley pixel-art image as the menu decoration
    BufferedImage dogSprite = game.getAssetManager().getPreloadedImage("wesleyPixel");
    if (dogSprite == null) {
      // Fallback to active dog skin walking frame
      String spriteKey = game.getSettings().getSpritePrefix() + "East0";
      dogSprite = game.getAssetManager().getPreloadedImage(spriteKey);
    }
    if (dogSprite != null) {
      menuPanel.setDecorationImage(dogSprite);
    }

    String playLabel =
        game.hasProgress() ? Messages.get("button.continue") : Messages.get("button.play");
    menuPanel.addButton(playLabel, Messages.get("hint.space"), game::startLevel);
    menuPanel.addButton(Messages.get("button.level_selection"), "", this::showLevelSelection);
    menuPanel.addButton(Messages.get("button.collection"), "", this::showSkins);
    menuPanel.addButton(Messages.get("button.settings"), "", this::showSettings);

    boolean inBrowser = "true".equals(System.getProperty("cheerpj.browser"));
    if (!inBrowser) {
      menuPanel.addButton(
          Messages.get("button.quit"),
          Messages.get("hint.esc"),
          () -> {
            game.getAudioManager().stopMusic();
            frame.dispose();
          });
    }

    // Keyboard shortcuts
    InputHandler.bindKey(
        menuPanel, KeyEvent.VK_SPACE, "Next Level", false, evt -> game.startLevel());
    InputHandler.bindKey(
        menuPanel,
        KeyEvent.VK_ESCAPE,
        "Exit",
        false,
        evt -> {
          if (!inBrowser) {
            game.getAudioManager().stopMusic();
            frame.dispose();
          }
        });

    swapContent(menuPanel);
  }

  /** Shows the game-over screen for the current level. */
  public void showGameOverScreen(int level) {
    ResultOverlayPanel overlay =
        new ResultOverlayPanel(Messages.fmt("message.level_failed", level), game.getAudioManager());
    overlay.addButton(
        Messages.get("button.retry_level"), Messages.get("hint.space"), game::startLevel);
    overlay.addButton(
        Messages.get("button.main_menu"), Messages.get("hint.esc"), this::showMainMenu);

    InputHandler.bindKey(
        overlay, KeyEvent.VK_SPACE, "Retry Level", false, evt -> game.startLevel());
    InputHandler.bindKey(overlay, KeyEvent.VK_ESCAPE, "Menu", false, evt -> showMainMenu());

    swapContent(overlay);
  }

  /** Shows the level-completion screen. */
  public void showCompletionScreen(int level, double timeTaken) {
    // Grab PB *before* recording so we can detect improvement
    double previousBest = game.getBestTime(level);
    game.recordLevelCompletion(level, timeTaken);

    ResultOverlayPanel overlay =
        new ResultOverlayPanel(
            Messages.fmt("message.level_completed", level), game.getAudioManager());
    String timeStr = String.format("%.1f", timeTaken);
    int totalBones = game.getTotalBones();

    // Build subtitle: current time + personal best + bones
    StringBuilder sb = new StringBuilder();
    sb.append(Messages.fmt("label.time_value", timeStr));
    if (previousBest > 0 && previousBest < timeTaken) {
      // Previous PB still stands
      sb.append("  ")
          .append(Messages.fmt("label.personal_best", String.format("%.1f", previousBest)));
    } else {
      // New PB (first completion or improved)
      sb.append("  ").append(Messages.get("label.new_personal_best"));
    }
    if (totalBones > 0) {
      sb.append("  ").append(Messages.fmt("label.golden_bones_count", totalBones));
    }
    overlay.setSubtitle(sb.toString());
    overlay.addButton(
        Messages.get("button.next_level"),
        Messages.get("hint.space"),
        () -> {
          game.increaseLevel();
          game.startLevel();
        });
    overlay.addButton(
        Messages.get("button.main_menu"), Messages.get("hint.esc"), this::showMainMenu);

    InputHandler.bindKey(
        overlay,
        KeyEvent.VK_SPACE,
        "Next Level",
        false,
        evt -> {
          game.increaseLevel();
          game.startLevel();
        });
    InputHandler.bindKey(overlay, KeyEvent.VK_ESCAPE, "Menu", false, evt -> showMainMenu());

    swapContent(overlay);
  }

  /** Shows the level-selection screen with custom-painted cards. */
  public void showLevelSelection() {
    String[] levelData = game.getLevelData();

    LevelSelectionPanel panel =
        new LevelSelectionPanel(
            levelData,
            game,
            this::showMainMenu,
            () -> {
              game.load(true);
              game.save();
              game.setCurrentLevel(-1);
              showLevelSelection(); // refresh after reset
            },
            game.getAudioManager());

    swapContent(panel);
    panel.requestFocusInWindow();
  }

  /** Shows the dedicated skins / collection screen. */
  public void showSkins() {
    SkinsPanel panel =
        new SkinsPanel(
            game.getSettings(),
            game.getAssetManager(),
            game.getAudioManager(),
            this::showMainMenu,
            game.getTotalBones());
    swapContent(panel);
    panel.requestFocusInWindow();
  }

  /** Shows the settings screen (audio toggles). */
  public void showSettings() {
    SettingsPanel panel =
        new SettingsPanel(game.getSettings(), game.getAudioManager(), this::showMainMenu);
    swapContent(panel);
    panel.requestFocusInWindow();
  }

  /** Replaces the frame's content pane and refreshes. */
  private void swapContent(javax.swing.JComponent component) {
    frame.setContentPane(component);
    frame.revalidate();
    frame.repaint();
  }
}
