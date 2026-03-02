package mazegame;

import java.util.ArrayList;
import java.util.Random;
import java.util.Stack;

/**
 * Generates a maze using the recursive backtracking algorithm. Extends {@link Tilemap} to build on
 * an initial all-walls grid, then carves passages, places an exit at the furthest reachable point,
 * and distributes key items.
 */
public class RecursiveBacktracker extends Tilemap {

  private final Tile[][] updateGrid;
  private Tile[][] exitTileSet;
  private Stack<Tile> visitedTiles = new Stack<>();
  private final int rowColAmount;
  private int maxSE;
  private final int minNW;
  private final int[] directions = {1, 2, 3, 4}; // N, E, S, W
  private int startingX;
  private int startingY;
  private int exitPathLength;
  private int biggestStack = 1;
  private TileExit furthestReached;
  private final int tileWH;
  private final ArrayList<TilePassage> keyTiles = new ArrayList<>();

  public RecursiveBacktracker(int tileWH, int tileBorder, int rowColAmount) {
    super(tileWH, tileBorder, rowColAmount);
    this.minNW = 1;
    this.rowColAmount = rowColAmount;
    this.tileWH = tileWH;
    this.exitPathLength = 1;
    this.updateGrid = super.getTileArr();
    this.maxSE = rowColAmount - 2;
  }

  /**
   * Runs the full maze generation pipeline: carve passages, place exit, distribute keys, and assign
   * wall sprite IDs.
   *
   * @return the completed tile grid
   */
  public Tile[][] startGeneration() {
    startingX = getRandomStartingCoord();
    startingY = getRandomStartingCoord();
    updateGrid[startingY][startingX] =
        new TilePassage(
            tileWH,
            updateGrid[startingY][startingX].getMinX(),
            updateGrid[startingY][startingX].getMinY());

    visitedTiles.push(new TilePassage(0, startingX, startingY));
    Tile[][] tiles = setWinningTile(carvePassage(startingX, startingY));
    tiles = addKeys(tiles);
    tiles = addWallIds(tiles);
    return tiles;
  }

  /**
   * Distributes key items randomly across passage tiles.
   *
   * @param allTiles the tile grid
   * @return the grid with keys placed
   */
  public Tile[][] addKeys(Tile[][] allTiles) {
    int keysRequired = (rowColAmount / 10) * 4;
    int keysAdded = 0;
    ArrayList<TilePassage> paths = new ArrayList<>();

    for (int i = 1; i < allTiles.length - 1; i++) {
      for (int j = 1; j < allTiles.length - 1; j++) {
        if (allTiles[i][j] instanceof TilePassage) {
          paths.add((TilePassage) allTiles[i][j]);
        }
      }
    }

    while (keysAdded < keysRequired) {
      int rndPath = new Random().nextInt(paths.size());
      TilePassage path = paths.get(rndPath);

      if (!path.hasItem()) {
        path.setItem(true);
        keyTiles.add(path);
        keysAdded++;
      }
    }

    return allTiles;
  }

  /**
   * Returns the list of passage tiles that contain keys.
   *
   * @return key tile coordinates
   */
  public ArrayList<TilePassage> getKeyCoords() {
    return keyTiles;
  }

  /**
   * Returns a random odd coordinate within the valid maze range. Maze cells must be at odd indices
   * for the backtracking algorithm.
   *
   * @return a random odd coordinate in {@code [1, rowColAmount - 2]}
   */
  public int getRandomStartingCoord() {

    /* Ex. Maze size 0-11 has valid cells 1-9 (and odd numbers only) */
    int endRange = rowColAmount - 2;
    int randomCoord = new Random().nextInt((endRange) + 1); // Ex. [1-9]
    if (randomCoord % 2 == 0) {
      randomCoord += 1;
    }

    return randomCoord;
  }

  public int getStartingX() {
    return startingX;
  }

  public int getStartingY() {
    return startingY;
  }

  /**
   * Recursively carves passages through the wall grid. For each direction (shuffled), attempts to
   * carve a two-cell corridor.
   *
   * @param x current x-coordinate
   * @param y current y-coordinate
   * @return the updated grid
   */
  public Tile[][] carvePassage(int x, int y) {
    shuffleDirection(directions);
    for (int i = 0; i < directions.length; i++) {
      isCellValid(x, y, directions[i]);
      // Check junctions here?
    }

    return updateGrid;
  }

