# KingFeatures

KingFeatures is a modular Paper SMP feature hub for SkyStrike. v0.2.0 adds a permission-free **OmniPanel Rank Visuals** system using the supplied pixel-art rank badges.

## OmniPanel Rank Visuals

KingFeatures does not create, grant or infer rank permissions. **OmniPanel remains the rank authority.** KingFeatures connects through the installed OmniPanel API and asks for `%omnipanel_primary_rank_plain%` (falling back to `%omnipanel_rank_plain%`), then renders the matching image badge.

Included badge artwork:
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
- Member/default

All 13 are enabled by default. `/kf ranks` opens a 54-slot settings GUI where the existing KingFeatures administrator can:
- enable/disable each rank visual independently;
- enable/disable all rank visuals;
- toggle Player List/TAB rendering;
- toggle Paper display/chat-name rendering;
- enable/disable all ranks at once;
- request a resource-pack refresh.

There are **no new permission nodes for rank matching**. A player gets a badge because OmniPanel reports that rank, not because KingFeatures checks LuckPerms itself.

### Rank resource pack
The supplied artwork is bundled inside the KingFeatures JAR. On startup KingFeatures reconstructs and validates:

```text
plugins/KingFeatures/resourcepack/KingFeatures-Rank-Visuals.zip
```

The pack uses the isolated font namespace `kingfeatures:ranks` and private glyphs so it does not overwrite Minecraft's default font or another plugin's custom glyphs.

Preferred delivery is through **ResourcePackManager**. KingFeatures registers its local pack through ResourcePackManager's installed API so it can be merged with packs from other plugins instead of replacing them. If ResourcePackManager is unavailable, `resource-pack.remote-url` remains the direct remote fallback.

## Troll Lab
- OmniPanel main-panel integration through the installed OmniPanel API.
- 54-slot animated target/action browser.
- Paper Dialog API search input on the 1.21.11 baseline.
- Safe prank actions including harmless lightning, launch, spin, creeper scare, darkness, freeze, fake death, hotbar shuffle, sound burst, ghost hit, fake diamonds and view flip.
- Executed trolls are written to `plugins/KingFeatures/audit.log`.

## King's Favor Bottle
- UEP price: **255**.
- Direct-purchase redeem window: **5 hours**.
- Drinking grants **Hero of the Village for 24 hours**.
- Price, redeem window, duration, amplifier and CustomModelData are configurable.
- Identity is stored with PDC, not only display name/lore.
- `/kf bottle template` creates an untimed crate/shop ItemStack template.

## Ultimate Economy Plus integration
KingFeatures does not create a second economy.
- Connects to the installed Ultimate Economy Plus API/classloader.
- Withdraws/refunds through UEP for `/kf bottle buy`.
- Crate/Visual-Shop integration uses exact signed ItemStacks.

## Resource-pack delivery
Resolution order in v0.2.0:
1. bundled `KingFeatures-Rank-Visuals.zip`;
2. configured local pack path;
3. legacy OmniPanel rank-pack fallback;
4. configured remote URL for direct delivery when appropriate.

When ResourcePackManager is installed it is preferred for local-pack merge/hosting/distribution.

## Compatibility
CI verifies:
- Paper **1.21.11** / Java 21 compile;
- exact Paper **26.2 build 129** / Java 25 compile;
- Paper **26.3** / Java 25 compile;
- a real Paper 26.2 build 129 boot test that verifies KingFeatures stays enabled and reconstructs the bundled rank pack byte-for-byte.

The deployable JAR uses the Java 21 / Paper 1.21.11 baseline so it can run forward on supported Java 25 servers.

## Commands
- `/kf ranks` — configure OmniPanel rank visuals
- `/kf troll`
- `/kf bottle buy`
- `/kf bottle template [amount]`
- `/kf bottle give <player> [amount]`
- `/kf pack status`
- `/kf pack resend [player]`
- `/kf status`
- `/kf reload`

## Existing permissions
v0.2.0 adds **no new permission nodes**.
- `kingfeatures.admin` — existing KingFeatures administration, also used to open the settings GUI
- `kingfeatures.trolllab`
- `kingfeatures.bottle.buy`
- `kingfeatures.bottle.give`

These permissions do not decide which rank image a player receives. Rank visuals come from OmniPanel's API result.

## Installation
1. Fully stop Paper; do not use `/reload` or PlugMan for the upgrade.
2. Remove the old KingFeatures JAR so there is only one copy.
3. Install `KingFeatures-0.2.0.jar`.
4. Keep OmniPanel installed for rank detection.
5. Keep ResourcePackManager installed if you want automatic pack merging/hosting with other plugin packs.
6. Start the server.
7. Run `/kf status` and `/kf ranks`.

## Startup isolation
UEP, resource-pack delivery, Troll Lab, OmniPanel and Rank Visuals are isolated startup modules. If an optional integration fails, KingFeatures remains enabled and `/kf status` reports the failing module instead of disabling the entire plugin.
