package com.fc.v2.model.custom;

import java.io.Serializable;

import com.fc.v2.model.auto.TBcastUnitCard;

/**
 * 寻卡寻到的那一张：卡本身连同所属台站、它压在哪一层一并带回来，
 * 不必再让人抄第二回。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastUnitCardHit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 寻到的那一张卡（卡号、属地层级序、应核/已核/还欠、进展都在卡面上） */
    private TBcastUnitCard card;

    /** 所属台站全称（底册里的） */
    private String siteName;

    /** 台站类别（无线发射台/微波站/转播台） */
    private String siteType;

    /** 属地那一路（省—市—县） */
    private String roadName;

    public BcastUnitCardHit() {
    }

    public BcastUnitCardHit(TBcastUnitCard card, String siteName, String siteType, String roadName) {
        this.card = card;
        this.siteName = siteName;
        this.siteType = siteType;
        this.roadName = roadName;
    }

    public TBcastUnitCard getCard() {
        return card;
    }

    public void setCard(TBcastUnitCard card) {
        this.card = card;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSiteType() {
        return siteType;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }
}
