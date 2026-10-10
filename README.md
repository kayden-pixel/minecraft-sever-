# KingFeatures

KingFeatures is a modular Paper SMP feature hub for SkyStrike. **Troll Lab** is the first major module, not the entire plugin.

## Current v0.1.0 modules

### Troll Lab
- OmniPanel main-panel integration through the installed OmniPanel API (no shaded OmniPanel classes).
- 54-slot animated target/action browser.
- Paper Dialog API search input (`DialogInput.text`) on the 1.21.11 baseline.
- Safe prank actions: harmless lightning, launch, spin, creeper scare, darkness, freeze, fake death, hotbar shuffle, sound burst, ghost hit, fake diamonds and view flip.
- Every executed troll is written to `plugins/KingFeatures/audit.log` with actor, target and action.
- `/kf troll` fallback works even if OmniPanel is absent.

### King's Favor Bottle
Default interpretation of the requested timings:
- UEP price: **255**.
- A directly purchased bottle must be redeemed within **5 hours**.
- Drinking it grants **Hero of the Village for 24 hours**.
- Price, redeem window, effect duration, amplifier and CustomModelData are configurable.
- Item identity is stored in Bukkit/Paper PersistentDataContainer, not in the display name/lore.

`/kf bottle template` creates an **untimed template** intended for Ultimate Economy Plus crates or Visual Shop. UEP can persist the exact ItemStack/PDC metadata. `/kf bottle buy` creates a timed direct-purchase bottle and withdraws through the installed UEP API.

### Ultimate Economy Plus integration
KingFeatures does **not** own a second balance, shop, Auction House or crate system.
- Connects to `kingez341yt.plugin.api.UltimateEconomyServices` from the installed UEP plugin classloader.
- Withdraws/refunds through UEP for `/kf bottle buy`.
- Crate/Visual-Shop integration uses exact signed ItemStacks.
- Recommended UEP flow: run `/kf bottle template`, hold the item, then use UEP Item Control -> **Add Held Item To Shop**, or place that exact template into Crate Studio.

### Resource-pack delivery
KingFeatures can send the resource pack itself; no `server.properties` resource-pack entry is required.

Resolution order:
1. `resource-pack.remote-url` if configured.
2. `plugins/KingFeatures/resourcepack/KingFeatures-Pack.zip`.
3. Existing OmniPanel rank pack at `plugins/OmniPanel/rank-pack/Kingez341YT-Rank-Pack-1.21.11.zip`.

For local packs, KingFeatures can start a tiny HTTP server and derives the hostname the player used to join. It computes a real SHA-1 and sends the pack with a stable UUID, so clients can cache it.

> Hosting note: many Minecraft hosts block arbitrary extra ports. If port `8124` is not publicly reachable, set `resource-pack.remote-url` to an HTTPS-hosted pack (GitHub Pages/CDN/object storage works well). The plugin still owns sending, required/optional state, hash and status tracking.

## Compatibility target
- Source/bytecode baseline: **Java 21 / Paper 1.21.11**.
- Paper 26.1+ servers use Java 25; Java 21 plugin bytecode runs forward on Java 25.
- Target range: Paper **1.21.11 through 26.3**.
- New UI is built on Paper's Dialog API already present by 1.21.11, avoiding a 26.3-only hard dependency.
- OmniPanel and UEP are optional runtime integrations and are resolved from the installed plugins' classloaders to avoid optional-dependency linkage failures.

## Commands
- `/kf troll`
- `/kf bottle buy`
- `/kf bottle template [amount]`
- `/kf bottle give <player> [amount]`
- `/kf pack status`
- `/kf pack resend [player]`
- `/kf status`
- `/kf reload`

## Permissions
- `kingfeatures.admin`
- `kingfeatures.trolllab`
- `kingfeatures.bottle.buy`
- `kingfeatures.bottle.give`

## Building
The project includes a GitHub Actions build. Locally with Gradle 8.x:

```bash
gradle clean build
```

Output:

```text
build/libs/KingFeatures-0.1.0.jar
```

## Installation
1. Fully stop Paper. Do not `/reload` or use PlugMan for integration testing.
2. Install `KingFeatures-0.1.0.jar`.
3. Keep your normal current OmniPanel and Ultimate Economy Plus JARs installed if you want those integrations.
4. Start the server.
5. Run `/kf status`.
6. Test `/kf troll`, `/kf bottle template`, `/kf bottle buy`, and one resource-pack join.

## Resource-pack art hook
`bottle.custom-model-data` defaults to `341255`. Once the KingFeatures bottle art is added to your pack, bind the item model to that value. Until then, the bottle remains fully functional with the vanilla potion appearance while the existing rank glyphs/images continue to work from the rank pack.
