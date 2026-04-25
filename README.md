# Litematica Extra

[![GitHub Releases](https://shields.io/github/v/release/shuangshun/litematica-extra)](https://github.com/shuangshun/litematica-extra/releases)
[![GitHub Releases downloads](https://shields.io/github/downloads/shuangshun/litematica-extra/total)](https://github.com/shuangshun/litematica-extra/releases)
[![GitHub Repo stars](https://shields.io/github/stars/shuangshun/litematica-extra)](https://github.com/shuangshun/litematica-extra)

A Litematica extension that adds various fixes and features

---

## Features

- Automatically downgrade schematic version from V7 (Minecraft 1.20.5+) to V6 (Minecraft 1.20.4-), achieving compatibility with older versions while preserving data such as blocks, entities, and containers as much as possible

- Fix duplicate entity pasting when pasting entities at chunk boundaries on servers

---

## Requirements

- Minecraft 1.20.x (for now)
- [Fabric Loader](https://fabricmc.net/) >= 0.14.21
- [MaLiLib](https://modrinth.com/mod/malilib)
- [Litematica](https://modrinth.com/mod/litematica)

## Building

Only the Windows version is provided in Releases, for Linux and other versions, please build them yourself

```bash
git clone --recurse-submodules https://github.com/shuangshun/litematica-extra.git
cd litematica-extra

./gradlew buildAndGather
```

## Credits

- [Litematica](https://modrinth.com/mod/litematica)
- [Litematic_V7_To_V6](https://github.com/chenjunfu2/Litematic_V7_To_V6)
