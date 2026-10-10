# KingFeatures v0.1.3 — Zero-Setup Rank Pack Fix

The player only installs `KingFeatures-0.1.3.jar` in `/plugins`.

- The rank pack is embedded in the KingFeatures JAR source payload.
- Startup reconstructs it into `plugins/KingFeatures/resourcepack/KingFeatures-Rank-Pack.zip` and verifies SHA-1 `6afdaefa9da8fb98c38b481e125c6b9ebce9bf1c`.
- OmniPanel is used only as the rank source. No KingFeatures ZIP belongs in `plugins/OmniPanel/rank-pack/`.
- No special OmniPanel build is required.
- If ResourcePackManager exists, KingFeatures registers the embedded pack for automatic merge/hosting.
- Otherwise KingFeatures sends its stable public `pack-host` copy directly with the verified SHA-1.
- The public URL is rebuilt from the same embedded bytes and round-trip checked in GitHub Actions.
- Runtime CI boots exact Paper 26.2 build 129 without seeding any manual resource-pack files and asserts the embedded pack is reconstructed successfully.
