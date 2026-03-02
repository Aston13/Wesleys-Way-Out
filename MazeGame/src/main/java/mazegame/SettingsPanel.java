package mazegame;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Custom-painted settings screen for audio toggles (sound effects and music). Skin selection has
 * been moved to {@link SkinsPanel}.
 */
public class SettingsPanel extends JPanel {

  private static final Color BG_TOP = new Color(26, 26, 26);
  private static final Color BG_BOTTOM = new Color(34, 30, 28);
  private static final Color TITLE_COLOR = new Color(240, 236, 232);
  private static final Color TITLE_SHADOW = new Color(80, 60, 40);
  private static final Color SECTION_LABEL = new Color(196, 149, 106);
  private static final Color CARD_BG = new Color(50, 44, 40);
  private static final Color CARD_BORDER = new Color(100, 85, 70);
  private static final Color CARD_HOVER_BG = new Color(65, 55, 48);
  private static final Color TEXT_PRIMARY = new Color(240, 236, 232);
  private static final Color TEXT_DIM = new Color(160, 145, 130);
  private static final Color GRID_LINE = new Color(255, 255, 255, 6);

  private static final int TITLE_FONT_SIZE = 36;
  private static final int SECTION_FONT_SIZE = 16;
  private static final int BTN_WIDTH = 140;
  private static final int BTN_HEIGHT = 42;
  private static final int BTN_ARC = 10;
  private static final int PARTICLE_COUNT = 20;
  private static final int PARTICLE_TICK_MS = 50;

  private final GameSettings settings;
  private final AudioManager audioManager;
  private final Runnable onBack;

  private static final String[] LANG_CODES = {"en", "nb"};

  private boolean hoveredBack = false;
  private boolean hoveredMuteToggle = false;
  private boolean hoveredMusicToggle = false;
  private boolean hoveredLanguageToggle = false;
  private final double[] particleX = new double[PARTICLE_COUNT];
  private final double[] particleY = new double[PARTICLE_COUNT];
  private final double[] particleSpeed = new double[PARTICLE_COUNT];
  private final double[] particleAlpha = new double[PARTICLE_COUNT];
  private final double[] particleSize = new double[PARTICLE_COUNT];
  private final Random particleRng = new Random();
  private Timer particleTimer;

  /**
   * Creates a new settings panel (audio toggles only).
   *
   * @param settings the game settings to modify
   * @param audioManager the audio manager for mute control
   * @param onBack callback to return to the main menu
   */
  public SettingsPanel(GameSettings settings, AudioManager audioManager, Runnable onBack) {
    this.settings = settings;
    this.audioManager = audioManager;
    this.onBack = onBack;
    setOpaque(true);
    setBackground(BG_TOP);
    setFocusable(true);

    // Initialise floating particles
    for (int i = 0; i < PARTICLE_COUNT; i++) {
      resetParticle(i, true);
    }
    particleTimer =
        new Timer(
            PARTICLE_TICK_MS,
            e -> {
              for (int i = 0; i < PARTICLE_COUNT; i++) {
                particleY[i] -= particleSpeed[i];
                particleAlpha[i] -= 0.004;
                if (particleY[i] < -10 || particleAlpha[i] <= 0) {
                  resetParticle(i, false);
                }
              }
              repaint();
            });
    particleTimer.start();

    MouseAdapter mouseHandler =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            updateHover(e.getX(), e.getY());
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            handleClick(e.getX(), e.getY());
          }

