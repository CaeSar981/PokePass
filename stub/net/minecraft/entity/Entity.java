package net.minecraft.entity;

/** 编译期桩：Pixelmon API 签名引用 NMS 类，编译期只需要类文件存在。运行时用服务器真实实现。 */
public class Entity {

    public String getName() {
        return "";
    }

    public int getEntityId() {
        return 0;
    }

    public boolean isDead() {
        return false;
    }
}
