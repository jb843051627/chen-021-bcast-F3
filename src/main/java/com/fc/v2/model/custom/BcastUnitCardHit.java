package com.fc.v2.model.custom;

import java.io.Serializable;

import com.fc.v2.model.auto.TBcastUnitCard;

/**
 * 寻卡命中：卡面之外，把所属台站与它压在哪一层（属地那一路）一并带回来，不必再让人抄第二回。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastUnitCardHit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 卡面 */
    private TBcastUnitCard card;

    /** 所属台站代号 */
    private String siteNo;

    /** 台站全称 */
    private String siteName;

    /** 台站类别（无线发射台/微波站/转播台） */
    private String siteType;

    /** 压在哪一层：属地那一路（省—市—县） */
    private String roadName;

    /** 系统按已完成级数说出的档：待核/核讫/压卡（旧卡空档按待核起算） */
    private String statusWord;

    public BcastUnitCardHit() {
    }

    public BcastUnitCardHit(TBcastUnitCard card, String siteNo, String siteName, String siteType,
                            String roadName, String statusWord) {
        this.card = card;
        this.siteNo = siteNo;
        this.siteName = siteName;
        this.siteType = siteType;
        this.roadName = roadName;
        this.statusWord = statusWord;
    }

    public TBcastUnitCard getCard() {
        return card;
    }

    public void setCard(TBcastUnitCard card) {
        this.card = card;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
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

    public String getStatusWord() {
        return statusWord;
    }

    public void setStatusWord(String statusWord) {
        this.statusWord = statusWord;
    }
}
