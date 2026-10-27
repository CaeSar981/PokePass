# PixelmonTXZ 精灵战令

Pixelmon 1.12.2 战令插件：每日/每周任务 + 战令等级 + 免费/高级双轨奖励 + 赛季 + 兑换商店
+ 周任务分页解锁。

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

- `config.yml` — 等级曲线、周期、高级轨开关、赛季长度、周任务分页、存储方式
- `quest/daily.yml` — 每日任务池
- `quest/weekly.yml` — 每周任务池（可写 `page: 2` 分页）
- `reward.yml` — 每级两条轨的奖励（默认配满 1~50 级）
- `shop.yml` — 兑换商店的商品与价格
- `message.yml` — 全部文案
- `gui.yml` — 界面自定义（见下）
- `pass.db` — SQLite 存档


## 周任务分页解锁

照原版战令的做法：周任务可以分成多页，第 1 页免费，第 2 页起要用点券买。

```yaml
# config.yml
weekly:
  pages:
    enabled: true       # 关掉 = 所有页都免费
    free-pages: 1       # 前几页免费
    cost-points: 10     # 解锁一页花多少点券
```

```yaml
# quest/weekly.yml
  w-fossil:
    name: "&5化石研究者"
    page: 2             # 不写就是第 1 页
```

每周任务界面底部会多出一个「解锁第 N 页」按钮（gui.yml 的 `weekly.buttons.unlock`）：

- 还有待解锁的页才显示，全解锁后自动消失
- 点一下先扣点券再解锁，扣不动就不解锁（不会出现白送）
- 解锁是**赛季级**的，换季一起清空
- 没解锁的页，里面的任务既不会显示，事件也不会推进

## 任务类型

| 类型 | 说明 |
|---|---|
| `Capture` | 捕捉（`condition` 填物种名，`*` 不限） |
| `BattleWin` | 对战胜利（`condition` 填 `Player` / `Trainer` / `WildPokemon`） |
| `Evolve` / `EggHatch` / `PixelmonTrade` | 进化 / 孵蛋 / 交换 |
| `ReceivedFossil` | 化石机复活（`condition` 可填物种名） |
| `ExperienceGain` / `EVsGained` | 精灵经验累计 / 努力值累计 |
| `BlockBreak` / `CraftItem` / `PickupItem` / `PlayerFish` / `EnchantItem` | 原版行为 |
| `Chat` / `InteractEntity` | 聊天关键字 / NPC 交互 |
| `Pokedex` | 图鉴物种数（快照） |
| `PokedexPercent` | 图鉴完成度百分比，`amount: 30` = 30%（快照） |
| `LevelUp` | 人物等级，`amount: 30` = 升到 30 级（快照） |
| `MaxLevelPokemon` | 背包里满级精灵数（快照） |
| `CheckItem` / `DeleteItem` | 持有 / 消耗物品（快照 / 事件） |
| `TrainingFinish` / `WorkFinish` | 完成一次特训 / 打工（跨插件，走命令推进） |

## 界面自定义

五个界面（主面板 / 每日任务 / 每周任务 / 等级奖励 / 兑换商店）都可以在 `gui.yml` 里改：

- **按钮**：每个按钮可改 `slot`（槽位）、`material`（材质）、`data`（数据值）、
  `name`（名字）、`enabled`（设 `false` 隐藏）。按钮 id 见 gui.yml 注释：
  主面板是 `daily` / `weekly` / `level` / `season` / `reward` / `shop` / `close`，
  任务与奖励页是 `back` / `prev` / `next` / `claim-all` / `close`，商店是 `back` / `close`。
- **装饰物**：`decor` 列表可在任意空槽位摆纯装饰物品，支持 `lore` 与 `glow`，
  与按钮或内容区撞槽位会被自动跳过。
- **底色**：`filler` 换铺底材质，如 `filler: "STAINED_GLASS_PANE:15"` 换成黑玻璃。
- **内容区**：任务页 / 奖励页的 `content: "10-16,19-25"` 决定任务卡、奖励卡摆在哪，
  分页按这里的格数自动重算。
- **占位符**：按钮名与装饰物的 name / lore 支持 `%player%`、`%level%`、`%season%`、
  `%days_left%`、`%claimable%`、`%points%`、`%daily_done%` 等实时变量，全表见 gui.yml 头部注释。
- **精灵图标**：按钮和装饰物都支持 `sprite: Pikachu`（英文物种名或图鉴号，可配
  `form` / `shiny`），用 Pixelmon 自带的精灵照片物品渲染；任务卡（daily.yml / weekly.yml
  的 `sprite:` 键）和商品图标（shop.yml 的 `sprite:` 键）同理。精灵名不认识或反射失败
  会退回普通材质并在 `/zl selftest` 里报出来。

规则：没写的键用默认值（默认值就是当前界面的长相），旧配置不用动；写错的项由
`/zl selftest` 报出来并退回默认，不会炸插件。改完 `/zl reload` 生效。
升级插件后想看新的注释模板，删掉 `plugins/PixelmonTXZ/gui.yml` 再 reload 即可重新生成。
注意 shop 的标题与尺寸在 `shop.yml`，`gui.yml` 的 shop 段只管按钮和装饰。


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

jar 名由 `plugin.yml` 的 `name + version` 推导（当前 `dist/PixelmonTXZ-1.2.0.jar`），
版本号只有那一处来源。依赖三个 jar 全部取自工作区根的 `../_libs/`：CatServer、
Pixelmon 8.4.3、PlaceholderAPI（编译期用，运行期用服务端自带的）。
`stub/` 下是 NMS 编译期桩，只为让 javac 能解析 Pixelmon 的签名，不进 jar。

> 构建脚本历史上是 `build.ps1`（全套六个插件里唯一的 PowerShell 脚本），已统一成
> `build.sh`，与其余五个插件同一组命令、同一套参数。

## 已知限制

- 没装 PlayerPoints 时 `points:` 奖励会提示「点券系统未就绪」，不会静默吞掉；建议先用 `command:` 发点券，或装上 PlayerPoints。
- `Pokedex` 是插件自己统计的已捕捉物种集合，从插件装上那天开始算，不读 Pixelmon 原生图鉴存档。
- 周期切换是懒重置（玩家进来才比对日期），没上线的玩家不会占定时任务资源。
