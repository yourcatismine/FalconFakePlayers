# FalconFK

FalconFK is a Minecraft fake-player plugin built for modern Paper and Folia servers. It lets staff summon, manage, and despawn server bots with a simple command interface, custom names, and configurable chat messages.

## Overview

FalconFK is designed for servers that want believable fake players for testing, activity simulation, or server presentation. Bots can be spawned with random pool-based names or with a custom base name, and they can be configured to chat automatically using messages from YAML files.

This plugin is marked as Folia supported and targets Minecraft 1.21+.

## Features

- Summon one or more fake players at your location.
- Spawn bots with a custom name or use the built-in name pool.
- Toggle chat for all bots or a single bot.
- Despawn a specific bot or remove all bots at once.
- Randomized chat messages loaded from `bot-messages.yml`.
- Randomized bot names loaded from `bot-names.yml`.
- Folia-aware scheduling and server-region support.
- LuckPerms integration detection for safer bot setup when the plugin is installed.

## Requirements

- Minecraft 1.21 or newer
- Paper or Folia 1.21+
- Java 21

## Installation

1. Build the plugin with Maven.
2. Place the generated JAR in your server's `plugins` folder.
3. Start or restart the server.
4. Edit the generated configuration files in `plugins/FalconFK` if you want to customize names or chat messages.

## Commands

Main command:

```text
/falcon
```

Subcommands:

- `/falcon summon [amount] [name]` - spawn fake players near you.
- `/falcon chat [all|botname] [on|off|toggle]` - control bot chat behavior.
- `/falcon despawn [all|botname]` - remove bots from the server.

If `amount` is omitted, FalconFK spawns one bot. If `name` is omitted, the plugin uses a random valid name from the configured pool.

## Permissions

- `falconfk.command` - access to `/falcon`
- `falconfk.summon` - use `/falcon summon`
- `falconfk.chat` - use `/falcon chat`
- `falconfk.despawn` - use `/falcon despawn`

Defaults are defined in `plugin.yml`.

## Configuration

FalconFK ships with two YAML files in the plugin data folder:

- `bot-names.yml` - the pool of bot names used when summoning without a custom name.
- `bot-messages.yml` - chat lines used by bots when chat is enabled.

Names are sanitized to valid Minecraft usernames, and custom bot names are limited to the standard 16-character username length.

## Behavior Notes

- Bots spawn with survival game mode.
- Bots can be created from the command sender's location, or from the server spawn if the command is run from console.
- Bot chat is scheduled automatically and uses random messages from the configured message pool.
- If LuckPerms is installed, FalconFK attempts to preload the bot user data before spawning.

## Build

```bash
mvn clean package
```

The compiled plugin JAR will be available in the `target` folder.

## Author

Created by Tejano Blue, AKA Robelyn.

## License

No license file is currently included in this repository. Add one if you plan to distribute the plugin publicly.
