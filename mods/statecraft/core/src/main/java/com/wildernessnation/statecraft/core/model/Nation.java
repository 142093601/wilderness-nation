package com.wildernessnation.statecraft.core.model;

/**
 * 一个国家（不可变快照）。
 *
 * <p>坐标用 double 存世界坐标；这是<strong>抽象位置</strong>，不等于"已经放了建筑"——
 * 实体化由 mod 层负责。
 */
public record Nation(
        String id,
        String name,
        String cultureId,
        int size,
        double development,
        double stance,
        double military,
        double treasury,
        double x,
        double z,
        boolean met,
        double attitudeToParty) {

    public Nation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Nation.id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nation.name 不能为空：" + id);
        }
        if (size < 1 || size > 10) {
            throw new IllegalArgumentException("规模必须 1..10：" + size);
        }
        if (development < 0.0 || development > 100.0) {
            throw new IllegalArgumentException("发展度必须 0..100：" + development);
        }
        if (stance < -100.0 || stance > 100.0) {
            throw new IllegalArgumentException("政治倾向必须 -100..100：" + stance);
        }
        if (attitudeToParty < -100.0 || attitudeToParty > 100.0) {
            throw new IllegalArgumentException("态度必须 -100..100：" + attitudeToParty);
        }
    }

    /** 到某个点的水平距离（出生点默认在原点附近时可直接比较）。 */
    public double distanceTo(double ox, double oz) {
        return Math.hypot(x - ox, z - oz);
    }

    public Nation withPosition(double nx, double nz) {
        return new Nation(id, name, cultureId, size, development, stance,
                military, treasury, nx, nz, met, attitudeToParty);
    }
}
