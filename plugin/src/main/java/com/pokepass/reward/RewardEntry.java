package com.pokepass.reward;

/** 一条奖励。语法见 reward.yml 顶部注释。 */
public class RewardEntry {

    public enum Kind {
        ITEM, COMMAND, POINTS, MONEY
    }

    private final Kind kind;
    private final String payload;
    private final int amount;
    private final short data;
    private final String raw;

    private RewardEntry(Kind kind, String payload, int amount, short data, String raw) {
        this.kind = kind;
        this.payload = payload;
        this.amount = amount;
        this.data = data;
        this.raw = raw;
    }

    public Kind getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public int getAmount() {
        return amount;
    }

    public short getData() {
        return data;
    }

    public String getRaw() {
        return raw;
    }

    /**
     * 解析一条奖励字符串。
     *
     * <pre>
     * item:DIAMOND:5        -> 5 个钻石
     * item:WOOL:1:14        -> 1 个红色羊毛（带数据值）
     * command:give {player} diamond 1
     * points:100            -> PlayerPoints 100 点
     * money:500             -> Vault 经济 500
     * </pre>
     */
    public static RewardEntry parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        int firstColon = text.indexOf(':');
        if (firstColon <= 0) {
            return null;
        }
        String head = text.substring(0, firstColon).trim().toLowerCase();
        String rest = text.substring(firstColon + 1).trim();
        if ("item".equals(head)) {
            String[] parts = rest.split(":");
            if (parts.length < 1 || parts[0].trim().isEmpty()) {
                return null;
            }
            String material = parts[0].trim();
            int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
            short data = (short) (parts.length > 2 ? parseInt(parts[2], 0) : 0);
            return new RewardEntry(Kind.ITEM, material, Math.max(1, amount), data, raw);
        }
        if ("command".equals(head) || "cmd".equals(head)) {
            return new RewardEntry(Kind.COMMAND, rest, 1, (short) 0, raw);
        }
        if ("points".equals(head) || "point".equals(head) || "pp".equals(head)) {
            return new RewardEntry(Kind.POINTS, rest, Math.max(0, parseInt(rest, 0)), (short) 0, raw);
        }
        if ("money".equals(head) || "vault".equals(head)) {
            return new RewardEntry(Kind.MONEY, rest, Math.max(0, parseInt(rest, 0)), (short) 0, raw);
        }
        return null;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
