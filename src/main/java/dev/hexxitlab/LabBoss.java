package dev.hexxitlab;

/** A summonable encounter managed by lab commands and practice checkpoints. */
public interface LabBoss {
  /** One status line for {@code /bosslab status}. */
  String describe();
}
