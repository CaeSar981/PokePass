# PixelmonTXZ 精灵战令

Pixelmon 1.12.2 战令插件：每日/每周任务 + 战令等级 + 免费/高级双轨奖励 + 赛季 + 兑换商店。

> 插件名（plugin.yml 的 name）是 `PixelmonTXZ`，与原版战令一致，数据目录也是
> `plugins/PixelmonTXZ/`。仓库名沿用 PokePass。

## 命令

| 命令 | 说明 |
|---|---|
| `/zl` | 打开主面板 |
| `/zl daily` | 每日任务页 |
| `/zl weekly` | 每周任务页 |
| `/zl reward` | 等级奖励页（左键领免费轨，右键领高级轨） |
| `/zl shop` | 兑换商店：用点券 / 金币换道具 |
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

全部在 `plugins/PixelmonTXZ/`：

- `config.yml` — 等级曲线、周期、高级轨开关、赛季长度、存储方式
- `quest/daily.yml` — 每日任务池
- `quest/weekly.yml` — 每周任务池
- `reward.yml` — 每级两条轨的奖励
- `shop.yml` — 兑换商店的商品与价格
- `message.yml` — 全部文案
- `gui.yml` — 三个界面的标题与尺寸
- `pass.db` — SQLite 存档


## 赛季

`config.yml` 的 `season` 段：

```yaml
season:
  enabled: true
  length-days: 60
  start-date: "2026-10-26"
```

- 换季是**懒判断**：玩家下次打开界面/登录时才比对赛季号，对不上就重置，
  不跑全服扫表任务。
- 重置范围：等级、总经验、免费/高级轨领取记录、每日/每周任务进度、商店限购次数。
- **不清**图鉴（`caughtSpecies`）—— 那是「抓过哪些物种」的事实记录，
  清掉会让图鉴类任务永久做不完。
- `start-date` 只影响「第几赛季 / 还剩几天」的算法，改它不会重置任何数据。
  想立刻换季，把它往前挪一个 `length-days` 即可。
- 玩家登录时若跨赛季会收到一条提示，控制台同时打一行换季日志。

## 兑换商店

`shop.yml` 里的每个商品就是一个兑换项，写法与 `reward.yml` 一致：

```yaml
items:
  diamonds:
    icon: DIAMOND
    name: "&b钻石包 &7x8"
    cost: { type: points, amount: 200 }   # points=点券, money=金币
    limit: 3                              # 每赛季限购，0 = 不限
    rewards:
      - "item:DIAMOND:8"
```

购买顺序是「查资格 → 预检 → 扣款 → 发货 → 记次数」：

- 预检会拦住物品名写错、货币前置没装这类必然失败的情况，玩家只会看到
  「现在兑换不了: 原因」，不会出现扣了点券没东西。
- 扣款成功但发货失败时**不退款**（退款比丢奖励更容易写错），改为逐条写进日志，
  管理员可按日志补发。
- 限购次数按赛季计，换季自动清零。

## PlaceholderAPI 变量

装了 PlaceholderAPI 才会注册，没装完全不影响。

| 变量 | 说明 |
|---|---|
| `%pixelmontxz_level%` | 战令等级 |
| `%pixelmontxz_max_level%` | 等级上限 |
| `%pixelmontxz_exp%` | 当前等级内经验 |
| `%pixelmontxz_exp_needed%` | 升到下一级需要多少 |
| `%pixelmontxz_exp_total%` | 累计总经验 |
| `%pixelmontxz_percent%` | 当前等级进度 0-100 |
| `%pixelmontxz_daily_done%` / `%pixelmontxz_daily_total%` | 每日任务进度 |
| `%pixelmontxz_weekly_done%` / `%pixelmontxz_weekly_total%` | 每周任务进度 |
| `%pixelmontxz_claimable%` | 可领取的奖励份数 |
| `%pixelmontxz_premium%` | `yes` / `no` |
| `%pixelmontxz_season%` / `%pixelmontxz_season_days%` | 赛季编号 / 距换季天数 |
| `%pixelmontxz_caught%` | 已捕捉物种数 |

另有别名 `%zhanling_*%`，写法完全一样。

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

```bash
./build.sh             # 构建；服务端没在跑就顺带部署
./build.sh deploy      # 构建并部署（服在跑同样跳过）
./build.sh no-deploy   # 只构建，产物在 dist/
```

jar 名由 `plugin.yml` 的 `name + version` 推导（当前 `dist/PixelmonTXZ-1.1.0.jar`），
版本号只有那一处来源。依赖三个 jar 全部取自工作区根的 `../_libs/`：CatServer、
Pixelmon 8.4.3、PlaceholderAPI（编译期用，运行期用服务端自带的）。
`stub/` 下是 NMS 编译期桩，只为让 javac 能解析 Pixelmon 的签名，不进 jar。

> 构建脚本历史上是 `build.ps1`（全套六个插件里唯一的 PowerShell 脚本），已统一成
> `build.sh`，与其余五个插件同一组命令、同一套参数。

## 已知限制

- 没装 PlayerPoints 时 `points:` 奖励会提示「点券系统未就绪」，不会静默吞掉；建议先用 `command:` 发点券，或装上 PlayerPoints。
- `Pokedex` 是插件自己统计的已捕捉物种集合，从插件装上那天开始算，不读 Pixelmon 原生图鉴存档。
- 周期切换是懒重置（玩家进来才比对日期），没上线的玩家不会占定时任务资源。
