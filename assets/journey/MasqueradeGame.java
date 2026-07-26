import java.util.*;
import java.util.stream.Collectors;

/**
 * MasqueradeGame.java
 *
 * 按用户提供的流程与规则模拟“假面舞会”式的三日斗舞游戏。
 *
 * 主要说明 / 设计假设：
 * - 有玩家（Player）和八个角色（编号 1-8）。玩家不是编号 1-8 中的一员。
 * - 在 1-8 中随机分配 3 个“伶人”（actors），其余为“舞者”（dancers）。
 * - 在三位伶人里随机选一位为“面具伶人”（maskActor），具有第三日可能的翻转技能。
 * - 每次需要玩家输入一个编号或选择随机时，程序**会明确打印可选编号范围**并等待输入（直到输入合法）。
 * - 随机事件按给定权重触发；每个随机事件会在事件发生时执行其影响（并在需要玩家输入时立即要求输入）。
 * - 特别注意：随机事件一定在当日斗舞前触发（并在需要时提前要求玩家输入，以保证“先完成随机事件再进行斗舞”）。
 * - “悲歌使一位舞者离场”在第2日和第3日开始时执行，且不会使伶人离场（即随机选一个当前存活的舞者移出）。
 * - 关于“愚者（Fool）”给玩家回复生命的设定：本实现将玩家视为需要“存活三日”的实体；
 *   但在本规则中玩家并不会在斗舞里作为编号被淘汰（玩家是与编号1-8并存的），
 *   因此“愚者”的效果会被模拟为一个被记录的“Buff”（在未来能抵消一次导致玩家死亡的致命事件），
 *   但由于当前规则没有直接能导致玩家死亡的机制，这主要以消息形式体现（详见程序注释）。
 *
 * - 胜利判定：玩家活着并且最后一日舞池中**没有舞者（dancer）被出局**即为胜利（出局为伶人也算胜利）。
 *
 * 使用提示：
 * - 运行后输入 "start" 开始（程序会提示）。
 * - 程序在每个输入环节都会指出合法范围并提示输入。
 *
 */
public class MasqueradeGame {
    static final Scanner scanner = new Scanner(System.in);
    static final Random rand = new Random();

    // 角色信息
    static class Role {
        int id;                 // 1..8
        boolean isActor;        // 伶人(true) 或 舞者(false)
        boolean alive = true;
        boolean isMaskActor = false; // 面具伶人标记
        boolean masked = false;      // 在第三日可能被面具佩戴导致翻转阵营（临时）
        Role(int id) { this.id = id; }
        String faction() { return isActor ? "伶人" : "舞者"; }
    }

    List<Role> roles = new ArrayList<>();
    List<Integer> eliminatedOrder = new ArrayList<>(); // 按顺序记录被淘汰的编号
    Map<Integer, String> eliminationReason = new HashMap<>(); // 纪录每次淘汰的原因（便于揭幕）
    boolean foolBuff = false; // 愚者效果标记（描述性）
    Integer crowChosenForToday = null; // 乌鸦事件在当日舞斗时使用的号码（已由玩家提前提供）
    boolean crowActiveToday = false;
    boolean gameStarted = false;
    Integer maskPickedSecret = null; // 被面具选中的编号（若发生），但不告诉玩家
    boolean maskActorAliveAtDay3 = false;

    public static void main(String[] args) {
        MasqueradeGame g = new MasqueradeGame();
        g.run();
    }

    void run() {
        System.out.println("欢迎来到假面舞会模拟器。输入 'start' 开始游戏。");
        while (true) {
            System.out.print("> ");
            String cmd = scanner.nextLine().trim();
            if (cmd.equalsIgnoreCase("start")) break;
            System.out.println("请输入 'start' 开始。");
        }
        initGame();
        day1();
        day2();
        day3();
        revealFinalResult();
    }

