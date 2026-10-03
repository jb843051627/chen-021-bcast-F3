package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 台站核点卡递卡表单。
 *
 * <p>应核、已核两个点位数用字符串接进来：为负的、带小数的（如 37.5）要在回执上点名，
 * 若直接用整数接，"37.5"在进门绑定那一步就被悄悄改了样，错的是哪一栏反倒说不出来。
 * 卡号、签认号、还欠、当前档一律不在表单上——这几样没有给人填的位置。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastUnitCardForm implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 卡主键（改卡时带，立卡时空） */
    private Long id;

    /** 所属发射台站（底册 id） */
    private Long siteId;

    /** 属地层级序 0省 1市 2县，卡挂的那一层 */
    private String nodeNo;

    /** 应核点位数（原文接，逐字验是不是非负整数） */
    private String shouldCount;

    /** 已核点位数（原文接，逐字验是不是非负整数、大不大于应核） */
    private String doneCount;

    /** 随卡交来的属地要件 */
    private String content;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(String nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getShouldCount() {
        return shouldCount;
    }

    public void setShouldCount(String shouldCount) {
        this.shouldCount = shouldCount;
    }

    public String getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(String doneCount) {
        this.doneCount = doneCount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