  public Tile[][] setWinningTile(Tile[][] tileSet) {
    TilePassage tile = new TilePassage(0, 0, 0);
    visitedTiles.clear();
    if (tileSet[startingY][startingX] instanceof TilePassage) {
      tile = (TilePassage) tileSet[startingY][startingX];
    }
    tile.setRowNo(startingY);
    tile.setColNo(startingX);

    visitedTiles.push(tile);
    exitTileSet = tileSet;
    // X+2 == South
    // X-2 == North
    // Y-2 == West
    // Y+2 == East

    TilePassage tp = (TilePassage) exitTileSet[startingY][startingX];
    tp.setCheckedExitPath(true);
    exitTileSet[startingY][startingX] = tp;

    setExitPath(startingY, startingX);
    return exitTileSet;
  }

  /**
   * Iteratively finds the exit placement by exploring all passages from the starting point,
   * tracking the furthest reachable cell. Replaces the original recursive implementation to avoid
   * {@code StackOverflowError} on large or deeply-branching mazes.
   */
  public void setExitPath(int startRow, int startCol) {
    long pathCount = super.getPassageCount(exitTileSet);
    int cX = startRow;
    int cY = startCol;

    outer:
    while (true) {
      if (visitedTiles.size() > biggestStack) {
        biggestStack = visitedTiles.size();
        TilePassage e = (TilePassage) visitedTiles.peek();
        furthestReached = new TileExit(e.getSize(), e.getMinX(), e.getMinY());
        furthestReached.setRowNo(e.getRowNo());
        furthestReached.setColNo(e.getColNo());
      }

      if (exitPathLength > pathCount / 2) {
        visitedTiles.push(furthestReached);
        exitTileSet[furthestReached.getRowNo()][furthestReached.getColNo()] = furthestReached;
        visitedTiles.clear();
        return;
      }

      shuffleDirection(directions);

      for (int i = 0; i < directions.length; i++) {
        int direction = directions[i];
        if (checkPath(direction, cX, cY)) {
          if (direction == 1) {
            cX = cX - 1;
          } else if (direction == 2) {
            cY = cY + 1;
          } else if (direction == 3) {
            cX = cX + 1;
          } else if (direction == 4) {
            cY = cY - 1;
          }
          continue outer;
        }
      }

      // Dead end — backtrack
      if (visitedTiles.size() <= 1) {
        return; // exhausted all paths
      }
      Tile t = visitedTiles.pop();
      TilePassage tp = (TilePassage) t;
      cX = tp.getRowNo();
      cY = tp.getColNo();
    }
  }

  public boolean checkPath(int dir, int cX, int cY) {
    TilePassage t;
    switch (dir) {
      case 1:
        // North -- Check tile is in a valid range and hasn't been visited already.
        if ((cX - 1 >= minNW)) {
          if (exitTileSet[cX - 1][cY] instanceof TilePassage) {
            TilePassage tp = (TilePassage) exitTileSet[cX - 1][cY];
            if (!tp.getCheckedExitPath()) {
              tp.setCheckedExitPath(true);
              exitTileSet[cX - 1][cY] = tp;
              exitPathLength++;
              t = (TilePassage) exitTileSet[cX - 1][cY];
              t.setColNo(cY);
              t.setRowNo(cX - 1);
              visitedTiles.push(t);
              return true;
            }
          }
        }
        break;
      case 2:
        // East
        if (cY + 1 <= maxSE) {
          if (exitTileSet[cX][cY + 1] instanceof TilePassage) {
            TilePassage tp = (TilePassage) exitTileSet[cX][cY + 1];
            if (!tp.getCheckedExitPath()) {
              tp.setCheckedExitPath(true);
              exitTileSet[cX][cY + 1] = tp;
              exitPathLength++;
              t = (TilePassage) exitTileSet[cX][cY + 1];
              t.setColNo(cY + 1);
              t.setRowNo(cX);
              visitedTiles.push(t);
              return true;
            }
          }
        }
        break;
      case 3:
        // South
        if (cX + 1 <= maxSE) {

          if (exitTileSet[cX + 1][cY] instanceof TilePassage) {
            TilePassage tp = (TilePassage) exitTileSet[cX + 1][cY];
            if (!tp.getCheckedExitPath()) {
              tp.setCheckedExitPath(true);
              exitTileSet[cX + 1][cY] = tp;
              exitPathLength++;
              t = (TilePassage) exitTileSet[cX + 1][cY];
              t.setColNo(cY);
              t.setRowNo(cX + 1);
              visitedTiles.push(t);
              return true;
            }
          }
        }
        break;
      case 4:
        // West
        if (cY - 1 >= minNW) {
          if (exitTileSet[cX][cY - 1] instanceof TilePassage) {
            TilePassage tp = (TilePassage) exitTileSet[cX][cY - 1];
            if (!tp.getCheckedExitPath()) {
              tp.setCheckedExitPath(true);
              exitTileSet[cX][cY - 1] = tp;
              exitPathLength++;
              t = (TilePassage) exitTileSet[cX][cY - 1];
              t.setColNo(cY - 1);
              t.setRowNo(cX);
              visitedTiles.push(t);
              return true;
            }
          }
        }
        break;
      default:
        break;
    }
    return false;
  }