          @Override
          public void mouseExited(MouseEvent e) {
            if (hoveredBack || hoveredMuteToggle || hoveredMusicToggle || hoveredLanguageToggle) {
              hoveredBack = false;
              hoveredMuteToggle = false;
              hoveredMusicToggle = false;
              hoveredLanguageToggle = false;
              setCursor(Cursor.getDefaultCursor());
              repaint();
            }
          }
        };
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);

    InputHandler.bindKey(this, KeyEvent.VK_ESCAPE, "Back", false, evt -> onBack.run());
  }

  @Override
  public Dimension getPreferredSize() {
    java.awt.Container parent = getParent();
    if (parent != null) {
      return parent.getSize();
    }
    return new Dimension(650, 650);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    int w = getWidth();
    int h = getHeight();

    // Gradient background
    g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM));
    g.fillRect(0, 0, w, h);

    // Decorative grid lines
    g.setColor(GRID_LINE);
    for (int x = 0; x < w; x += 40) {
      g.drawLine(x, 0, x, h);
    }
    for (int y = 0; y < h; y += 40) {
      g.drawLine(0, y, w, y);
    }

    // Floating particles
    Composite particleOrig = g.getComposite();
    for (int i = 0; i < PARTICLE_COUNT; i++) {
      float alpha = (float) Math.max(0, Math.min(1, particleAlpha[i]));
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      g.setColor(SECTION_LABEL);
      double px = particleX[i] / 800.0 * w;
      double py = particleY[i] / 800.0 * h;
      int sz = (int) particleSize[i];
      g.fillOval((int) px, (int) py, sz, sz);
    }
    g.setComposite(particleOrig);

    // Title
    Font titleFont = new Font("Dialog", Font.BOLD, TITLE_FONT_SIZE);
    g.setFont(titleFont);
    FontMetrics titleFm = g.getFontMetrics();
    String title = Messages.get("screen.settings");
    int titleX = (w - titleFm.stringWidth(title)) / 2;
    int titleY = h / 4 + titleFm.getAscent() / 2;
    g.setColor(TITLE_SHADOW);
    g.drawString(title, titleX + 2, titleY + 2);
    g.setColor(TITLE_COLOR);
    g.drawString(title, titleX, titleY);

    // Sound section label
    Font sectionFont = new Font("Dialog", Font.BOLD, SECTION_FONT_SIZE);
    g.setFont(sectionFont);
    FontMetrics secFm = g.getFontMetrics();
    String soundLabel = Messages.get("label.sound_section");
    int soundSecX = (w - secFm.stringWidth(soundLabel)) / 2;
    int soundSecY = titleY + 55;
    g.setColor(SECTION_LABEL);
    g.drawString(soundLabel, soundSecX, soundSecY);

    // Sound-effects mute toggle
    int toggleW = 200;
    int toggleH = 42;
    int toggleX = (w - toggleW) / 2;
    int toggleY = soundSecY + 15;
    RoundRectangle2D.Double muteRect =
        new RoundRectangle2D.Double(toggleX, toggleY, toggleW, toggleH, BTN_ARC, BTN_ARC);
    boolean muted = settings.isSoundMuted();
    g.setColor(hoveredMuteToggle ? CARD_HOVER_BG : CARD_BG);
    g.fill(muteRect);
    g.setColor(hoveredMuteToggle ? SECTION_LABEL : CARD_BORDER);
    g.draw(muteRect);

    g.setFont(new Font("Dialog", Font.PLAIN, 14));
    FontMetrics mfm = g.getFontMetrics();
    String muteLabel = muted ? Messages.get("toggle.sound_off") : Messages.get("toggle.sound_on");
    g.setColor(muted ? TEXT_DIM : TEXT_PRIMARY);
    g.drawString(
        muteLabel,
        toggleX + (toggleW - mfm.stringWidth(muteLabel)) / 2,
        toggleY + (toggleH + mfm.getAscent()) / 2 - 2);

    // Music mute toggle
    int musicToggleY = toggleY + toggleH + 12;
    RoundRectangle2D.Double musicRect =
        new RoundRectangle2D.Double(toggleX, musicToggleY, toggleW, toggleH, BTN_ARC, BTN_ARC);
    boolean musicMuted = settings.isMusicMuted();
    g.setColor(hoveredMusicToggle ? CARD_HOVER_BG : CARD_BG);
    g.fill(musicRect);
    g.setColor(hoveredMusicToggle ? SECTION_LABEL : CARD_BORDER);
    g.draw(musicRect);

    g.setFont(new Font("Dialog", Font.PLAIN, 14));
    FontMetrics mmfm = g.getFontMetrics();
    String musicLabel =
        musicMuted ? Messages.get("toggle.music_off") : Messages.get("toggle.music_on");
    g.setColor(musicMuted ? TEXT_DIM : TEXT_PRIMARY);
    g.drawString(
        musicLabel,
        toggleX + (toggleW - mmfm.stringWidth(musicLabel)) / 2,
        musicToggleY + (toggleH + mmfm.getAscent()) / 2 - 2);

    // Browser audio notice
    int noticeY = musicToggleY + toggleH + 8;
    if ("true".equals(System.getProperty("cheerpj.browser"))) {
      g.setFont(new Font("Dialog", Font.ITALIC, 11));
      FontMetrics nfm2 = g.getFontMetrics();
      String notice = Messages.get("notice.browser_no_audio");
      g.setColor(TEXT_DIM);
      g.drawString(notice, (w - nfm2.stringWidth(notice)) / 2, noticeY + nfm2.getAscent() + 2);
      noticeY += 20;
    }

    // Language section label
    int langSecY = noticeY + 22;
    g.setFont(sectionFont);
    String langLabel = Messages.get("label.language_section");
    int langSecX = (w - secFm.stringWidth(langLabel)) / 2;
    g.setColor(SECTION_LABEL);
    g.drawString(langLabel, langSecX, langSecY);

    // Language toggle
    int langToggleY = langSecY + 15;
    RoundRectangle2D.Double langRect =
        new RoundRectangle2D.Double(toggleX, langToggleY, toggleW, toggleH, BTN_ARC, BTN_ARC);
    g.setColor(hoveredLanguageToggle ? CARD_HOVER_BG : CARD_BG);
    g.fill(langRect);
    g.setColor(hoveredLanguageToggle ? SECTION_LABEL : CARD_BORDER);
    g.draw(langRect);

    g.setFont(new Font("Dialog", Font.PLAIN, 14));
    FontMetrics lfm = g.getFontMetrics();
    String langCode = settings.getLanguage();
    String langDisplayLabel = Messages.get("language." + langCode);
    g.setColor(TEXT_PRIMARY);
    g.drawString(
        langDisplayLabel,
        toggleX + (toggleW - lfm.stringWidth(langDisplayLabel)) / 2,
        langToggleY + (toggleH + lfm.getAscent()) / 2 - 2);

    // Back button
    int btnX = (w - BTN_WIDTH) / 2;
    int btnY = langToggleY + toggleH + 22;
    UiTheme.paintButton(
        g,
        btnX,
        btnY,
        BTN_WIDTH,
        BTN_HEIGHT,
        BTN_ARC,
        Messages.get("button.back_esc"),
        null,
        hoveredBack,
        16,
        true);
  }

  // ---- Particles ----

  private void resetParticle(int i, boolean randomY) {
    particleX[i] = particleRng.nextDouble() * 800;
    particleY[i] = randomY ? particleRng.nextDouble() * 800 : 780 + particleRng.nextDouble() * 40;
    particleSpeed[i] = 0.3 + particleRng.nextDouble() * 0.7;
    particleAlpha[i] = 0.10 + particleRng.nextDouble() * 0.20;
    particleSize[i] = 2 + particleRng.nextDouble() * 3;
  }

  // ---- Hit testing ----

  /** Computes the Y position shared by both toggle buttons and the back button. */
  private int computeSoundSecY() {
    int h = getHeight();
    Font titleFont = new Font("Dialog", Font.BOLD, TITLE_FONT_SIZE);
    FontMetrics titleFm = getFontMetrics(titleFont);
    int titleY = h / 4 + titleFm.getAscent() / 2;
    return titleY + 55;
  }

  private boolean isOverBackButton(int mx, int my) {
    int w = getWidth();
    int soundSecY = computeSoundSecY();
    int toggleH = 42;
    int toggleY = soundSecY + 15;
    int musicToggleY = toggleY + toggleH + 12;
    int noticeY = musicToggleY + toggleH + 8;
    if ("true".equals(System.getProperty("cheerpj.browser"))) {
      noticeY += 20;
    }
    int langSecY = noticeY + 22;
    int langToggleY = langSecY + 15;

    int btnX = (w - BTN_WIDTH) / 2;
    int btnY = langToggleY + toggleH + 22;
    return mx >= btnX && mx <= btnX + BTN_WIDTH && my >= btnY && my <= btnY + BTN_HEIGHT;
  }

  private boolean isOverMuteToggle(int mx, int my) {
    int w = getWidth();
    int soundSecY = computeSoundSecY();
    int toggleW = 200;
    int toggleH = 42;
    int toggleX = (w - toggleW) / 2;
    int toggleY = soundSecY + 15;
    return mx >= toggleX && mx <= toggleX + toggleW && my >= toggleY && my <= toggleY + toggleH;
  }

  private boolean isOverMusicToggle(int mx, int my) {
    int w = getWidth();
    int soundSecY = computeSoundSecY();
    int toggleW = 200;
    int toggleH = 42;
    int toggleX = (w - toggleW) / 2;
    int toggleY = soundSecY + 15;
    int musicToggleY = toggleY + toggleH + 12;
    return mx >= toggleX
        && mx <= toggleX + toggleW
        && my >= musicToggleY
        && my <= musicToggleY + toggleH;
  }

  private boolean isOverLanguageToggle(int mx, int my) {
    int w = getWidth();
    int soundSecY = computeSoundSecY();
    int toggleW = 200;
    int toggleH = 42;
    int toggleX = (w - toggleW) / 2;
    int toggleY = soundSecY + 15;
    int musicToggleY = toggleY + toggleH + 12;
    int noticeY = musicToggleY + toggleH + 8;
    if ("true".equals(System.getProperty("cheerpj.browser"))) {
      noticeY += 20;
    }
    int langSecY = noticeY + 22;
    int langToggleY = langSecY + 15;
    return mx >= toggleX
        && mx <= toggleX + toggleW
        && my >= langToggleY
        && my <= langToggleY + toggleH;
  }

  private void updateHover(int mx, int my) {
    boolean oldBack = hoveredBack;
    boolean oldMute = hoveredMuteToggle;
    boolean oldMusic = hoveredMusicToggle;
    boolean oldLang = hoveredLanguageToggle;

    hoveredBack = isOverBackButton(mx, my);
    hoveredMuteToggle = isOverMuteToggle(mx, my);
    hoveredMusicToggle = isOverMusicToggle(mx, my);
    hoveredLanguageToggle = isOverLanguageToggle(mx, my);

    boolean interactable =
        hoveredBack || hoveredMuteToggle || hoveredMusicToggle || hoveredLanguageToggle;
    setCursor(
        interactable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

    if (oldBack != hoveredBack
        || oldMute != hoveredMuteToggle
        || oldMusic != hoveredMusicToggle
        || oldLang != hoveredLanguageToggle) {
      repaint();
    }
  }

  @SuppressWarnings("deprecation")
  private void handleClick(int mx, int my) {
    if (isOverMuteToggle(mx, my)) {
      audioManager.play(AudioManager.Sound.BUTTON_CLICK);
      boolean newMuted = !settings.isSoundMuted();
      settings.setSoundMuted(newMuted);
      audioManager.setMuted(newMuted);
      repaint();
      return;
    }
    if (isOverMusicToggle(mx, my)) {
      audioManager.play(AudioManager.Sound.BUTTON_CLICK);
      boolean newMusicMuted = !settings.isMusicMuted();
      settings.setMusicMuted(newMusicMuted);
      audioManager.setMusicMuted(newMusicMuted);
      repaint();
      return;
    }
    if (isOverLanguageToggle(mx, my)) {
      audioManager.play(AudioManager.Sound.BUTTON_CLICK);
      String current = settings.getLanguage();
      int idx = 0;
      for (int i = 0; i < LANG_CODES.length; i++) {
        if (LANG_CODES[i].equals(current)) {
          idx = i;
          break;
        }
      }
      String next = LANG_CODES[(idx + 1) % LANG_CODES.length];
      settings.setLanguage(next);
      Messages.setLocale(new java.util.Locale(next)); // NOSONAR — Locale.of() requires Java 19+
      repaint();
      return;
    }
    if (isOverBackButton(mx, my)) {
      audioManager.play(AudioManager.Sound.BUTTON_CLICK);
      onBack.run();
    }
  }
}
