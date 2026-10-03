package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TBcastUnitCard;

/**
 * 台站核点卡立卡回执。
 *
 * <p>卡号、签认号一律由系统发，回执把这两个号连同折算好的还欠点位数一并带回；
 * 递卡时哪一栏不对（为负、带小数、大过应核），回执上点名是哪一栏，整卡不入库。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastUnitCardReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 回执点名用的栏名：所属台站 */
    public static final String FIELD_SITE = "所属台站";
    /** 回执点名用的栏名：属地层级序 */
    public static final String FIELD_NODE = "属地层级序";
    /** 回执点名用的栏名：应核点位数 */
    public static final String FIELD_SHOULD = "应核点位数";
    /** 回执点名用的栏名：已核点位数 */
    public static final String FIELD_DONE = "已核点位数";

    /** 收没收下：true 已落库；false 没入库，缘由在 errors 里点名 */
    private boolean accepted;

    /** 系统发出的卡号（人自家拟的不作数） */
    private String billNo;

    /** 系统发出的签认号（推进到核讫那一步发） */
    private String signNo;

    /** 折算落定的还欠点位数（只读栏，由应核-已核减出） */
    private Integer lackCount;

    /** 没入库时点名的错栏，一条一句，不带含糊话 */
    private List<String> errors = new ArrayList<String>();

    /** 收下后落定的卡；没收下为空 */
    private TBcastUnitCard card;

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getSignNo() {
        return signNo;
    }

    public void setSignNo(String signNo) {
        this.signNo = signNo;
    }

    public Integer getLackCount() {
        return lackCount;
    }

    public void setLackCount(Integer lackCount) {
        this.lackCount = lackCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public TBcastUnitCard getCard() {
        return card;
    }

    public void setCard(TBcastUnitCard card) {
        this.card = card;
    }
}
