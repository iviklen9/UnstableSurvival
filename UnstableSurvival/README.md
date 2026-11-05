# UnstableSurvival 1.0.0 – Paper 1.21.11 / Java 21

## Build

Install a JDK 21 and Maven 3.9+. From this folder run:

```bash
mvn clean package
```

The output will be `target/UnstableSurvival.jar`. Paper API is provided by the PaperMC Maven repository and is not bundled into the plugin.

## Aternos installation

1. In Aternos choose Paper 1.21.11 (or a compatible newer Paper build) and Java 21.
2. Build the project with Maven on a computer that has internet access.
3. Upload `target/UnstableSurvival.jar` under Aternos → Plugins.
4. Restart the server once. The plugin creates its YAML files and the `hardcore` world.
5. Set the world spawn coordinates in `plugins/UnstableSurvival/config.yml`, then restart.

Aternos does not compile Maven projects or accept a source ZIP as a plugin; it needs the built JAR.

## Commands

- `/survival` – enter the dedicated hardcore world.
- `/rtp` – start a safe random teleport; movement cancels the countdown.
- `/rtp world` – show configured RTP worlds.
- `/rtp cooldown` – show remaining RTP cooldown.
- `/shop`, `/orders`, `/ah`, `/ah sell <price>`
- `/money`, `/pay <player> <amount>`, `/baltop`
- `/customitems`
- `/us reload`, `/us money give <player> <amount>`, `/us item give <player> <item-id>`, `/us rtp set`, `/us ah clear`

## Hardcore behavior

The plugin uses a separate survival world named `hardcore`, sets its difficulty to Hard, and persists a death flag in `dead.yml`. A player who dies in that world is moved to Spectator and cannot re-enter `/survival`, including after a restart. Deaths in other worlds do not affect the Hardcore flag. The world name, spawn, and hardcore toggle are configurable.

## Configuration

- `config.yml`: hardcore world, spawn, RTP bounds/cooldown, AH duration and economy defaults.
- `shop.yml`: buy/sell prices.
- `orders.yml`: item delivery orders and money rewards.
- `auction-house.yml`: serialized listings.
- `economy.yml`: balances.
- `dead.yml`: created automatically for permanent-death flags.

## Important implementation notes

- Custom items use PersistentDataContainer IDs; renaming a vanilla item cannot forge one.
- All 25 item definitions register shaped crafting recipes. The Dragon Orbital recipe is the requested Elytra recipe and does not use a Dragon Egg.
- Economy and auction changes are written to YAML synchronously to reduce loss on shutdown.
- Permission checks use Bukkit permission nodes (`unstablesurvival.admin`, `unstablesurvival.bypass.rtp`), which LuckPerms can manage without a hard dependency.

This is an initial working implementation of the core survival/economy loop, not a claim that every advanced feature in the original specification is present. In particular, it does not yet include the requested full order rotation scheduler, AH search/filter/expiry-return GUI, multi-world inventory isolation, all advanced custom-item combat mechanics, or a fully featured confirmation workflow. Review and test on a backup server before using it on a live world.
