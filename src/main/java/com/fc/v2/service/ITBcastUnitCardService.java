package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverageRow;
import com.fc.v2.model.custom.BcastUnitCardHit;

import java.util.List;

/**
 * 台站核点卡立卡单 Service接口
 *
 * <p>收口规矩（一张卡合不合规、眼下算哪一步、能不能再迈一步或退一步、还欠点位数
 * 折出几、按属地那一路该露哪几张，一律由本接口这几条方法独家定调）：</p>
 * <ul>
 *   <li>卡号与随卡签认号两边一律由系统发出，人自家拟的递进来不入库，也不给手填留位置。</li>
 *   <li>还欠点位数不由人报也不由人填：应核减已核，刚立卡、已核点位数动了、换了属地挂靠的
 *       那一层，三个时点都重算落回卡上，栏位只读不写。</li>
 *   <li>已核点位数为负、大过应核点位数的，卡进不了库，回执上点名是哪一栏不对；应核点位数同理。</li>
 *   <li>一年之内一座台站只认一张当季的卡：上一张还没走到压卡，新的这张起不来；走到压卡，
 *       名额才算空出来。</li>
 *   <li>整张只认待核、核讫、压卡三态，一步一档不得乱踏；眼下算哪一档由系统按已完成的级数
 *       自己数，屏上手填的档不作数。核讫可退回待核重来，上一趟记下的签认号仍旧保留；走到
 *       压卡这卡便封了顶——添不进去、字也动不得、整张撤不掉，要改只能够另开一张。</li>
 *   <li>早年从纸质簿搬进来的旧卡步数、核验串都空着：按待核容下，不抛错，也不当成已压卡。</li>
 *   <li>已经关停的台站不再收新卡；早前挂在它底下的那几张仍旧查得到、仍旧算在册。</li>
 * </ul>
 *
 * <p>递卡的来路有两条，两条都走 {@link #insertTBcastUnitCard} 这同一个入口。这条保存方法的
 * 签名照旧不动；若要另种立卡走法，另起一个同义方法去接，不动这一条。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITBcastUnitCardService {

    /** 按主键查询 */
    TBcastUnitCard selectTBcastUnitCardById(Long id);

    /**
     * 按条件查询列表（分页由调用方统一处理）。只列库里确有的卡：本层没有就空着，
     * 已经在场的那几张（待核、核讫、压卡）都得在这一屏上找得着，不许给抹掉。
     */
    List<TBcastUnitCard> selectTBcastUnitCardList(Wrapper<TBcastUnitCard> queryWrapper);

    /**
     * 立卡（两条来路共用的唯一入口，签名照旧不动）。
     *
     * <p>卡号、进展档位、随卡签认号、还欠点位数一律由系统落笔；递进来的这几样不作数。
     * 应核/已核点位数填得不对（为负、已核大过应核）的，卡进不了库，抛
     * {@link com.fc.v2.common.exception.BcastCardRuleException} 点名是哪一栏——这一句
     * 经全局异常处理落到回执上。</p>
     *
     * @return 1 立成；0 立不起来（底册查无此台、台站已关停、或上一张卡还没走到压卡）
     */
    int insertTBcastUnitCard(TBcastUnitCard record);

    /**
     * 改卡。压了卡的封死动不得（返回 0）；在走的卡只许动应核/已核/属地层级序/备注，
     * 还欠随之一并重算落回卡上。已核填得不对同样点名回执。
     */
    int updateTBcastUnitCard(TBcastUnitCard record);

    /** 批量撤卡：压了卡的整张撤不掉（跳过），在走的可撤；返回实际撤掉的张数 */
    int deleteTBcastUnitCardByIds(String ids);

    /** 推进一态（待核→核讫→压卡）：到核讫系统签发随卡签认号；压了卡的再推不认（返回 0） */
    int advanceTBcastUnitCard(Long id);

    /** 退回一态（核讫→待核）：上一趟记下的签认号仍旧保留；待核、压卡都退不动（返回 0） */
    int revertTBcastUnitCard(Long id);

    /** 按主键撤卡：压了卡的整张撤不掉（返回 0），在走的（含旧卡）可就地叫停 */
    int deleteTBcastUnitCardById(Long id);

    /**
     * 寻卡：卡号、台站代号、台站类别三样给全给半都行，整段咬得上算寻着，只咬上中间
     * 几个字也算寻着。该露哪几张只看本级有权的那一层（scopeRoad：本级属地那一路，
     * 空白按不限）——县里翻不出邻县的卡，市域那份名单里不混别市的行；本层一张没有，
     * 就空着，不拿旁的行来凑。寻到的那一张，所属台站与它压在哪一层一并带回来。
     */
    List<BcastUnitCardHit> searchUnitCards(String scopeRoad, String billNo, String siteNo, String siteType);

    /**
     * 季表覆盖率：属地里台站的总数做分母（不是取已经建了卡的数）、已经核讫的张数
     * （核讫、压卡都算）做分子。底册与在册卡同一次点上来，县并市、市并省一层层往上出，
     * 上一层的数与下一层各屏点出来的是同一次算的。
     */
    List<BcastCoverageRow> coverageStat();
}
