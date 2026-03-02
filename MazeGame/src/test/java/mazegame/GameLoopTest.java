package mazegame;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameLoopTest {

  /** Minimal stub implementation of GameLoop.Callbacks for testing. */
  private static class StubCallbacks implements GameLoop.Callbacks {
    volatile boolean gameInProgress = true;
    volatile boolean paused = false;

    @Override
    public void onUpdate() {}

    @Override
    public void onRender() {}

    @Override
    public void onAnimationTick() {}

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
      return false;
    }
  }

  @Test
  @DisplayName("GameLoop starts and reports isRunning")
  void startsAndReportsRunning() throws Exception {
    StubCallbacks cb = new StubCallbacks();
    GameLoop loop = new GameLoop(cb);

    assertFalse(loop.isRunning(), "Should not be running before start");
    loop.start();
    assertTrue(loop.isRunning(), "Should be running after start");
    loop.stop();
  }

  @Test
  @DisplayName("stop sets isRunning to false")
  void stopSetsNotRunning() {
    StubCallbacks cb = new StubCallbacks();
    GameLoop loop = new GameLoop(cb);

    loop.start();
    loop.stop();
    assertFalse(loop.isRunning(), "Should not be running after stop");
  }

  @Test
  @DisplayName("join also stops the loop")
  void joinStopsLoop() {
    StubCallbacks cb = new StubCallbacks();
    GameLoop loop = new GameLoop(cb);

    loop.start();
    loop.join();
    assertFalse(loop.isRunning());
  }

  @Test
  @DisplayName("Double start is a no-op")
  void doubleStartIsNoOp() {
    StubCallbacks cb = new StubCallbacks();
    GameLoop loop = new GameLoop(cb);

    loop.start();
    assertTrue(loop.isRunning());
    loop.start(); // second call should not crash
    assertTrue(loop.isRunning());
    loop.stop();
  }

  @Test
  @DisplayName("stop before start is safe")
  void stopBeforeStartIsSafe() {
    StubCallbacks cb = new StubCallbacks();
    GameLoop loop = new GameLoop(cb);
    assertDoesNotThrow(loop::stop);
  }

  @Test
  @DisplayName("join before start is safe")
  void joinBeforeStartIsSafe() {
    StubCallbacks cb = new StubCallbacks();
    GameLoop loop = new GameLoop(cb);
    assertDoesNotThrow(loop::join);
  }

  @Test
  @DisplayName("onComplete fires when isGameInProgress returns false")
  void onCompleteFiresWhenGameEnds() throws Exception {
    StubCallbacks cb = new StubCallbacks();
    cb.gameInProgress = false; // game immediately not in progress

    CountDownLatch latch = new CountDownLatch(1);
    GameLoop loop = new GameLoop(cb);
    loop.setOnComplete(latch::countDown);
    loop.start();

    // The timer fires on EDT; give it a moment
    assertTrue(latch.await(2, TimeUnit.SECONDS), "onComplete should have been called");
    assertFalse(loop.isRunning());
  }
}
