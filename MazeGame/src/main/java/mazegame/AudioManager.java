package mazegame;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashMap;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;

/**
 * Manages synthesised sound effects and music playback for the game. Uses {@link
 * javax.sound.sampled} to generate short tones and play looping background music from classpath
 * audio files (MP3/OGG via SPI).
 *
 * <p>All playback is wrapped in try/catch so environments without audio support (e.g. CheerpJ)
 * degrade silently.
 */
public class AudioManager {

  /** Identifiers for the available sound effects. */
  public enum Sound {
    KEY_PICKUP,
    KEY_VANISHED,
    LOCKED_DOOR,
    DOOR_OPEN,
    LEVEL_COMPLETE,
    LEVEL_FAILED,
    LOW_TIME_WARNING,
    BUTTON_CLICK,
    DOG_TALK,
    BONE_PICKUP
  }

  private static final float SAMPLE_RATE = 44100f;
  private static final int BITS = 16;
  private static final int CHANNELS = 1;
  private static final AudioFormat FORMAT =
      new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, true, false);

  /** Classpath resource path for the menu music loop. */
  private static final String MENU_MUSIC = "Assets/music/menu/Game-Menu_Looping.ogg";

  /** Classpath resource paths for in-game music tracks (alternated between levels). */
  private static final String[] INGAME_MUSIC = {
    "Assets/music/ingame/Strange-Nature_Looping.mp3", "Assets/music/ingame/The-Trees-Wake-Up.mp3",
  };

  private final HashMap<Sound, byte[]> soundData = new HashMap<>();
  private boolean muted;
  private boolean musicMuted;
  private float musicVolume = 0.5f; // 0.0 – 1.0
  private boolean audioAvailable = true;
  private Clip musicClip;
  private String currentMusicPath;

  /** Creates a new AudioManager and pre-generates all sound effect samples. */
  public AudioManager() {
    try {
      generateSounds();
    } catch (Exception e) {
      System.out.println("Audio not available: " + e.getMessage());
      audioAvailable = false;
    }
  }

  /** Returns whether audio is currently muted. */
  public boolean isMuted() {
    return muted;
  }

  /** Sets the muted state. When muted, {@link #play(Sound)} is a no-op. */
  public void setMuted(boolean muted) {
    this.muted = muted;
  }

  /** Toggles the muted state and returns the new value. */
  public boolean toggleMute() {
    muted = !muted;
    return muted;
  }

  /**
   * Plays a sound effect asynchronously. Does nothing if muted or if audio is unavailable.
   *
   * @param sound the sound to play
   */
  public void play(Sound sound) {
    if (muted || !audioAvailable) return;
    byte[] data = soundData.get(sound);
    if (data == null) return;

    // Play on a daemon thread to avoid blocking the game loop
    Thread playThread =
        new Thread(
            () -> {
              try {
                Clip clip = AudioSystem.getClip();
                clip.open(FORMAT, data, 0, data.length);
                clip.start();
                // Close clip when done
                clip.addLineListener(
                    event -> {
                      if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                        clip.close();
                      }
                    });
              } catch (LineUnavailableException | IllegalArgumentException e) {
                // Silently degrade — audio not supported in this environment
              }
            });
    playThread.setDaemon(true);
    playThread.start();
  }

  // ---------------------------------------------------------------------------
  // Music playback
  // ---------------------------------------------------------------------------

  /** Returns whether music is currently muted. */
  public boolean isMusicMuted() {
    return musicMuted;
  }

  /** Sets whether music is muted. Stops/resumes the current track accordingly. */
  public void setMusicMuted(boolean muted) {
    this.musicMuted = muted;
    if (musicClip != null && musicClip.isOpen()) {
      if (muted) {
        musicClip.stop();
      } else {
        applyMusicVolume();
        musicClip.loop(Clip.LOOP_CONTINUOUSLY);
      }
    }
  }

  /** Returns the music volume (0.0 – 1.0). */
  public float getMusicVolume() {
    return musicVolume;
  }

  /**
   * Sets the music volume.
   *
   * @param volume a value between 0.0 (silent) and 1.0 (full)
   */
  public void setMusicVolume(float volume) {
    this.musicVolume = Math.max(0f, Math.min(1f, volume));
    if (musicClip != null && musicClip.isOpen()) {
      applyMusicVolume();
    }
  }

  /** Starts looping the main-menu music track. Stops any currently playing music first. */
  public void playMenuMusic() {
    playMusic(MENU_MUSIC);
  }

  /**
   * Starts looping an in-game music track, alternating between available tracks based on the level
   * number.
   *
   * @param level the current level number (1-based)
   */
  public void playIngameMusic(int level) {
    int idx = (level - 1) % INGAME_MUSIC.length;
    playMusic(INGAME_MUSIC[idx]);
  }

  /** Stops the currently playing music, if any. */
  public void stopMusic() {
    if (musicClip != null) {
      musicClip.stop();
      musicClip.close();
      musicClip = null;
    }
    currentMusicPath = null;
  }

  /**
   * Loads an audio file from the classpath, opens a Clip, and loops it continuously. The SPI
   * providers (mp3spi, vorbisspi) handle MP3/OGG decoding transparently.
   *
   * <p>If the requested track is already playing, the call is a no-op so that navigating between
   * menu screens does not restart the music.
   */
  private void playMusic(String resourcePath) {
    // Skip if the same track is already playing
    if (resourcePath.equals(currentMusicPath) && musicClip != null && musicClip.isRunning()) {
      return;
    }
    stopMusic();
    if (!audioAvailable || musicMuted) return;

    Thread loadThread =
        new Thread(
            () -> {
              try {
                InputStream raw = getClass().getResourceAsStream(resourcePath);
                if (raw == null) {
                  System.err.println("Music not found: " + resourcePath);
                  return;
                }
                BufferedInputStream buffered = new BufferedInputStream(raw);
                AudioInputStream sourceStream = AudioSystem.getAudioInputStream(buffered);

                // Decode to PCM so javax.sound.sampled can play it
                AudioFormat baseFormat = sourceStream.getFormat();
                AudioFormat decodedFormat =
                    new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false);
                AudioInputStream decodedStream =
                    AudioSystem.getAudioInputStream(decodedFormat, sourceStream);

                Clip clip = AudioSystem.getClip();
                clip.open(decodedStream);
                musicClip = clip;
                currentMusicPath = resourcePath;
                applyMusicVolume();
                clip.loop(Clip.LOOP_CONTINUOUSLY);
              } catch (Exception e) {
                System.err.println("Music playback failed: " + e.getMessage());
              }
            });
    loadThread.setDaemon(true);
    loadThread.start();
  }

  /** Applies the current {@link #musicVolume} to the active music clip's gain control. */
  private void applyMusicVolume() {
    if (musicClip == null || !musicClip.isOpen()) return;
    try {
      FloatControl gain = (FloatControl) musicClip.getControl(FloatControl.Type.MASTER_GAIN);
      // Convert linear 0–1 to decibels (−40 dB silence … 0 dB full)
      float dB;
      if (musicVolume <= 0f) {
        dB = gain.getMinimum();
      } else {
        dB = (float) (20.0 * Math.log10(musicVolume));
        dB = Math.max(dB, gain.getMinimum());
        dB = Math.min(dB, gain.getMaximum());
      }
      gain.setValue(dB);
    } catch (IllegalArgumentException e) {
      // Gain control not available — ignore
    }
  }

  // ---------------------------------------------------------------------------
  // Sound synthesis
  // ---------------------------------------------------------------------------

  private void generateSounds() {
    // Key pickup: bright ascending two-note chime (C5 → E5)
    soundData.put(Sound.KEY_PICKUP, twoTone(523.25, 659.25, 0.08, 0.08, 0.6));

    // Key vanished: descending minor tone (E5 → C5)
    soundData.put(Sound.KEY_VANISHED, twoTone(659.25, 440.0, 0.12, 0.12, 0.4));

    // Locked door: short low buzz (100 Hz square-ish wave)
    soundData.put(Sound.LOCKED_DOOR, buzz(150, 0.15, 0.5));

    // Door open: ascending chime (C4 → G4 → C5)
    soundData.put(Sound.DOOR_OPEN, threeTone(261.63, 392.0, 523.25, 0.08, 0.5));

    // Level complete: happy ascending arpeggio (C5 → E5 → G5 → C6)
    soundData.put(Sound.LEVEL_COMPLETE, arpeggio(new double[] {523, 659, 784, 1047}, 0.12, 0.5));

    // Level failed: descending minor (E4 → C4 → A3)
    soundData.put(Sound.LEVEL_FAILED, arpeggio(new double[] {330, 262, 220}, 0.15, 0.45));

    // Low time warning: short tick
    soundData.put(Sound.LOW_TIME_WARNING, sine(880, 0.05, 0.3));

    // Button click: short soft tap
    soundData.put(Sound.BUTTON_CLICK, sine(600, 0.04, 0.25));

    // Dog talk: short two-syllable bark/yap sound
    soundData.put(Sound.DOG_TALK, dogBark(0.3));

    // Bone pickup: satisfying crunch/munch sound
    soundData.put(Sound.BONE_PICKUP, boneCrunch(0.45));
  }

  /**
   * Synthesises a short dog-bark sound by layering a breathy noise burst with pitched harmonics.
   * Two short syllables give a "ruff-ruff" feel.
   */
  private byte[] dogBark(double volume) {
    // Two short bark syllables with a tiny gap
    byte[] yap1 = barkSyllable(320, 0.07, volume);
    byte[] gap = new byte[(int) (SAMPLE_RATE * 0.04) * 2]; // ~40ms silence
    byte[] yap2 = barkSyllable(380, 0.06, volume * 0.85);
    byte[] result = new byte[yap1.length + gap.length + yap2.length];
    System.arraycopy(yap1, 0, result, 0, yap1.length);
    System.arraycopy(gap, 0, result, yap1.length, gap.length);
    System.arraycopy(yap2, 0, result, yap1.length + gap.length, yap2.length);
    return result;
  }

  /**
   * A single bark syllable: a mix of a fundamental tone, an overtone, and filtered noise to create
   * a rough, breathy bark quality.
   */
  private byte[] barkSyllable(double freq, double durationSec, double volume) {
    int samples = (int) (SAMPLE_RATE * durationSec);
    byte[] buf = new byte[samples * 2];
    java.util.Random noiseRng = new java.util.Random(42);
    for (int i = 0; i < samples; i++) {
      double t = i / (double) SAMPLE_RATE;
      // Sharp attack, fast decay envelope
      double env;
      int attackLen = (int) (SAMPLE_RATE * 0.008);
      if (i < attackLen) {
        env = i / (double) attackLen;
      } else {
        double decay = (i - attackLen) / (double) (samples - attackLen);
        env = 1.0 - decay * decay; // quadratic decay
      }
      // Fundamental + overtone for tonal bark character
      double tone =
          Math.sin(2 * Math.PI * freq * t) * 0.5 + Math.sin(2 * Math.PI * freq * 1.5 * t) * 0.25;
      // Noise component for breathiness
      double noise = (noiseRng.nextDouble() * 2 - 1) * 0.25;
      double val = (tone + noise) * volume * env;
      val = Math.max(-1.0, Math.min(1.0, val));
      short sample = (short) (val * Short.MAX_VALUE);
      buf[i * 2] = (byte) (sample & 0xFF);
      buf[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    return buf;
  }

  /**
   * Generates a sine-wave tone.
   *
   * @param freq frequency in Hz
   * @param durationSec length in seconds
   * @param volume volume from 0.0 to 1.0
   */
  private byte[] sine(double freq, double durationSec, double volume) {
    int samples = (int) (SAMPLE_RATE * durationSec);
    byte[] buf = new byte[samples * 2]; // 16-bit = 2 bytes per sample
    for (int i = 0; i < samples; i++) {
      double t = i / (double) SAMPLE_RATE;
      double envelope = fadeEnvelope(i, samples);
      double val = Math.sin(2 * Math.PI * freq * t) * volume * envelope;
      short sample = (short) (val * Short.MAX_VALUE);
      buf[i * 2] = (byte) (sample & 0xFF);
      buf[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    return buf;
  }

  /** Generates a two-note tone (note1 then note2). */
  private byte[] twoTone(double freq1, double freq2, double dur1, double dur2, double volume) {
    byte[] a = sine(freq1, dur1, volume);
    byte[] b = sine(freq2, dur2, volume);
    byte[] result = new byte[a.length + b.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }

  /** Generates a three-note tone. */
  private byte[] threeTone(double freq1, double freq2, double freq3, double dur, double volume) {
    byte[] a = sine(freq1, dur, volume);
    byte[] b = sine(freq2, dur, volume);
    byte[] c = sine(freq3, dur, volume);
    byte[] result = new byte[a.length + b.length + c.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    System.arraycopy(c, 0, result, a.length + b.length, c.length);
    return result;
  }

  /** Generates an arpeggio from an array of frequencies. */
  private byte[] arpeggio(double[] freqs, double noteDur, double volume) {
    byte[][] notes = new byte[freqs.length][];
    int total = 0;
    for (int i = 0; i < freqs.length; i++) {
      notes[i] = sine(freqs[i], noteDur, volume);
      total += notes[i].length;
    }
    byte[] result = new byte[total];
    int offset = 0;
    for (byte[] note : notes) {
      System.arraycopy(note, 0, result, offset, note.length);
      offset += note.length;
    }
    return result;
  }

  /** Generates a buzzy/harsh tone using clipped sine (pseudo-square wave). */
  private byte[] buzz(double freq, double durationSec, double volume) {
    int samples = (int) (SAMPLE_RATE * durationSec);
    byte[] buf = new byte[samples * 2];
    for (int i = 0; i < samples; i++) {
      double t = i / (double) SAMPLE_RATE;
      double envelope = fadeEnvelope(i, samples);
      // Clipped sine for a harsher sound
      double raw = Math.sin(2 * Math.PI * freq * t);
      double val = Math.signum(raw) * Math.min(1.0, Math.abs(raw) * 2) * volume * envelope;
      short sample = (short) (val * Short.MAX_VALUE);
      buf[i * 2] = (byte) (sample & 0xFF);
      buf[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    return buf;
  }

  /**
   * Synthesises a satisfying crunch sound for bone pickup — a burst of filtered noise mixed with a
   * short bright tone for a "munch" quality.
   */
  private byte[] boneCrunch(double volume) {
    // Two quick crunches
    byte[] crunch1 = crunchSyllable(0.06, volume);
    byte[] gap = new byte[(int) (SAMPLE_RATE * 0.03) * 2];
    byte[] crunch2 = crunchSyllable(0.05, volume * 0.7);
    byte[] shimmer = sine(1200, 0.08, volume * 0.25);
    byte[] result = new byte[crunch1.length + gap.length + crunch2.length + shimmer.length];
    System.arraycopy(crunch1, 0, result, 0, crunch1.length);
    System.arraycopy(gap, 0, result, crunch1.length, gap.length);
    System.arraycopy(crunch2, 0, result, crunch1.length + gap.length, crunch2.length);
    // Mix shimmer into the tail by adding samples
    int shimmerStart = crunch1.length + gap.length + crunch2.length;
    System.arraycopy(shimmer, 0, result, shimmerStart, shimmer.length);
    return result;
  }

  /** A single crunch: burst of shaped noise with a tonal click. */
  private byte[] crunchSyllable(double durationSec, double volume) {
    int samples = (int) (SAMPLE_RATE * durationSec);
    byte[] buf = new byte[samples * 2];
    java.util.Random rng = new java.util.Random(77);
    for (int i = 0; i < samples; i++) {
      double t = i / (double) SAMPLE_RATE;
      // Very sharp attack, fast exponential decay
      double env;
      int attackLen = (int) (SAMPLE_RATE * 0.003);
      if (i < attackLen) {
        env = i / (double) attackLen;
      } else {
        double decay = (i - attackLen) / (double) (samples - attackLen);
        env = Math.exp(-decay * 5.0);
      }
      double noise = (rng.nextDouble() * 2 - 1) * 0.6;
      double click = Math.sin(2 * Math.PI * 800 * t) * 0.4;
      double val = (noise + click) * volume * env;
      val = Math.max(-1.0, Math.min(1.0, val));
      short sample = (short) (val * Short.MAX_VALUE);
      buf[i * 2] = (byte) (sample & 0xFF);
      buf[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    return buf;
  }

  /**
   * Simple fade-in/fade-out envelope to avoid clicks.
   *
   * @param sampleIdx current sample index
   * @param totalSamples total number of samples
   * @return amplitude multiplier (0.0..1.0)
   */
  private double fadeEnvelope(int sampleIdx, int totalSamples) {
    int fadeLen = Math.min(totalSamples / 8, (int) (SAMPLE_RATE * 0.005));
    if (fadeLen == 0) return 1.0;
    if (sampleIdx < fadeLen) {
      return sampleIdx / (double) fadeLen;
    } else if (sampleIdx > totalSamples - fadeLen) {
      return (totalSamples - sampleIdx) / (double) fadeLen;
    }
    return 1.0;
  }
}
