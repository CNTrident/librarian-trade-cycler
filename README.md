# Librarian Trade Cycler

[![CI](https://github.com/CNTrident/librarian-trade-cycler/actions/workflows/build.yml/badge.svg)](https://github.com/CNTrident/librarian-trade-cycler/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4)](https://fabricmc.net/)

面向 Minecraft Java Edition 26.1.2 + Fabric 的客户端便捷模组。它与
[Trade Cycling 1.0.21+26.1.2](https://www.curseforge.com/minecraft/mc-mods/trade-cycling/files/8020648)
配合，在未锁定的村民交易界面中自动刷新交易，直到出现满足条件的附魔书。

## 功能

- 可保存多个附魔书目标，并以“任一满足（OR）”或“全部满足（AND）”决定停止条件。
- 找到目标并停止时在聊天栏显示本轮刷新次数；每次重新开始时从 0 计数。
- 每个目标可设置附魔 ID、精确等级、最低和最高原始绿宝石价格。
- 价格使用 `MerchantOffer#getBaseCostA()`，不计入治愈/声望/村庄英雄折扣，也不计入需求涨价。
- 每次刷新都等待服务端返回新交易后再继续，不会每 tick 连续发送刷新包。
- 交易已锁定、服务端不支持、没有目标、网络响应超时或达到 100000 次安全上限时自动停止。
- 内置简体中文和英文界面。

## 安装

客户端 `mods` 目录放入：

1. `librarian_trade_cycler-fabric-26.1.2-1.0.0.jar`
2. `trade-cycling-fabric-1.0.21+26.1.2.jar`
3. 与 Minecraft 26.1.2 匹配的 Fabric API

多人游戏中，服务器也必须安装 Trade Cycling；本模组自身只需安装在客户端。
Minecraft 26.1.2 和本项目的构建环境要求 Java 25。

## 使用

1. 打开一个尚未交易锁定的图书管理员交易界面。
2. 按 `C` 或点击齿轮按钮，添加一个或多个目标。例如：
   - 附魔 ID：`minecraft:mending`
   - 精确等级：`1`
   - 原始价格：`10` 到 `20`
3. 保存后按 `R` 或点击播放按钮开始。
4. 找到目标后会自动停止并播放提示音；再次按 `R` 可手动停止。

`C`、`R` 均可在 Minecraft 的按键设置中修改。配置保存在
`config/easyautocycler-filters.json`。

## 构建

使用 Java 25：

```powershell
.\gradlew.bat build
```

产物位于 `build/libs/`。

## 开源来源与许可

本项目是一个派生作品，参考关系明确如下：

- **代码基础：** 基于 MIT 许可的
  [Uncraftbar/Easy-Auto-Cycler](https://github.com/Uncraftbar/Easy-Auto-Cycler)
  Fabric 26.1.2 分支定制，复用了其配置界面、Trade Cycling 集成和网络同步框架。原作者版权声明已保留在
  [`LICENSE`](LICENSE) 中。
- **功能与交互参考：** 参考 GPL-3.0-only 项目
  [Greeenman999/LibrarianTradeFinder](https://github.com/Greeenman999/LibrarianTradeFinder)
  的多目标附魔、等级和价格筛选思路；本项目未复制或合并其 GPL 源代码。
- **运行依赖：** 使用
  [henkelmax/trade-cycling](https://github.com/henkelmax/trade-cycling)
  提供的未锁定村民交易刷新协议。Trade Cycling 不会被打包进本项目 JAR，玩家需要单独安装它。

本项目自身以 [MIT License](LICENSE) 发布。提交问题请使用
[GitHub Issues](https://github.com/CNTrident/librarian-trade-cycler/issues)，参与开发前请阅读
[CONTRIBUTING.md](CONTRIBUTING.md)。

本项目不是 Mojang/Microsoft 的官方产品，也不受其认可或关联。
