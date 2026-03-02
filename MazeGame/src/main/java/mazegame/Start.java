package mazegame;

/**
 * Application entry point. Creates the game window and launches the main menu on the Event Dispatch
 * Thread.
 */
public class Start {

  private static final int WINDOW_SIZE = 650;
  private static final int INITIAL_GRID_SIZE = 10;

  /**
   * Launches Wesley's Way Out.
   *
   * @param args command-line arguments (unused)
   */
  @SuppressWarnings("deprecation")
  public static void main(String[] args) {
    MazeGame game = new MazeGame(WINDOW_SIZE, WINDOW_SIZE, INITIAL_GRID_SIZE);
    // Apply saved language preference
    Messages.setLocale(new java.util.Locale(game.getSettings().getLanguage()));
    javax.swing.SwingUtilities.invokeLater(game::runMenu);
  }
}
