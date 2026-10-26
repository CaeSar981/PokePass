# PokePass 精灵战令

Pixelmon 1.12.2 战令插件：每日/每周任务 + 战令等级 + 免费/高级双轨奖励。

## 命令

| 命令 | 说明 |
|---|---|
| `/zl` | 打开主面板 |
| `/zl daily` | 每日任务页 |
| `/zl weekly` | 每周任务页 |
| `/zl reward` | 等级奖励页（左键领免费轨，右键领高级轨） |
| `/zl claim` | 一键领取所有够等级的奖励 |
| `/zl info` | 查看自己的等级与进度 |
| `/zl reload` | 重载配置（admin） |
| `/zl give <玩家> <经验>` | 加战令经验（admin） |
| `/zl reset <玩家>` | 清空战令进度（admin） |
| `/zl selftest` | 自检：事件总线、任务表、存储读写（admin） |
| `/zl progress <玩家> <training\|working> [数量]` | 给其它插件用的进度入口（控制台 / admin），玩家自己调会被拒 |

别名：`/pass`、`/ptxz`、`/zhanling`

## 权限

- `pokepass.use` — 基础使用，默认所有人
- `pokepass.premium` — 解锁高级轨奖励，默认不给（用权限插件卖）
- `pokepass.admin` — 管理命令，默认 OP

## 配置文件

全部在 `plugins/PokePass/`：

- `config.yml` — 等级曲线、周期、高级轨开关、存储方式
- `quest/daily.yml` — 每日任务池
- `quest/weekly.yml` — 每周任务池
- `reward.yml` — 每级两条轨的奖励
- `message.yml` — 全部文案
- `gui.yml` — 三个界面的标题与尺寸
- `pass.db` — SQLite 存档

## 任务类型

累积型（事件推进）：

| type | 条件写法 | 说明 |
|---|---|---|
| `Capture` | 物种英文名 / `*` | 捕捉 |
| `BattleWin` | `Player` / `Trainer` / `WildPokemon` / `*` | 对战胜利 |
| `Evolve` | 物种英文名 / `*` | 进化 |
| `EggHatch` | `*` | 孵化 |
| `ExperienceGain` | `*` | 精灵经验累计（amount 写总量） |
| `EVsGained` | `*` | 努力值累计 |
| `BlockBreak` | 方块名 | 挖掘 |
| `CraftItem` | 物品名 | 合成 |
| `PlayerFish` | 物品名 / `*` | 钓鱼 |
| `PickupItem` | 物品名 | 拾取 |
| `EnchantItem` | 物品名 | 附魔 |
| `Chat` | 关键字 | 聊天 |
| `InteractEntity` | NPC 名 / `*` | NPC 交互 |
| `PixelmonTrade` | `*` | 精灵交易 |
| `DeleteItem` | 物品名 | 消耗物品（接入时用） |
| `TrainingFinish` | `*` | 完成一次精灵特训（PokeTraining 领取时自动派命令推进） |
| `WorkFinish` | `*` | 完成一次精灵打工（PokeTools 领取成功时自动推进） |

快照型（定时扫描，不累加）：

| type | 条件写法 | 说明 |
|---|---|---|
| `Pokedex` | `*` | 捕捉过的不同物种数（插件自己统计） |
| `MaxLevelPokemon` | `目标等级`（如 `100`） | 队伍里满级精灵数量 |
| `CheckItem` | 物品名 | 背包持有数量 |

## 奖励写法

```yaml
levels:
  5:
    free:                          # 人人可领
      - "item:DIAMOND:6"
      - "command:give {player} diamond 2"
    premium:                       # 需要 pokepass.premium
      - "points:300"               # PlayerPoints，没装会提示并跳过
      - "money:500"                # Vault 经济，没装会提示并跳过
```

## 构建

```powershell
.\build.ps1            # 出 dist\PokePass-1.0.0.jar
.\build.ps1 -Deploy    # 顺便拷到 ..\TestServer\plugins（服务器没开时）
```

依赖两个 jar 从 `..\PokeTraining\libs` 复用：CatServer 与 Pixelmon 8.4.3。
`stub/` 下是 NMS 编译期桩，只为让 javac 能解析 Pixelmon 的签名，不进 jar。

## 已知限制

- 没装 PlayerPoints 时 `points:` 奖励会提示「点券系统未就绪」，不会静默吞掉；建议先用 `command:` 发点券，或装上 PlayerPoints。
- `Pokedex` 是插件自己统计的已捕捉物种集合，从插件装上那天开始算，不读 Pixelmon 原生图鉴存档。
- 周期切换是懒重置（玩家进来才比对日期），没上线的玩家不会占定时任务资源。