    void initGame() {
        // 初始化 8 个角色
        roles.clear();
        for (int i = 1; i <= 8; i++) roles.add(new Role(i));
        // 随机指定 3 个为伶人
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 8; i++) indices.add(i);
        Collections.shuffle(indices, rand);
        for (int k = 0; k < 3; k++) {
            roles.get(indices.get(k)).isActor = true;
        }
        // 在三位伶人中随机设定一个为面具伶人
        List<Role> actors = roles.stream().filter(r -> r.isActor).collect(Collectors.toList());
        Role mask = actors.get(rand.nextInt(actors.size()));
        mask.isMaskActor = true;
        // 初始化其他状态
        eliminatedOrder.clear();
        eliminationReason.clear();
        foolBuff = false;
        crowChosenForToday = null;
        crowActiveToday = false;
        maskPickedSecret = null;
        gameStarted = true;
        System.out.println("游戏开始：已生成 8 位角色（编号 1-8）。三位为伶人（身份对玩家保密），其中一位为面具伶人（也保密）。");
    }

    // ---------- Day 1 ----------
    void day1() {
        System.out.println("\n=== 第一天 ===");
        System.out.println("随机事件发生（按权重随机选择一个事件触发）...");
        triggerRandomEvent(1);

        System.out.println("\n现在进入 斗舞场 · 一（战斗环节）。");
        dancePhase(1, null); // day 1 dance
        System.out.println("第一日结束。\n");
    }

    // ---------- Day 2 ----------
    void day2() {
        System.out.println("\n=== 第二日 开始 ===");
        // 悲歌：一位角色在悲歌中黯然离场（不会使伶人离场 -> 随机选一位活着的舞者离场）
        System.out.println("伶人的悲歌响起：一位 **舞者**（非伶人）将被迫离场（该悲歌不会使伶人离场）。");
        Role removed = removeOneAliveByFaction(false, "悲歌使舞者离场（第2日）");
        if (removed != null) {
            System.out.printf("被悲歌离场的编号：%d（已从舞场移出）。\n", removed.id);
        } else {
            System.out.println("当前没有存活的舞者可以被悲歌移出（跳过）。");
        }

        // 随机事件（在斗舞前）
        System.out.println("\n随机事件发生（按权重随机选择一个事件触发）...");
        triggerRandomEvent(2);

        // 中场休息（固定）
        System.out.println("\n中场休息（固定事件）：请选择 **三位**角色，他们将不参与接下来的斗舞（战斗环节）但在战斗后回到舞场。");
        List<Integer> sitOut = chooseUniqueIds(3, "请输入要暂时休息（不参与下一次斗舞）的三位角色编号（逗号或空格分隔）。范围：当前存活的编号");
        System.out.println("这三位将在接下来的斗舞中暂时离场（但不会永久淘汰）： " + sitOut);

        // 斗舞场 · 二
        System.out.println("\n现在进入 斗舞场 · 二（战斗环节）。");
        dancePhase(2, sitOut);

        // 战斗后，中场休息的那三位回到舞场（他们并没有被标记为淘汰）
        System.out.println("中场休息的三位角色回到舞场。");
    }

    // ---------- Day 3 ----------
    void day3() {
        System.out.println("\n=== 第三日 · 终幕 开始 ===");
        // 悲歌在第三日开始
        System.out.println("伶人的悲歌再次响起：一位 **舞者**（非伶人）将被迫离场（该悲歌不会使伶人离场）。");
        Role removed = removeOneAliveByFaction(false, "悲歌使舞者离场（第3日）");
        if (removed != null) {
            System.out.printf("被悲歌离场的编号：%d（已从舞场移出）。\n", removed.id);
        } else {
            System.out.println("当前没有存活的舞者可以被悲歌移出（跳过）。");
        }

        // 揭幕时刻（固定事件）
        System.out.println("\n揭幕时刻（固定事件）：你可以得知前两日（斗舞环节）出局角色的真实身份。");
        revealDanceEliminations();

        // 面具伶人的被动技能：若面具伶人在第三日仍存活，则为随机一名存活角色带上面具（使该角色在舞池中阵营倒转）。
        Role maskActor = roles.stream().filter(r -> r.isMaskActor && r.alive).findFirst().orElse(null);
        if (maskActor != null) {
            maskActorAliveAtDay3 = true;
            // 随机选一名存活角色（可以是伶人或舞者，但不要是玩家，且需是存活编号）
            List<Role> aliveRoles = roles.stream().filter(r -> r.alive).collect(Collectors.toList());
            if (!aliveRoles.isEmpty()) {
                Role chosen = aliveRoles.get(rand.nextInt(aliveRoles.size()));
                chosen.masked = true; // 标记该角色被面具佩戴（临时翻转）
                maskPickedSecret = chosen.id;
                System.out.println("（系统说明：面具伶人若存活，会对一位存活角色施加面具——该选择是秘密的，玩家不会看到编号。）");
            }
        } else {
            maskActorAliveAtDay3 = false;
            System.out.println("（系统说明：面具伶人在第三日已不在场，面具技能不会触发。）");
        }

        // 压轴舞台：玩家在仍在舞场的角色中选择两位（和自己一同进入最后的舞池）
        System.out.println("\n压轴舞台：请选择 **两位**当前存活的角色编号（范围：存活编号），与您（玩家）一同进入最后的舞池。");
        List<Integer> alive = getAliveIds();
        System.out.println("范围："+alive);
        if (alive.size() < 2) {
            System.out.println("当前存活角色少于 2 位，无法组成舞池。直接判定结局。");
            return;
        }
        List<Integer> chosen = chooseUniqueIdsFromList(2, alive, "请输入要带入压轴舞台的两位角色编号（逗号或空格分隔）");
        System.out.println("你选择进入压轴舞台的两位编号为：" + chosen);

        // 构成舞池（player + two chosen）。检查阵营一致性（考虑面具翻转效果）
        // 先确定两位的“当前阵营”（若被 mask 翻转，则翻转 isActor）
        Map<Integer, Boolean> currentFaction = new HashMap<>();
        for (Role r : roles) {
            if (!r.alive) continue;
            boolean faction = r.isActor;
            if (r.masked) faction = !faction; // 面具翻转
            currentFaction.put(r.id, faction);
        }
        boolean a1 = currentFaction.get(chosen.get(0));
        boolean a2 = currentFaction.get(chosen.get(1));
        // 玩家自己的阵营视为“舞者”（非伶人）？——这里有一个实际规则的歧义：
        //   玩家并非编号1-8中的一员，因此其“阵营”需要一个定义。为了可玩性，这里我们**把玩家视为舞者阵营**（非伶人）。
        //   该选择来自于合理化：玩家通常不是伶人阵营，因此把玩家作为舞者会让“舞池一致性”有意义。
        boolean playerFactionIsActor = false; // false 表示玩家为舞者阵营
        int differingId = -1;
        String differingFactionName = "";
        // 如果三者不全相同，找出与另外两者不同的那一位并使其立即离场
        List<Boolean> factions = Arrays.asList(playerFactionIsActor, a1, a2);
        // We map indices: 0->player, 1->chosen[0], 2->chosen[1]
        if (allEqual(factions)) {
            System.out.println("\n舞池检查：三者阵营一致。无人被立刻驱逐。");
            System.out.println("结局：无人出局 -> 永续狂欢（胜利）。");
        } else {
            // 找到不同者（若有两人阵营相同且第三者不同，则第三者离场）
            boolean majority = majorityFaction(factions);
            int idxDifferent = -1;
            for (int i = 0; i < 3; i++) {
                if (factions.get(i) != majority) idxDifferent = i;
            }
            if (idxDifferent == 0) {
                // player 被驱逐（理论上玩家被驱逐：这会导致玩家死亡 -> 失败）
                System.out.println("\n舞池检查：玩家的阵营与另外两位不同，玩家被立即移出舞池（玩家出局）！");
                System.out.println("结局：玩家被出局 -> 游戏失败。");
            } else {
                differingId = chosen.get(idxDifferent - 1);
                differingFactionName = currentFaction.get(differingId) ? "伶人" : "舞者";
                // 将该角色标记为离场（出局）
                Role r = getRoleById(differingId);
                if (r != null && r.alive) {
                    r.alive = false;
                    eliminatedOrder.add(differingId);
                    eliminationReason.put(differingId, "压轴舞台阵营不合被驱逐（终局）");
                    System.out.printf("\n舞池结果：编号 %d 被立即移出舞池（阵营：%s）。\n", differingId, differingFactionName);
                    // 根据其真实身份判断结局
                    if (r.isActor) {
                        System.out.println("结局：出局为伶人 -> 悲剧的落幕（特殊胜利结局）。");
                    } else {
                        System.out.println("结局：出局为舞者 -> 游戏失败。");
                    }
                } else {
                    System.out.println("异常：要被驱逐的角色已不在场（可能之前被淘汰），忽略。");
                }
            }
        }
        // 显示面具秘密（供调试/完整回合记录）
        System.out.println("\n---（系统总结）---");
        if (maskActorAliveAtDay3) {
            System.out.println("面具伶人存活并已在秘密中选定一名角色佩戴面具（该选择未提前告知玩家）。");
            if (maskPickedSecret != null) {
                System.out.println("（秘密）被戴上面具的编号是：" + maskPickedSecret + "，该角色的阵营已在压轴舞台效果中被视作已翻转。");
            }
        } else {
            System.out.println("面具伶人在第三日已不在场，未触发面具技能。");
        }
    }

    // 在所有战斗结束后（或按规则需要时）揭示前两日斗舞中被淘汰编号的真实身份
    void revealDanceEliminations() {
        if (eliminatedOrder.isEmpty()) {
            System.out.println("截至目前（前两日）没有在斗舞环节出局的角色。");
            return;
        }
        System.out.println("以下是在前两日斗舞中出局的角色（编号 -> 真实身份）：");
        // 只透露被斗舞淘汰的那些（eliminationReason 中标注为斗舞）
        for (Integer id : eliminatedOrder) {
            String reason = eliminationReason.getOrDefault(id, "被淘汰（原因未知）");
            Role r = getRoleById(id);
            if (r == null) continue;
            System.out.printf("编号 %d -> %s （原因：%s）\n", id, r.faction(), reason);
        }
    }

    // 最终总结并判断玩家是否胜利（依据：玩家存活三日，且最后一日舞池中没有真正的舞者出局）
    void revealFinalResult() {
        System.out.println("\n=== 游戏结束 总结 ===");
        // 玩家在本实现中没有被直接淘汰（如被终局驱逐会被提示），所以判断“玩家生存三日”视为 true（除非在压轴舞台被驱逐）
        // 简单判定最后一日结局文本中是否为失败（舞者出局或玩家出局）
        // 为简明：如果最后被驱逐的编号存在且其真实身份是 舞者 -> 失败；否则视作胜利（包含特殊胜利）。
        // 查找 eliminationReason 中是否存在终局原因
        boolean finalFailure = false;
        for (Map.Entry<Integer, String> e : eliminationReason.entrySet()) {
            if (e.getValue().contains("终局") ) {
                Role r = getRoleById(e.getKey());
                if (r != null && !r.isActor) finalFailure = true;
            }
        }
        if (finalFailure) {
            System.out.println("结局判断：失败（压轴舞台出局为舞者）。");
        } else {
            System.out.println("结局判断：玩家达成存活要求且压轴舞台没有导致舞者被出局 → 胜利（或特殊胜利）。");
        }
        // 最后显示所有角色（1-8）的真实身份，供玩家回顾
        System.out.println("\n（所有角色身份揭示）编号 -> 身份（面具伶人有标记）:");
        for (Role r : roles) {
            System.out.printf("%d -> %s %s %s\n", r.id, r.faction(), r.isMaskActor ? "[面具伶人]" : "", r.alive ? "" : "(已离场)");
        }
        System.out.println("\n感谢游玩。");
    }

    // ---------- 辅助方法 ----------

    // 执行随机事件（按权重）
    void triggerRandomEvent(int day) {
        // 事件权重：
        // 先知 Prophet (30%)
        // 酒保 Bartender (15%)
        // 愚者 Fool (10%)
        // 乐手 Musician (20%)
        // 乌鸦 Crow (15%)
        // 剧作家 Playwright (10%)
        double r = rand.nextDouble() * 100;
        if (r < 30) {
            eventProphet();
        } else if (r < 45) {
            eventBartender();
        } else if (r < 55) {
            eventFool();
        } else if (r < 75) {
            eventMusician();
        } else if (r < 90) {
            eventCrow(day);
        } else {
            eventPlaywright();
        }
    }

    // 先知：可以选择一位角色，获得其具体身份信息。
    void eventProphet() {
        System.out.println("随机事件：先知（30%）触发。你可以选择一位角色，获得其身份信息。");
        List<Integer> alive = getAliveIds();
        int pick = chooseOneIdFromList(alive, "请选择要被先知查看身份的编号。范围：" + alive);
        Role r = getRoleById(pick);
        System.out.printf("先知告诉你：编号 %d 的身份为：%s。\n", r.id, r.faction());
    }

    // 酒保：可选复活上一位离场角色，或使一位角色离场（催眠酒）
    void eventBartender() {
        System.out.println("随机事件：酒保（15%）触发。你可以选择：");
        System.out.println("1) 调疗愈酒，唤醒上一位离场的角色（若存在）");
        System.out.println("2) 调催眠酒，使一位存活角色离场（选择编号）");
        int choice = chooseIntegerInRange(1, 2, "输入 1 或 2 选择操作：");
        if (choice == 1) {
            // 唤醒上一位离场的角色（按 eliminatedOrder 最近的一位）
            if (eliminatedOrder.isEmpty()) {
                System.out.println("目前没有被淘汰的角色可以唤醒（无效果）。");
            } else {
                int last = eliminatedOrder.remove(eliminatedOrder.size() - 1);
                Role r = getRoleById(last);
                if (r != null) {
                    r.alive = true;
                    eliminationReason.put(last, "被酒保疗愈酒唤醒（回归）");
                    System.out.printf("酒保疗愈成功：编号 %d 被唤醒并回到舞场。\n", last);
                } else {
                    System.out.println("回溯失败（找不到被唤醒的角色）。");
                }
            }
        } else {
            List<Integer> alive = getAliveIds();
            if (alive.isEmpty()) {
                System.out.println("没有存活角色可被催眠（无效果）。");
            } else {
                int pick = chooseOneIdFromList(alive, "请选择要被催眠离场的角色编号。范围：" + alive);
                Role r = getRoleById(pick);
                if (r != null) {
                    r.alive = false;
                    eliminatedOrder.add(r.id);
                    eliminationReason.put(r.id, "被酒保催眠酒使其离场");
                    System.out.printf("编号 %d 被酒保的催眠酒使其离场。\n", r.id);
                }
            }
        }
    }

    // 愚者：当本轮游戏的舞场首次死亡时恢复全部生命（此实现记录为 Buff）
    void eventFool() {
        System.out.println("随机事件：愚者（10%）触发。愚者的效果：在本轮游戏的舞场首次死亡时玩家将恢复全部生命（在本实现中表现为一条 Buff 提示）。");
        foolBuff = true;
        System.out.println("（提示）你获得了愚者的祝福（若出现能导致玩家死亡的事件，可用于抵消一次）。");
    }

    // 乐手：玩家选择两位角色组织双人乐团。若乐团中存在伶人则会产生不和谐的音乐，告知玩家结果。
    void eventMusician() {
        System.out.println("随机事件：乐手（20%）触发。请选择两位角色组成双人乐团（若乐团中存在伶人则产生不和谐的音乐）。");
        List<Integer> alive = getAliveIds();
        if (alive.size() < 2) {
            System.out.println("当前可选角色不足两位，乐手效果无效。");
            return;
        }
        List<Integer> picks = chooseUniqueIdsFromList(2, alive, "请输入两位编号（逗号或空格分隔）。范围：" + alive);
        Role r1 = getRoleById(picks.get(0));
        Role r2 = getRoleById(picks.get(1));
        boolean discord = (r1.isActor || r2.isActor);
        if (discord) {
            System.out.printf("结果：乐团中存在伶人（编号 %d 或 %d），产生不和谐的音乐（你被告知音乐的不和谐）。\n", r1.id, r2.id);
        } else {
            System.out.printf("结果：两人皆为舞者（编号 %d 与 %d），音乐和谐。\n", r1.id, r2.id);
        }
    }

    // 乌鸦：玩家获得祝福，在本日斗舞环节中首个攻击的角色若为伶人，可使其立即出局。
    // 在这里模拟为：在随机事件时玩家提供一个号码（范围：存活编号），若其为伶人则在斗舞时直接出局，
    // 否则在其他存活号码中随机一个号码出局（该决定在斗舞时应用）
    void eventCrow(int day) {
        System.out.println("随机事件：乌鸦（15%）触发。你获得乌鸦的祝福：在今日斗舞中，首个被你指定攻击的编号若为伶人则其直接出局。");
        List<Integer> alive = getAliveIds();
        if (alive.isEmpty()) {
            System.out.println("当前没有存活角色，乌鸦效果无用。");
            return;
        }
        int pick = chooseOneIdFromList(alive, "请现在输入一个编号来指定乌鸦的目标（该选择将在本日斗舞中被使用）。范围：" + alive);
        crowChosenForToday = pick;
        crowActiveToday = true;
        System.out.printf("你已为本日斗舞指定乌鸦目标编号 %d（该信息将用于本日斗舞中）。\n", pick);
    }

    // 剧作家：直接使面具伶人离开舞场，但是要告知玩家离场的号码；若面具伶人已经离场则无效果。
    void eventPlaywright() {
        System.out.println("随机事件：剧作家（10%）触发。剧作家会直接使面具伶人离开舞场（若其仍在场），并告知玩家该离场的编号。");
        Role mask = roles.stream().filter(r -> r.isMaskActor && r.alive).findFirst().orElse(null);
        if (mask == null) {
            System.out.println("面具伶人已不在场，剧作家无效果。");
        } else {
            mask.alive = false;
            eliminatedOrder.add(mask.id);
            eliminationReason.put(mask.id, "被剧作家直接写出场（面具伶人）");
            System.out.printf("剧作家使面具伶人离场！被迫离场的编号为：%d（该信息已告知玩家）。\n", mask.id);
        }
    }

    // 斗舞阶段：day 指示第几日， sitOut 为本次斗舞暂时不参与的编号列表（可以为 null）
    void dancePhase(int day, List<Integer> sitOut) {
        // 在进入斗舞环节前，处理乌鸦效果预先指定的目标（说明：乌鸦的行为在这里模拟时已记录为 crowChosenForToday）
        // 当进行斗舞时，若 crowActiveToday 为 true，我们需要按事件说明执行：
        // - 玩家在斗舞环节提供一个号码（程序会提示），若该号码为伶人 -> 直接出局（并告诉玩家该编号出局）
        // - 否则，在其他存活号码中随机一个号码出局
        // 但仍要保证随机事件是先于斗舞并且玩家输入在斗舞前完成（我们在触发乌鸦事件时已请求玩家提前提供号码）
        List<Integer> available = getAliveIds();
        if (sitOut != null) {
            // 从可选名单中去除 sitOut
            available = available.stream().filter(id -> !sitOut.contains(id)).collect(Collectors.toList());
        }
        if (available.isEmpty()) {
            System.out.println("本次斗舞没有可参与的角色（全部暂时休息或已离场）。");
            return;
        }

        // 提示玩家选择模式：直接选一个编号淘汰某角色，或按“随机”模式
        System.out.println("斗舞选择：你可以选择：");
        System.out.println("1) 直接指定一个编号使其出局（输入编号）");
        System.out.println("2) 选择随机（输入 'random'），此时 50% 概率出局为伶人，50% 为除玩家外的舞者（随机）");
        System.out.println("注意：如果乌鸦事件在本日激活（你先前已指定了目标），该效果将在本轮斗舞中生效（将按乌鸦规则处理）。");
        // 如果乌鸦激活，并且已在随机事件时记录 player-supplied crowChosenForToday，那么在斗舞开始前应确保该号码存在并提示玩家（但不重复要求）
        if (crowActiveToday) {
            System.out.printf("（乌鸦已激活）本日乌鸦目标为编号 %d（由你在随机事件发生时指定）。\n", crowChosenForToday);
        }

        // 要求玩家输入要淘汰的编号或 'random'
        while (true) {
            System.out.printf("请输入要淘汰的编号（可选范围 %s）或输入 'random'：%n", available);
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("random")) {
                // 随机模式：50% 概率出局为伶人，50% 为除玩家外的舞者
                boolean actorOutcome = rand.nextBoolean();
                if (actorOutcome) {
                    // 从 alive actors 中（并排除 sitOut）随机选取一位出局
                    List<Role> aliveActors = roles.stream()
                            .filter(r -> r.alive && r.isActor && (sitOut == null || !sitOut.contains(r.id)))
                            .collect(Collectors.toList());
                    if (!aliveActors.isEmpty()) {
                        Role chosen = aliveActors.get(rand.nextInt(aliveActors.size()));
                        chosen.alive = false;
                        eliminatedOrder.add(chosen.id);
                        eliminationReason.put(chosen.id, "斗舞（随机模式）被淘汰");
                        System.out.printf("随机斗舞结果：编号 %d 被淘汰（仅告知编号）。\n", chosen.id);
                    } else {
                        // 若没有伶人可被淘汰，则随机选一个其他存活的角色
                        List<Integer> alt = available;
                        int pick = alt.get(rand.nextInt(alt.size()));
                        Role chosen = getRoleById(pick);
                        if (chosen != null) {
                            chosen.alive = false;
                            eliminatedOrder.add(chosen.id);
                            eliminationReason.put(chosen.id, "斗舞（随机模式，伶人缺失）被淘汰");
                            System.out.printf("随机斗舞结果（无伶人可选）：编号 %d 被淘汰（仅告知编号）。\n", chosen.id);
                        }
                    }
                } else {
                    // 出局为除玩家外的舞者 -> 从 alive dancers 中选一个
                    List<Role> aliveDancers = roles.stream()
                            .filter(r -> r.alive && !r.isActor && (sitOut == null || !sitOut.contains(r.id)))
                            .collect(Collectors.toList());
                    if (!aliveDancers.isEmpty()) {
                        Role chosen = aliveDancers.get(rand.nextInt(aliveDancers.size()));
                        chosen.alive = false;
                        eliminatedOrder.add(chosen.id);
                        eliminationReason.put(chosen.id, "斗舞（随机模式）被淘汰");
                        System.out.printf("随机斗舞结果：编号 %d 被淘汰（仅告知编号）。\n", chosen.id);
                    } else {
                        // 若没有舞者可被淘汰，则随机选一个其他存活的角色
                        List<Integer> alt = available;
                        int pick = alt.get(rand.nextInt(alt.size()));
                        Role chosen = getRoleById(pick);
                        if (chosen != null) {
                            chosen.alive = false;
                            eliminatedOrder.add(chosen.id);
                            eliminationReason.put(chosen.id, "斗舞（随机模式，舞者缺失）被淘汰");
                            System.out.printf("随机斗舞结果（无舞者可选）：编号 %d 被淘汰（仅告知编号）。\n", chosen.id);
                        }
                    }
                }
                // 在随机模式下，乌鸦的事如果激活则不重复触发（乌鸦在其事件中已经要求玩家输入并将用于指定首个攻击）
                crowActiveToday = false;
                crowChosenForToday = null;
                break;
            } else {
                // 玩家输入了一个编号 -> validate
                int pick;
                try {
                    pick = Integer.parseInt(input);
                } catch (NumberFormatException ex) {
                    System.out.println("输入无效，请输入合法编号或 'random'。");
                    continue;
                }
                if (!available.contains(pick)) {
                    System.out.println("所选编号不在可参与本次斗舞的范围内，请重新选择。");
                    continue;
                }
                // 若乌鸦激活且 crowChosenForToday 指定了某编号，则我们需要优先处理乌鸦描述：
                // 本实现遵循你给的说明：乌鸦在随机事件时会要求玩家提供一个号码（已保存为 crowChosenForToday）。
                // 但是在斗舞时若玩家自己再次选择，乌鸦效果仍以早前的选择为准（已告知玩家）。
                // 为简化交互：如果玩家现在选择的编号恰好等于 crowChosenForToday，则按乌鸦规则处理；
                // 否则按普通玩家选择处理。
                if (crowActiveToday && crowChosenForToday != null) {
                    // 玩家在斗舞中实际选择的编号为 pick，但乌鸦的目标是 crowChosenForToday
                    // 依照题目给的“模拟规则”：在这里模拟为，斗舞环节时，玩家提供一个号码，若其为伶人直接出局，否则在其他存活号码中随机一个号码出局
                    // 但因为我们已在随机事件阶段要求玩家先提供了乌鸦目标，因此采用该目标进行判定
                    // 如果 crowChosenForToday 是伶人 -> 该伶人直接出局，否则在其他存活号码中随机出局
                    int crowTarget = crowChosenForToday;
                    Role crowRole = getRoleById(crowTarget);
                    if (crowRole != null && crowRole.alive) {
                        if (crowRole.isActor) {
                            // 直接出局
                            crowRole.alive = false;
                            eliminatedOrder.add(crowRole.id);
                            eliminationReason.put(crowRole.id, "乌鸦效果（本日）直接出局");
                            System.out.printf("乌鸦效果触发：编号 %d（伶人）被直接出局（仅告知编号）。\n", crowRole.id);
                        } else {
                            // 否则在其他存活号码中随机一个号码出局（排除 crowTarget）
                            List<Role> pool = roles.stream()
                                    .filter(r -> r.alive && r.id != crowTarget && (sitOut == null || !sitOut.contains(r.id)))
                                    .collect(Collectors.toList());
                            if (!pool.isEmpty()) {
                                Role chosen = pool.get(rand.nextInt(pool.size()));
                                chosen.alive = false;
                                eliminatedOrder.add(chosen.id);
                                eliminationReason.put(chosen.id, "乌鸦效果（本日）随机淘汰");
                                System.out.printf("乌鸦效果：目标非伶人，随机淘汰了编号 %d（仅告知编号）。\n", chosen.id);
                            } else {
                                System.out.println("乌鸦效果：没有其他可淘汰的存活角色（无效果）。");
                            }
                        }
                    } else {
                        System.out.println("乌鸦目标当前不在场或已被淘汰，乌鸦效果无效。");
                    }
                    // 乌鸦只能用一次
                    crowActiveToday = false;
                    crowChosenForToday = null;
                    break;
                } else {
                    // 普通玩家指定一个编号 -> 该编号被淘汰
                    Role chosen = getRoleById(pick);
                    if (chosen != null && chosen.alive) {
                        chosen.alive = false;
                        eliminatedOrder.add(chosen.id);
                        eliminationReason.put(chosen.id, "斗舞（玩家指定）被淘汰");
                        System.out.printf("你指定淘汰编号 %d（仅告知编号）。\n", chosen.id);
                        // 若愚者的buff在且这次导致玩家死亡的事件发生，本实现没有玩家直接死亡的场景
                    } else {
                        System.out.println("所选编号已不在场或已被淘汰，请重新操作。");
                        continue;
                    }
                    break;
                }
            }
        }
    }

    // removeOneAliveByFaction(false) 表示移除一位舞者；true 表示移除一位伶人（用于扩展），并记录原因
    Role removeOneAliveByFaction(boolean actor, String reason) {
        List<Role> cand = roles.stream().filter(r -> r.alive && r.isActor == actor).collect(Collectors.toList());
        if (cand.isEmpty()) return null;
        Role chosen = cand.get(rand.nextInt(cand.size()));
        chosen.alive = false;
        eliminatedOrder.add(chosen.id);
        eliminationReason.put(chosen.id, reason);
        return chosen;
    }

    // get list of alive ids
    List<Integer> getAliveIds() {
        return roles.stream().filter(r -> r.alive).map(r -> r.id).collect(Collectors.toList());
    }

    Role getRoleById(int id) {
        return roles.stream().filter(r -> r.id == id).findFirst().orElse(null);
    }

    // 输入辅助：选择一个合法的编号（从 alive 列表）
    int chooseOneIdFromList(List<Integer> alive, String prompt) {
        while (true) {
            System.out.println(prompt);
            System.out.print("> ");
            String s = scanner.nextLine().trim();
            int pick;
            try { pick = Integer.parseInt(s); }
            catch (Exception e) { System.out.println("输入有误，请输入一个编号。"); continue; }
            if (!alive.contains(pick)) { System.out.println("编号不在范围内，请重新输入。"); continue; }
            return pick;
        }
    }

    // 输入辅助：从给定列表中选择 n 个互不相同的编号（用于中场休息、压轴舞台等）
    List<Integer> chooseUniqueIdsFromList(int n, List<Integer> pool, String prompt) {
        while (true) {
            System.out.println(prompt);
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            String[] parts = line.split("[,\\s]+");
            Set<Integer> picks = new LinkedHashSet<>();
            boolean ok = true;
            for (String p : parts) {
                if (p.isEmpty()) continue;
                try {
                    int x = Integer.parseInt(p);
                    if (!pool.contains(x)) { ok = false; break; }
                    picks.add(x);
                } catch (Exception e) { ok = false; break; }
            }
            if (!ok || picks.size() != n) {
                System.out.println("输入无效：请提供 " + n + " 个互不相同并且在范围内的编号。示例格式：1 3 5 或 1,3,5");
                continue;
            }
            return new ArrayList<>(picks);
        }
    }

    // 输入辅助：选择 n 个互不相同的编号，从当前存活者中选择（用于中场休息）
    List<Integer> chooseUniqueIds(int n, String prompt) {
        List<Integer> alive = getAliveIds();
        return chooseUniqueIdsFromList(n, alive, prompt);
    }

    // 输入整数范围
    int chooseIntegerInRange(int lo, int hi, String prompt) {
        while (true) {
            System.out.println(prompt);
            System.out.print("> ");
            String s = scanner.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < lo || v > hi) {
                    System.out.printf("请输入 %d 到 %d 之间的整数。\n", lo, hi);
                    continue;
                }
                return v;
            } catch (Exception e) {
                System.out.println("请输入有效整数。");
            }
        }
    }

    // Helpers for factions
    boolean allEqual(List<Boolean> arr) {
        for (int i = 1; i < arr.size(); i++) if (!arr.get(i).equals(arr.get(0))) return false;
        return true;
    }

    boolean majorityFaction(List<Boolean> arr) {
        int cntTrue = 0;
        for (boolean b : arr) if (b) cntTrue++;
        return cntTrue >= 2;
    }
}

