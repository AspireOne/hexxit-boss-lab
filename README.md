# Hexxit Boss Lab

A local workshop for Minecraft 1.5.2 / Forge 7.8.1.737 experiments.

```powershell
.\lab.ps1 check   # Compile, static analysis, compatibility and workflow tests
.\lab.ps1 play    # Check, build, install into the isolated lab, launch Prism
.\lab.ps1 smoke   # Automated two-launch test against the actual modpack
```

The lab uses **Kratom - Boss Lab**, copied from the most recently played save. Summon the [Hexbound Sovereign](docs/features/sovereign/README.md) with `/bosslab sovereign`, inspect it with `/bosslab status`, or end the fight with `/bosslab clear`. `/bosslab spawn` still provides the harmless practice target.

See the [development guide](docs/features/development-foundation/README.md) for setup, testing, recovery, and the autonomous agent workflow.
