package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverage;
import com.fc.v2.model.custom.BcastUnitCardForm;
import com.fc.v2.model.custom.BcastUnitCardHit;
import com.fc.v2.model.custom.BcastUnitCardReceipt;

/**
 * 台站核点卡 Service接口。
 *
 * <p>一张核点卡合不合规、眼下算哪一步、能不能再迈一步或退一步、还欠点位数折出几、
 * 按属地那一路该露哪几张，一律由本接口这几条方法独家定调；接口与界面都不开第二道
 * 绕得过去的口子。卡号、签认号由系统发；还欠栏由应核-已核减出，只读不写。</p>
 *
 * <p>步子只有三档，不得乱踏：待核(0) → 核讫(1) → 压卡(2)。压卡即封顶，添不进、
 * 改不动、撤不掉，要改只能另开一张。一座台站同时只容一张在走的卡（未压卡、未叫停），
 * 压卡之后名额才空出来。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITBcastUnitCardService {

    /** 步次：待核（刚立卡落这一档；纸质旧卡步数空着也按这一档起算） */
    int STAGE_PENDING = 0;
    /** 步次：核讫（推进到这一档由系统发签认号） */
    int STAGE_VERIFIED = 1;
    /** 步次：压卡（封顶，此后整卡冻结） */
    int STAGE_SEALED = 2;

    /** 按主键查询 */
    TBcastUnitCard selectTBcastUnitCardById(Long id);

    /** 可供挂卡的在册台站（已关停的不列）：立卡页只从这里取，关停站挂不上 */
    java.util.List<com.fc.v2.model.auto.TBcastSite> listLiveSites();

    /** 按条件查询列表（条件由调用方给，本方法不掺私货、不写死只查某一档） */
    List<TBcastUnitCard> selectTBcastUnitCardList(Wrapper<TBcastUnitCard> queryWrapper);

    /**
     * 保存方法（签名照旧不动）。规矩与 {@link #fileCard} 同一条：卡号系统发、还欠系统折、
     * 同台站不叠卡、关停台不收。不合规返回 0，不入库。
     * 界面递卡走的是带回执的 {@link #fileCard}——那是本方法的同义立卡走法，不是第二套规矩。
     */
    int insertTBcastUnitCard(TBcastUnitCard record);

    /** 修改保存（签名照旧）：压过卡的一律 0；卡号、步次、还欠、签认号不由这条改 */
    int updateTBcastUnitCard(TBcastUnitCard record);

    /** 批量删除（签名照旧）：只叫停得动在走的卡；压卡的撤不掉 */
    int deleteTBcastUnitCardByIds(String ids);

    /** 按主键删除（签名照旧）：语义同为叫停，压卡的撤不掉 */
    int deleteTBcastUnitCardById(Long id);

    /** 推进一态（待核→核讫发签认号；核讫→压卡封顶）。步子不得踏空、不得越级，返回 0 即没迈成 */
    int advanceTBcastUnitCard(Long id);

    /** 退回一态（只准核讫→待核）。上一趟记下的要件与签认号仍旧保留，不清空、不并排另存 */
    int revertTBcastUnitCard(Long id);

    /**
     * 递卡（立卡/改卡同义走法）：这是界面与接口唯一的递卡口子。
     * 不合规的卡进不了库，回执上点名是哪一栏不对；收下的，回执带回系统发出的卡号与折好的还欠。
     */
    BcastUnitCardReceipt fileCard(BcastUnitCardForm form);

    /** 就地叫停一张在走的卡（未到压卡才叫得停），叫停后同台站的名额空出来；返回是否叫停成 */
    boolean haltCard(Long id);

    /** 这一张眼下算哪一档，由系统按已完成的级数自己数；旧卡步数空着按待核起算 */
    String statusWord(TBcastUnitCard card);

    /**
     * 本层这一季在册的卡：按属地那一路（roadPrefix 前缀）圈定，县翻不出邻县、市混不进别市。
     * 本层一张没有就回空表，不拿旁的行凑；库里立过的每一张都不漏。
     */
    List<TBcastUnitCard> listByScope(int nodeNo, String roadPrefix);

    /**
     * 寻卡：卡号、台站代号、台站类别三样给全给半都行，整段咬得上、只咬中间几个字也算寻着。
     * 只在本级有权的那一层里找；命中的卡连同所属台站与压在哪一带回来。
     */
    List<BcastUnitCardHit> searchCards(String billNo, String siteNo, String siteType,
                                       int nodeNo, String roadPrefix);

    /**
     * 季表覆盖率：分母取属地里台站的总数（不是已建卡数），分子取本季已核讫的张数，
     * 县并市、市并省都由属地下台站一次点成，不分两回数。
     *
     * @param at 落季的界点时刻（按它所在的那个季度算）
     */
    BcastCoverage coverage(int nodeNo, String roadPrefix, Date at);
}
