package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 季度核点覆盖率。
 *
 * <p>只认两个数：属地里台站的总数（分母，不是已经建了卡的数），和已经核讫的张数（走到核讫、
 * 压卡两档的都算核讫过）。比率由这两个数当场一次算定，县并市、市并省与各屏点出来的同一次
 * 算出来；不用"大约九成"这种话回话。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastCoverage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 本级层级序 0省 1市 2县 */
    private Integer nodeNo;

    /** 本级属地那一路前缀（如 长岭省—河湾区） */
    private String roadPrefix;

    /** 分母：属地那一路底下台站的总数（关停台仍在底册内，照数；不是已建卡数） */
    private int siteTotal;

    /** 分子：本季已经核讫的卡张数（核讫、压卡两档都算） */
    private int verifiedCards;

    /** 比率：核讫张数 ÷ 台站总数，保留四位小数；台站总数为 0 时为 0 */
    private BigDecimal rate;

    /** 分数原话，如 74/80 —— 屏上就落这两个数 */
    private String rateText;

    public void calc() {
        if (siteTotal <= 0) {
            this.rate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            this.rateText = "0/0";
        } else {
            this.rate = new BigDecimal(verifiedCards).divide(new BigDecimal(siteTotal), 4, RoundingMode.HALF_UP);
            this.rateText = verifiedCards + "/" + siteTotal;
        }
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getRoadPrefix() {
        return roadPrefix;
    }

    public void setRoadPrefix(String roadPrefix) {
        this.roadPrefix = roadPrefix;
    }

    public int getSiteTotal() {
        return siteTotal;
    }

    public void setSiteTotal(int siteTotal) {
        this.siteTotal = siteTotal;
    }

    public int getVerifiedCards() {
        return verifiedCards;
    }

    public void setVerifiedCards(int verifiedCards) {
        this.verifiedCards = verifiedCards;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getRateText() {
        return rateText;
    }

    public void setRateText(String rateText) {
        this.rateText = rateText;
    }
}
