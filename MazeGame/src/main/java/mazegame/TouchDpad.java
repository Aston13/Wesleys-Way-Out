package mazegame;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws a translucent directional pad and pause button overlay for touch / mobile browser play. All
 * coordinates are in the logical game space (650×650).
 *
 * <p>The D-pad is positioned in the bottom-right corner; the pause icon sits in the top-right.
 */
public final class TouchDpad {

  /** Direction constants returned by {@link #hitTestDpad}. */
  public static final int NONE = -1;

  public static final int NORTH = 0;
  public static final int EAST = 1;
  public static final int SOUTH = 2;
  public static final int WEST = 3;

  // Layout — all values relative to logical game size
  private static final int DPAD_SIZE = 130;
  private static final int DPAD_MARGIN = 18;
  private static final int ARROW_INSET = 8;

  private static final int PAUSE_SIZE = 40;
  private static final int PAUSE_MARGIN = 14;

  private static final Color BG = new Color(0, 0, 0, 80);
  private static final Color ARROW_COLOR = new Color(240, 236, 232, 180);
  private static final Color ARROW_PRESSED = new Color(240, 236, 232, 240);
  private static final Color BORDER = new Color(255, 255, 255, 40);
  private static final Color PAUSE_BAR = new Color(240, 236, 232, 200);

  private TouchDpad() {}

  // ---- Rendering ----

