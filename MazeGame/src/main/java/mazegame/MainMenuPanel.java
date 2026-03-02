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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A custom-painted main menu panel with gradient background, animated title, and styled buttons
 * with hover effects. Replaces the old null-layout + plain JButton approach.
 */
public class MainMenuPanel extends JPanel {

  private static final Color BG_TOP = new Color(26, 26, 26);
  private static final Color BG_BOTTOM = new Color(34, 30, 28);
  private static final Color TITLE_COLOR = new Color(240, 236, 232);
  private static final Color TITLE_SHADOW = new Color(80, 60, 40);
  private static final Color TITLE_GLOW = new Color(196, 149, 106, 60);
  private static final Color ACCENT_LINE = new Color(196, 149, 106);
  private static final Color ACCENT_LINE_FADE = new Color(196, 149, 106, 0);
  private static final Color SUBTITLE_COLOR = new Color(160, 145, 130);

  private static final int TITLE_FONT_SIZE = 42;
  private static final int SUBTITLE_FONT_SIZE = 14;
  private static final int PARTICLE_COUNT = 25;
  private static final int PARTICLE_TICK_MS = 50;

  private final List<MenuButton> buttons = new ArrayList<>();
  private final AssetManager assetManager;
  private final double[] particleX = new double[PARTICLE_COUNT];
  private final double[] particleY = new double[PARTICLE_COUNT];
  private final double[] particleSpeed = new double[PARTICLE_COUNT];
  private final double[] particleAlpha = new double[PARTICLE_COUNT];
  private final double[] particleSize = new double[PARTICLE_COUNT];
  private final Random particleRng = new Random();
  private Timer particleTimer;
  private int hoveredIndex = -1;
  private BufferedImage decorationImage;

  /** Describes a clickable button on a custom-painted menu. */
  static class MenuButton {
    private final String label;
    private final String hint;
    private final Runnable action;

    MenuButton(String label, String hint, Runnable action) {
      this.label = label;
      this.hint = hint;
      this.action = action;
    }

    String label() {
      return label;
    }

    String hint() {
      return hint;
    }

    Runnable action() {
      return action;
    }
  }

