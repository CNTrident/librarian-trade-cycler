# Contributing

感谢你考虑为 Villager Trade Finder 做出贡献。

## 开发环境

- Minecraft Java Edition 26.2
- Java 25
- Fabric Loader / Fabric API
- Trade Cycling 1.0.21+26.2

克隆仓库后运行：

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。

## 提交问题

请先搜索现有 Issues。错误报告应包含：

- Minecraft、Fabric Loader、Fabric API 和 Trade Cycling 的准确版本；
- 是否为单人游戏或多人服务器；
- 可复现步骤、预期行为和实际行为；
- `logs/latest.log` 中与本模组有关的错误片段；
- 必要时提供截图，但不要上传包含账号令牌或私人服务器地址的日志。

## Pull Request

1. 从 `main` 创建功能分支。
2. 保持改动集中，不提交 `build/`、`.gradle/`、`.tools/` 或游戏运行目录。
3. 对用户可见的文字同时更新 `en_us.json` 与 `zh_cn.json`。
4. 提交前运行 `gradlew build`。
5. 在 PR 中说明改动原因、行为变化和验证方法。

## 许可与来源

提交代码即表示你同意按本仓库的 MIT License 发布贡献。不得从 GPL 或其他不兼容许可项目直接复制代码。
本项目的来源关系和第三方参考见 README 的“开源来源与许可”章节。