  /**
   * Draws the directional pad in the bottom-right of the game area.
   *
   * @param g2 the graphics context (logical coords)
   * @param gameW logical game width
   * @param gameH logical game height
   * @param activeDir currently pressed direction ({@link #NORTH}..{@link #WEST}) or {@link #NONE}
   */
  public static void drawDpad(Graphics2D g2, int gameW, int gameH, int activeDir) {
    Composite origComp = g2.getComposite();
    Stroke origStroke = g2.getStroke();
    Object origAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int cx = gameW - DPAD_MARGIN - DPAD_SIZE / 2;
    int cy = gameH - DPAD_MARGIN - DPAD_SIZE / 2;
    int r = DPAD_SIZE / 2;

    // Background circle
    g2.setColor(BG);
    g2.fill(new Ellipse2D.Double(cx - r, cy - r, DPAD_SIZE, DPAD_SIZE));
    g2.setColor(BORDER);
    g2.setStroke(new BasicStroke(1.5f));
    g2.draw(new Ellipse2D.Double(cx - r, cy - r, DPAD_SIZE, DPAD_SIZE));

    // Divider cross (thin lines separating quadrants)
    g2.setStroke(new BasicStroke(0.8f));
    g2.setColor(BORDER);
    // Diagonal top-left to bottom-right
    // Actually, draw X shape from centre to edges (the 4 quadrant separators)
    // North-East / South-West and North-West / South-East
    int len = r - 4;
    g2.drawLine(cx - len, cy - len, cx + len, cy + len);
    g2.drawLine(cx + len, cy - len, cx - len, cy + len);

    // Draw each directional arrow with highlight if active
    drawArrow(g2, cx, cy, r, NORTH, activeDir == NORTH);
    drawArrow(g2, cx, cy, r, EAST, activeDir == EAST);
    drawArrow(g2, cx, cy, r, SOUTH, activeDir == SOUTH);
    drawArrow(g2, cx, cy, r, WEST, activeDir == WEST);

    g2.setStroke(origStroke);
    g2.setComposite(origComp);
    if (origAA != null) {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, origAA);
    }
  }

  /**
   * Draws the pause icon in the top-right of the game area.
   *
   * @param g2 the graphics context (logical coords)
   * @param gameW logical game width
   */
  public static void drawPauseIcon(Graphics2D g2, int gameW) {
    Composite origComp = g2.getComposite();
    Stroke origStroke = g2.getStroke();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int x = gameW - PAUSE_MARGIN - PAUSE_SIZE;
    int y = PAUSE_MARGIN;

    // Background rounded rect
    RoundRectangle2D.Double bg = new RoundRectangle2D.Double(x, y, PAUSE_SIZE, PAUSE_SIZE, 10, 10);
    g2.setColor(BG);
    g2.fill(bg);
    g2.setColor(BORDER);
    g2.setStroke(new BasicStroke(1.5f));
    g2.draw(bg);

    // Two vertical bars
    int barW = 5;
    int barH = PAUSE_SIZE / 2;
    int barY = y + (PAUSE_SIZE - barH) / 2;
    int gap = 4;
    int cx = x + PAUSE_SIZE / 2;
    g2.setColor(PAUSE_BAR);
    g2.fillRoundRect(cx - gap - barW, barY, barW, barH, 2, 2);
    g2.fillRoundRect(cx + gap, barY, barW, barH, 2, 2);

    g2.setStroke(origStroke);
    g2.setComposite(origComp);
  }

  // ---- Hit testing ----

  /**
   * Tests whether the given logical coordinate hits one of the four D-pad quadrants.
   *
   * @return {@link #NORTH}, {@link #EAST}, {@link #SOUTH}, {@link #WEST}, or {@link #NONE}
   */
  public static int hitTestDpad(int lx, int ly, int gameW, int gameH) {
    int cx = gameW - DPAD_MARGIN - DPAD_SIZE / 2;
    int cy = gameH - DPAD_MARGIN - DPAD_SIZE / 2;
    int r = DPAD_SIZE / 2;

    double dx = lx - cx;
    double dy = ly - cy;
    if (dx * dx + dy * dy > (double) r * r) return NONE;

    // Determine quadrant — use the diagonal lines y=x and y=-x through centre
    if (dy < -Math.abs(dx)) return NORTH;
    if (dy > Math.abs(dx)) return SOUTH;
    if (dx > Math.abs(dy)) return EAST;
    if (dx < -Math.abs(dy)) return WEST;
    return NONE; // dead centre
  }

  /**
   * Tests whether the given logical coordinate hits the pause icon.
   *
   * @return true if the point is inside the pause button bounds
   */
  public static boolean hitTestPause(int lx, int ly, int gameW) {
    int x = gameW - PAUSE_MARGIN - PAUSE_SIZE;
    int y = PAUSE_MARGIN;
    return lx >= x && lx <= x + PAUSE_SIZE && ly >= y && ly <= y + PAUSE_SIZE;
  }

  // ---- Internal arrow drawing ----

  private static void drawArrow(Graphics2D g2, int cx, int cy, int r, int dir, boolean pressed) {
    if (pressed) {
      // Highlight the quadrant
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
      g2.setColor(new Color(196, 149, 106));
      fillQuadrant(g2, cx, cy, r, dir);
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    // Arrow triangle
    Path2D arrow = new Path2D.Double();
    int inset = ARROW_INSET;
    int aLen = r / 3; // half-width of the arrow base
    int aTip = r - inset - 4; // distance from centre to tip

    switch (dir) {
      case NORTH:
        arrow.moveTo(cx, cy - aTip);
        arrow.lineTo(cx - aLen, cy - aTip + aLen + 4);
        arrow.lineTo(cx + aLen, cy - aTip + aLen + 4);
        break;
      case SOUTH:
        arrow.moveTo(cx, cy + aTip);
        arrow.lineTo(cx - aLen, cy + aTip - aLen - 4);
        arrow.lineTo(cx + aLen, cy + aTip - aLen - 4);
        break;
      case EAST:
        arrow.moveTo(cx + aTip, cy);
        arrow.lineTo(cx + aTip - aLen - 4, cy - aLen);
        arrow.lineTo(cx + aTip - aLen - 4, cy + aLen);
        break;
      case WEST:
        arrow.moveTo(cx - aTip, cy);
        arrow.lineTo(cx - aTip + aLen + 4, cy - aLen);
        arrow.lineTo(cx - aTip + aLen + 4, cy + aLen);
        break;
      default:
        return;
    }
    arrow.closePath();

    g2.setColor(pressed ? ARROW_PRESSED : ARROW_COLOR);
    g2.fill(arrow);
  }

  /** Fills a pie-slice quadrant of the D-pad circle for the pressed highlight. */
  private static void fillQuadrant(Graphics2D g2, int cx, int cy, int r, int dir) {
    Path2D quad = new Path2D.Double();
    quad.moveTo(cx, cy);
    double rr = r;
    int segments = 16;
    double startAngle;
    double endAngle;
    switch (dir) {
      case NORTH:
        startAngle = Math.toRadians(225);
        endAngle = Math.toRadians(315);
        break;
      case EAST:
        startAngle = Math.toRadians(315);
        endAngle = Math.toRadians(405);
        break;
      case SOUTH:
        startAngle = Math.toRadians(45);
        endAngle = Math.toRadians(135);
        break;
      case WEST:
        startAngle = Math.toRadians(135);
        endAngle = Math.toRadians(225);
        break;
      default:
        return;
    }
    for (int i = 0; i <= segments; i++) {
      double a = startAngle + (endAngle - startAngle) * i / segments;
      quad.lineTo(cx + rr * Math.cos(a), cy - rr * Math.sin(a));
    }
    quad.closePath();
    g2.fill(quad);
  }
}