  public MainMenuPanel(AudioManager audioManager, AssetManager assetManager) {
    this.assetManager = assetManager;
    setOpaque(true);
    setBackground(BG_TOP);

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
            int idx = getButtonIndex(e.getX(), e.getY());
            if (idx != hoveredIndex) {
              hoveredIndex = idx;
              setCursor(
                  idx >= 0
                      ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                      : Cursor.getDefaultCursor());
              repaint();
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            int idx = getButtonIndex(e.getX(), e.getY());
            if (idx >= 0 && idx < buttons.size()) {
              if (audioManager != null) audioManager.play(AudioManager.Sound.BUTTON_CLICK);
              buttons.get(idx).action().run();
            }
          }

          @Override
          public void mouseExited(MouseEvent e) {
            if (hoveredIndex != -1) {
              hoveredIndex = -1;
              setCursor(Cursor.getDefaultCursor());
              repaint();
            }
          }
        };
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);
  }

  /** Sets a decorative sprite image (e.g. Wesley) to display between the subtitle and buttons. */
  public void setDecorationImage(BufferedImage image) {
    this.decorationImage = image;
    repaint();
  }

  /** Resets a particle to a random starting position. */
  private void resetParticle(int i, boolean randomY) {
    particleX[i] = particleRng.nextDouble() * 800;
    particleY[i] = randomY ? particleRng.nextDouble() * 800 : 780 + particleRng.nextDouble() * 40;
    particleSpeed[i] = 0.3 + particleRng.nextDouble() * 0.7;
    particleAlpha[i] = 0.10 + particleRng.nextDouble() * 0.20;
    particleSize[i] = 2 + particleRng.nextDouble() * 3;
  }

  /** Adds a button to the menu. */
  public void addButton(String label, String hint, Runnable action) {
    buttons.add(new MenuButton(label, hint, action));
    repaint();
  }

  @Override
  public Dimension getPreferredSize() {
    // Return parent's preferred size (the frame will set this)
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
    g.setColor(new Color(255, 255, 255, 6));
    for (int x = 0; x < w; x += 40) {
      g.drawLine(x, 0, x, h);
    }
    for (int y = 0; y < h; y += 40) {
      g.drawLine(0, y, w, y);
    }

    // Floating particles (subtle ambient dots drifting upward)
    Composite origComp = g.getComposite();
    for (int i = 0; i < PARTICLE_COUNT; i++) {
      float alpha = (float) Math.max(0, Math.min(1, particleAlpha[i]));
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      g.setColor(ACCENT_LINE);
      double px = particleX[i] / 800.0 * w;
      double py = particleY[i] / 800.0 * h;
      int sz = (int) particleSize[i];
      g.fillOval((int) px, (int) py, sz, sz);
    }
    g.setComposite(origComp);

    // Title — rendered with glow, shadow layers, and letter spacing for a premium feel
    Font titleFont = new Font("Dialog", Font.BOLD, TITLE_FONT_SIZE);
    g.setFont(titleFont);
    FontMetrics titleFm = g.getFontMetrics();
    String title = Messages.get("title.game_name");
    int titleY = h / 5 + titleFm.getAscent() / 2;

    // Measure total width with letter spacing
    int letterSpacing = 3;
    int totalTitleWidth = 0;
    for (int i = 0; i < title.length(); i++) {
      totalTitleWidth += titleFm.charWidth(title.charAt(i));
      if (i < title.length() - 1) totalTitleWidth += letterSpacing;
    }
    int titleX = (w - totalTitleWidth) / 2;

    // Warm glow layer (drawn slightly larger behind)
    Composite origComposite = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
    g.setColor(TITLE_GLOW);
    Font glowFont = titleFont.deriveFont((float) (TITLE_FONT_SIZE + 2));
    g.setFont(glowFont);
    FontMetrics glowFm = g.getFontMetrics();
    int glowTotalWidth = 0;
    for (int i = 0; i < title.length(); i++) {
      glowTotalWidth += glowFm.charWidth(title.charAt(i));
      if (i < title.length() - 1) glowTotalWidth += letterSpacing;
    }
    int glowX = (w - glowTotalWidth) / 2;
    drawStringSpaced(g, title, glowX - 1, titleY + 1, letterSpacing);
    drawStringSpaced(g, title, glowX + 1, titleY - 1, letterSpacing);
    g.setComposite(origComposite);

    // Shadow layer
    g.setFont(titleFont);
    g.setColor(TITLE_SHADOW);
    drawStringSpaced(g, title, titleX + 2, titleY + 2, letterSpacing);

    // Main title text
    g.setColor(TITLE_COLOR);
    drawStringSpaced(g, title, titleX, titleY, letterSpacing);

    // Accent line (horizontal gradient underline beneath title)
    int lineY = titleY + 10;
    int lineHalfW = totalTitleWidth / 2 + 20;
    int lineCenter = w / 2;
    g.setPaint(
        new GradientPaint(
            lineCenter - lineHalfW, lineY, ACCENT_LINE_FADE, lineCenter, lineY, ACCENT_LINE));
    g.fillRect(lineCenter - lineHalfW, lineY, lineHalfW, 2);
    g.setPaint(
        new GradientPaint(
            lineCenter, lineY, ACCENT_LINE, lineCenter + lineHalfW, lineY, ACCENT_LINE_FADE));
    g.fillRect(lineCenter, lineY, lineHalfW, 2);
    // Small diamond accent at centre
    g.setColor(ACCENT_LINE);
    int dx = lineCenter;
    int dy = lineY + 1;
    g.fillPolygon(new int[] {dx - 4, dx, dx + 4, dx}, new int[] {dy, dy - 4, dy, dy + 4}, 4);

    // Subtitle
    Font subtitleFont = new Font("Dialog", Font.PLAIN, SUBTITLE_FONT_SIZE);
    g.setFont(subtitleFont);
    FontMetrics subFm = g.getFontMetrics();
    String subtitle = Messages.get("title.subtitle");
    g.setColor(SUBTITLE_COLOR);
    g.drawString(subtitle, (w - subFm.stringWidth(subtitle)) / 2, titleY + 30);

    // Wesley decoration sprite (pixel-art, centered between subtitle and buttons)
    if (decorationImage != null) {
      int imgSize = Math.max(80, h / 6);
      int imgX = (w - imgSize) / 2;
      int imgY = titleY + 40;
      Composite oldComposite = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(decorationImage, imgX, imgY, imgSize, imgSize, null);
      g.setComposite(oldComposite);
    }

    // Buttons (rendered via shared UiTheme for consistency across all screens)
    int totalBtnHeight =
        buttons.size() * UiTheme.STD_BTN_HEIGHT + (buttons.size() - 1) * UiTheme.STD_BTN_GAP;
    int startY = (h / 2) + (h / 2 - totalBtnHeight) / 2 - 20;

    for (int i = 0; i < buttons.size(); i++) {
      MenuButton btn = buttons.get(i);
      int btnX = (w - UiTheme.STD_BTN_WIDTH) / 2;
      int btnY = startY + i * (UiTheme.STD_BTN_HEIGHT + UiTheme.STD_BTN_GAP);
      UiTheme.paintStdButton(
          g,
          btnX,
          btnY,
          btn.label(),
          btn.hint(),
          i == hoveredIndex,
          assetManager != null ? assetManager.getKeyFrame() : null);
    }

    // Version label (bottom-right corner)
    String versionText = Messages.fmt("label.version", BuildInfo.getVersion());
    Font versionFont = new Font("Dialog", Font.PLAIN, 11);
    g.setFont(versionFont);
    FontMetrics vFm = g.getFontMetrics();
    g.setColor(new Color(120, 110, 100));
    g.drawString(versionText, w - vFm.stringWidth(versionText) - 10, h - 10);
  }

  private int getButtonIndex(int mx, int my) {
    int w = getWidth();
    int h = getHeight();
    int totalBtnHeight =
        buttons.size() * UiTheme.STD_BTN_HEIGHT + (buttons.size() - 1) * UiTheme.STD_BTN_GAP;
    int startY = (h / 2) + (h / 2 - totalBtnHeight) / 2 - 20;

    for (int i = 0; i < buttons.size(); i++) {
      int btnX = (w - UiTheme.STD_BTN_WIDTH) / 2;
      int btnY = startY + i * (UiTheme.STD_BTN_HEIGHT + UiTheme.STD_BTN_GAP);
      if (mx >= btnX
          && mx <= btnX + UiTheme.STD_BTN_WIDTH
          && my >= btnY
          && my <= btnY + UiTheme.STD_BTN_HEIGHT) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Draws a string with custom letter spacing.
   *
   * @param g the graphics context (font and color must already be set)
   * @param text the string to draw
   * @param x starting x position
   * @param y baseline y position
   * @param spacing extra pixels between each character
   */
  private void drawStringSpaced(Graphics2D g, String text, int x, int y, int spacing) {
    FontMetrics fm = g.getFontMetrics();
    int cx = x;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      g.drawString(String.valueOf(ch), cx, y);
      cx += fm.charWidth(ch) + spacing;
    }
  }
}
