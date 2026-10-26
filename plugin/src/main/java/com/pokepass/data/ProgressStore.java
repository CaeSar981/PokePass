package com.pokepass.data;

import java.util.Collection;
import java.util.UUID;

/** 战令进度存储。实现之间可以互换，业务层只认这个接口。 */
public interface ProgressStore {

    PlayerProgress load(UUID uuid);

    void save(PlayerProgress progress);

    /** 批量刷盘，退出/重载时用 */
    void saveAll(Collection<PlayerProgress> progresses);

    void close();

    String describe();
}
