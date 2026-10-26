# Hexxit Boss Lab

Use `./lab.ps1` for the local workflow. Read `docs/development.md` before deploying, and `docs/testing.md` before testing in Minecraft. Read `docs/encounter-design.md` before designing or tuning a boss: it records the player's equipment and the rules every fight must hold. Each boss has its guide in `docs/bosses/`.

- Target Minecraft 1.5.2 and Forge 7.8.1.737. Compile shipping code to Java 6 using JDK 11; run the actual pack with its existing Java 8 runtime.
- Keep production code small and feature-oriented. Development automation belongs in the separate harness, never the shipped mod.
- Use the shared FML-backed logger for lifecycle events and failures.
- Run `check` and relevant runtime scenarios after changes. Static checks do not establish runtime compatibility or visual correctness.
- Formatting runs automatically through the commit hook. Commit self-contained milestones with conventional commit titles.
- Operate only on the marked lab instance. Never edit original saves or install into the original instance.
- Keep game files, saves, credentials, machine paths, and runtime evidence out of Git. Store local evidence under `.lab/`.
