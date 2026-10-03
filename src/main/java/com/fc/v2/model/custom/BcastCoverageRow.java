package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 季表覆盖率一行：属地里台站的总数做分母、已经核讫的张数做分子，
 * 两个数都是点出来的实数，不拿"大约九成"回话。
 *
 * <p>县里的数并到市域、市域并到省域，一层层往上出；上一层的数与下一层
 * 各屏点出来的是同一次算的——本行对象就是那一次计算落下来的。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastCoverageRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 层级：省 */
    public static final int LEVEL_PROVINCE = 0;
    /** 层级：市 */
    public static final int LEVEL_CITY = 1;
    /** 层级：县 */
    public static final int LEVEL_COUNTY = 2;

    /** 属地那一路（省 / 省—市 / 省—市—县） */
    private String roadName;

    /** 层级：0省 1市 2县 */
    private int level;

    /** 属地里台站的总数（分母取这个，不是取已经建了卡的数） */
    private int totalStations;

    /** 已经核讫的张数（核讫、压卡都算已经核讫） */
    private int verifiedCards;

    public BcastCoverageRow() {
    }

    public BcastCoverageRow(String roadName, int level, int totalStations, int verifiedCards) {
        this.roadName = roadName;
        this.level = level;
        this.totalStations = totalStations;
        this.verifiedCards = verifiedCards;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getTotalStations() {
        return totalStations;
    }

    public void setTotalStations(int totalStations) {
        this.totalStations = totalStations;
    }

    public int getVerifiedCards() {
        return verifiedCards;
    }

    public void setVerifiedCards(int verifiedCards) {
        this.verifiedCards = verifiedCards;
    }

    /** 覆盖率按实数出：核讫张数/台站总数，如 "74/80"，不约成几成 */
    public String getCoverageText() {
        return verifiedCards + "/" + totalStations;
    }
}
