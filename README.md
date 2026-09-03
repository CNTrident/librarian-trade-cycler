# Villager Trade Finder

[![CI](https://github.com/CNTrident/villager-trade-finder/actions/workflows/build.yml/badge.svg)](https://github.com/CNTrident/villager-trade-finder/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4)](https://fabricmc.net/)

面向 Minecraft Java Edition 26.2 + Fabric 的客户端便捷模组。它与
[Trade Cycling 1.0.21+26.2](https://www.curseforge.com/minecraft/mc-mods/trade-cycling/files/8271477)
配合，在未锁定的职业村民交易界面中自动刷新交易，直到出现满足条件的附魔书或物品。

## 下载

从 [GitHub Releases](https://github.com/CNTrident/villager-trade-finder/releases/latest) 下载与 Minecraft 26.2 对应的 JAR。

## 功能

- 每种村民职业都有独立的目标列表和 AND/OR 模式；界面标题和物品候选随当前职业变化，流浪商人不会启用本模组。
- 盔甲商、武器商、工具商、渔夫和制箭师的物品目标可用逗号添加多个中文/英文目标附魔及对应精确等级。
- 设置目标附魔后会严格匹配完整附魔集合；包含任何未指定的额外附魔都不会判定为命中。
- 其他村民的物品候补来自当前职业的原版数据驱动交易表，并支持中文名、英文名和完整 ID。
- 自动补全候补支持每页 6 项、上下翻页以及 Page Up/Page Down 键盘翻页。
- 每个目标支持 `ON`、`OFF`、`AUTO` 三种状态；`AUTO` 目标匹配并停止后会自动切换为 `OFF` 并保存。
- 可保存多个附魔书目标，并以“任一满足（OR）”或“全部满足（AND）”决定停止条件。
- 找到目标并停止时在聊天栏显示本轮刷新次数；每次重新开始时从 0 计数。
- 手动停止时也会显示本轮刷新次数。
- 附魔输入支持中文名、英文名和完整 ID，配置文件仍保存稳定的完整 ID。
- 目标列表同时显示附魔的中文名和英文名；批量状态按钮依次将全部目标设为 `ON`、`OFF`、`AUTO`。
- 每个目标可独立选择“初始交易”“后续交易”或“全部交易”；后两者会读取 VisibleTraders 提供的锁定交易。
- 新目标输入框使用示例提示；附魔等级和价格会按所选附魔及原版交易范围自动限制。
- 每个目标可设置附魔名称、精确等级、最低和最高原始绿宝石价格。
- 价格使用 `MerchantOffer#getBaseCostA()`，不计入治愈/声望/村庄英雄折扣，也不计入需求涨价。
- 每次刷新都等待服务端返回新交易后再继续，不会每 tick 连续发送刷新包。
- 收到并应用村民交易数据包后才会判断结果，未命中时才发送下一次刷新请求。
- 交易已锁定、服务端不支持、没有目标、网络响应超时或达到 100000 次安全上限时自动停止。
- 内置简体中文和英文界面。

## 安装

客户端 `mods` 目录放入：

1. `villager_trade_finder-fabric-26.2-1.7.0.jar`
2. `trade-cycling-fabric-1.0.21+26.2.jar`
3. 与 Minecraft 26.2 匹配的 Fabric API

如需匹配“后续交易”或“全部交易”，客户端和服务器还需要安装 VisibleTraders 2.4.0 或更高版本。

多人游戏中，服务器也必须安装 Trade Cycling；本模组自身只需安装在客户端。
Minecraft 26.2 和本项目的构建环境要求 Java 25。

### 使用注意事项

- 只对尚未完成过交易、仍可刷新职业的村民使用；一旦交易锁定，模组会停止循环。
- 刷新必须由服务器上的 Trade Cycling 支持；网络响应超时或达到 100000 次时会自动停止。
- “后续交易”和“全部交易”依赖 VisibleTraders；服务器未提供锁定交易数据时只能可靠判断初始交易。
- 显示和筛选的是原始绿宝石价格，不包含治愈、声望、村庄英雄或需求变化造成的价格调整。
- 自动操作前请确认目标条件与职业列表正确，避免长时间无效刷新。

## 使用

1. 打开一个尚未交易锁定的职业村民交易界面；不同职业会读取各自独立的目标列表。
2. 按 `C` 或点击齿轮按钮，添加一个或多个目标。例如：
   - 附魔名称：`经验修补`、`Mending` 或 `minecraft:mending`
   - 精确等级：`1`
   - 原始价格：`10` 到 `20`
   - 可附魔物品可填写多个附魔，例如：`耐久, 经验修补`，等级填写：`3, 1`
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
- **可选兼容：** 通过可选反射桥接兼容 MIT 许可的
  [Ramixin/VisibleTraders](https://github.com/Ramixin/VisibleTraders)，读取其提供的后续锁定交易；未复制或打包其源码。

本项目自身以 [MIT License](LICENSE) 发布。提交问题请使用
[GitHub Issues](https://github.com/CNTrident/villager-trade-finder/issues)，参与开发前请阅读
[CONTRIBUTING.md](CONTRIBUTING.md)。

本项目不是 Mojang/Microsoft 的官方产品，也不受其认可或关联。
