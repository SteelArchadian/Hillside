# Hillside

Natural terrain gets half-block steps. Wherever the ground rises by one block (hillsides, riverbanks, cave floors, dunes, nether slopes), Hillside places a matching slab, so you walk up hills instead of jumping.

Minecraft 1.21.1, NeoForge.

## What it does
- **Slabs for natural blocks only.** Grass, dirt, sand, gravel, stone, deepslate, terracotta, snow, moss, sculk, netherrack, soul sand, nylium and the like get slabs. Building blocks, ores, logs, leaves and mushroom blocks never do.
- **Works with any terrain mod, with no per-mod code.** Hillside reads which blocks each installed mod tags as natural ground, and makes slabs for those that look like terrain. Tested with Biomes O' Plenty, Regions Unexplored, Oh The Biomes We've Gone, Nature's Spirit, Terralith and Tectonic.
- **Looks like the block it came from.** Each slab uses its source block's textures, colour tint and sounds. Grass slabs tint like grass, and snowy grass stays snowy.
- **No seams.** Snow layers, snow blocks and slabs meet without gaps. Slabs under overhangs don't show see-through faces.
- **Shaders.** Slabs carry their source block's shader material ID, so Iris packs (Bliss, Complementary...) shade them the same as the full block.
- **Caves too.** Cave floors and ledges get slabs as well as the surface.
- Plays well with Better Grass and Boundless LOD.

- **Sand and gravel slabs fall** like their full blocks do. This can be switched off.

## For admins
- `/hillside blocks` (op level 2) opens a searchable list of every block that could have a slab, each with an On/Off switch. Blocks Hillside picked by itself are listed first.
  - Turning a block **off** takes effect at once: no new slabs of it generate.
  - Turning an undetected block **on** (say, a modded pink gravel) gives it slabs after a restart, because blocks register at startup.
  - The screen also has the falling-slabs switch.
- The same switches as commands: `/hillside blocks <block> true|false` and `/hillside blocks falling true|false`.
- Everything is saved in `config/hillside/blocks.json`. On a server, players need the same file.
- `/hillside census [radius]` counts the one-block steps in loaded chunks around you, by biome. It reports how many have a slab and which floor blocks didn't get one. Handy when you add a terrain mod.

## Building
Put `iris-neoforge-1.8.14-beta.1+mc1.21.1.jar` in `libs/` (compile-only, not shipped), then run `./gradlew build`.

## License
GPL-3.0-only. Inspired by Countered's Terrain Slabs; no code from it.

Made with AI assistance.
