package com.fc.v2.common.exception;

/**
 * 台站核点卡立卡规矩异常。
 *
 * <p>卡面上某一栏填得不对（应核/已核点位数为负、已核大过应核等），卡进不了库时抛出。
 * 消息里点名是哪一栏不对——这一句经全局异常处理落到回执上，递卡的人照着改。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastCardRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BcastCardRuleException(String message) {
        super(message);
    }
}
