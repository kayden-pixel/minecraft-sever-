# KingFeatures

KingFeatures is a modular Paper SMP feature hub for SkyStrike. OmniPanel remains the source of truth for server ranks; KingFeatures adds the visual layer, Troll Lab and other SMP features without creating a second rank or economy system.

## v0.1.3 — zero-setup OmniPanel rank visuals

Install **one file:** `KingFeatures-0.1.3.jar`.

The plugin contains its own rank resource pack. On startup it automatically reconstructs and checksum-verifies that pack at:

```text
plugins/KingFeatures/resourcepack/KingFeatures-Rank-Pack.zip
```

You do **not** copy a ZIP into `plugins/OmniPanel/rank-pack/`, and you do **not** edit `server.properties`.

Delivery is automatic:

1. If ResourcePackManager is installed, KingFeatures registers its embedded pack through the official ResourcePackManager API so it can be merged/hosted with packs from other plugins such as BackpackPlus.
2. If ResourcePackManager is not installed or its integration fails, KingFeatures sends its own stable hosted copy directly to the player.
3. The hosted copy is generated from the exact embedded payload and CI verifies the public client-download URL against the same SHA-1 before publishing.

Built-in pack SHA-1:

```text
6afdaefa9da8fb98c38b481e125c6b9ebce9bf1c
```

### OmniPanel relationship

For OmniPanel rank visuals, **normal OmniPanel still needs to be installed and enabled**, because it is the source of the player's rank. It does not need a special KingFeatures-compatible build and it does not need to store the KingFeatures resource pack.

KingFeatures reads/syncs the rank through the installed OmniPanel integration and maps the visual automatically. It does not create/remove LuckPerms groups or add per-rank permission requirements.

Supported visuals:

- Owner
- Co-Owner
- CEO
- Manager
- Admin
- Builder
- Developer
- Moderator
- Helper
- MVP
- VIP
- YouTuber
- Member

`/kf ranks` opens the visual-control GUI. All mapped ranks are enabled by default; server operators can globally toggle individual visuals without changing the player's actual rank.

## Troll Lab

- OmniPanel main-panel integration through the installed OmniPanel API.
- Animated target/action browser with search.
- Safe prank actions including lightning effect, launch, spin, creeper scare, darkness, freeze, fake death, hotbar shuffle, sound burst, ghost hit, fake diamonds and view flip.
- Actions are audited to `plugins/KingFeatures/audit.log`.
- `/kf troll` remains available as the direct route.

## King's Favor Bottle

Default timings:

- UEP price: **255**.
- Direct-purchased bottle redeem window: **5 hours**.
- Hero of the Village duration: **24 hours**.
- Item identity uses PersistentDataContainer.

`/kf bottle template` creates an untimed template intended for Ultimate Economy Plus crates/shop storage. `/kf bottle buy` uses UEP for the purchase flow.

## Compatibility

CI compiles the same source against:

- Paper **1.21.11** / Java 21
- Paper **26.2 build 129** / Java 25
- Paper **26.3** / Java 25

A separate runtime smoke test boots the deployable JAR on exact Paper 26.2 build 129 and verifies that KingFeatures stays enabled and reconstructs a valid embedded rank pack with the expected checksum.

## Commands

- `/kf ranks`
- `/kf troll`
- `/kf bottle buy`
- `/kf bottle template [amount]`
- `/kf bottle give <player> [amount]`
- `/kf pack status`
- `/kf pack resend [player]`
- `/kf status`
- `/kf reload`

## Existing permissions

KingFeatures v0.1.3 does not introduce new rank-visual permission nodes.

Existing plugin permissions remain:

- `kingfeatures.admin`
- `kingfeatures.trolllab`
- `kingfeatures.bottle.buy`
- `kingfeatures.bottle.give`

## Installation

1. Fully stop Paper. Do not use `/reload` or PlugMan.
2. Remove the old KingFeatures JAR.
3. Put `KingFeatures-0.1.3.jar` in `/plugins`.
4. Keep your normal OmniPanel JAR installed if you want OmniPanel-backed rank visuals.
5. Start the server normally.
6. Run `/kf status` and `/kf ranks`.

There is no manual KingFeatures rank-pack installation step.