  private void shuffleDirection(int[] array) {
    int index;
    Random rand = new Random();
    for (int i = 0; i < array.length; i++) {
      index = rand.nextInt(i + 1);
      if (index != i) {
        array[index] ^= array[i];
        array[i] ^= array[index];
        array[index] ^= array[i];
      }
    }
  }

  public void isCellValid(int x, int y, int direction) {
    switch (direction) {

      // North
      case 1:

        // If northern cell is within maze range and is not already a passage.
        if (!((y <= minNW) || (updateGrid[y - 2][x] instanceof TilePassage))) {

          visitedTiles.push(new TilePassage(0, x, y));

          updateGrid[y - 2][x] =
              new TilePassage(
                  tileWH, updateGrid[y - 2][x].getMinX(), updateGrid[y - 2][x].getMinY());

          updateGrid[y - 1][x] =
              new TilePassage(
                  tileWH, updateGrid[y - 1][x].getMinX(), updateGrid[y - 1][x].getMinY());

          carvePassage(x, y - 2);
        }
        break;

      // East
      case 2:
        if (!((x >= maxSE) || (updateGrid[y][x + 2] instanceof TilePassage))) {
          visitedTiles.push(new TilePassage(0, x, y));

          updateGrid[y][x + 2] =
              new TilePassage(
                  tileWH, updateGrid[y][x + 2].getMinX(), updateGrid[y][x + 2].getMinY());

          updateGrid[y][x + 1] =
              new TilePassage(
                  tileWH, updateGrid[y][x + 1].getMinX(), updateGrid[y][x + 1].getMinY());

          carvePassage(x + 2, y);
        }
        break;

      // South
      case 3:
        if (!((y >= maxSE) || (updateGrid[y + 2][x] instanceof TilePassage))) {
          visitedTiles.push(new TilePassage(0, x, y));

          updateGrid[y + 2][x] =
              new TilePassage(
                  tileWH, updateGrid[y + 2][x].getMinX(), updateGrid[y + 2][x].getMinY());

          updateGrid[y + 1][x] =
              new TilePassage(
                  tileWH, updateGrid[y + 1][x].getMinX(), updateGrid[y + 1][x].getMinY());

          carvePassage(x, y + 2);
        }
        break;

      // West
      case 4:
        if (!((x <= minNW) || (updateGrid[y][x - 2] instanceof TilePassage))) {
          visitedTiles.push(new TilePassage(0, x, y));

          updateGrid[y][x - 2] =
              new TilePassage(
                  tileWH, updateGrid[y][x - 2].getMinX(), updateGrid[y][x - 2].getMinY());

          updateGrid[y][x - 1] =
              new TilePassage(
                  tileWH, updateGrid[y][x - 1].getMinX(), updateGrid[y][x - 1].getMinY());

          carvePassage(x - 2, y);
        }
    }
  }

  /**
   * Assigns NESW neighbour bitmask strings to all wall tiles for sprite selection.
   *
   * @param tileSet the tile grid
   * @return the grid with wall IDs set
   */
  public Tile[][] addWallIds(Tile[][] tileSet) {
    int neighbours;

    for (int i = 0; i < tileSet.length; i++) {

      for (int j = 0; j < tileSet.length; j++) {
        if ((tileSet[i][j]) instanceof TileWall) { // If current tile is a wall
          neighbours = 0;

          if (((i - 1) >= 0 && (i - 1) <= tileSet.length)
              && ((tileSet[i - 1][j]) instanceof TilePassage)) { // Check if north tile is a passage
            neighbours += Integer.parseInt("1000", 2);
          }

          if (((j + 1) >= 0 && (j + 1) < tileSet.length)
              && ((tileSet[i][j + 1]) instanceof TilePassage)) { // Check if east tile is a passage
            neighbours += Integer.parseInt("0100", 2);
          }

          if (((i + 1) >= 0 && (i + 1) < tileSet.length)
              && ((tileSet[i + 1][j]) instanceof TilePassage)) { // Check if south tile is a passage
            neighbours += Integer.parseInt("0010", 2);
          }

          if (((j - 1) >= 0 && (j - 1) <= tileSet.length)
              && ((tileSet[i][j - 1]) instanceof TilePassage)) { // Check if west tile is a passage
            neighbours += Integer.parseInt("0001", 2);
          }

          TileWall t = (TileWall) tileSet[i][j];

          String binaryString = Integer.toBinaryString(neighbours);
          while (binaryString.length() < 4) {
            binaryString = "0" + binaryString;
          }
          t.setPassageNeighbours(binaryString);
          tileSet[i][j] = t;
        }
      }
    }

    return tileSet;
  }
}
