package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.BcastCardRuleException;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.mapper.auto.TBcastUnitCardMapper;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverageRow;
import com.fc.v2.model.custom.BcastUnitCardHit;
import com.fc.v2.service.ITBcastUnitCardService;
import com.fc.v2.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 台站核点卡 Service业务层处理。
 *
 * <p>一张卡合不合规、眼下算哪一步、能不能再迈一步或退一步、还欠点位数折出几、
 * 按属地那一路该露哪几张，一律由本类这几条方法独家定调；接口与界面不再开第二道
 * 绕得过去的口子。递卡的来路有两条，两条都走 {@link #insertTBcastUnitCard} 这同一个
 * 入口，屏上因此不会有第二种答案。</p>
 *
 * <p>早年间从纸质簿搬进来的旧卡，步数（status）、核验串（content）都空着：本类按
 * 待核（0）容下它们——碰一下就抛错不行，仍在走的旧卡被当成已压卡的也不行。</p>
 *
 * <p>不继承 ServiceImpl：IService 那一套 save/updateById/removeById 是绕得过去的
 * 第二道口子，收口规矩不许它开张。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TBcastUnitCardServiceImpl implements ITBcastUnitCardService {

    private static final Logger log = LoggerFactory.getLogger(TBcastUnitCardServiceImpl.class);

    /** 立卡进展：待核（旧卡空步数也按这一档容下） */
    private static final int STATUS_PENDING = 0;
    /** 立卡进展：核讫 */
    private static final int STATUS_VERIFIED = 1;
    /** 立卡进展：压卡（封顶：添不进去、字也动不得、整张撤不掉） */
    private static final int STATUS_SEALED = 2;

    /** 底册情形：已关停（不再收新卡，旧卡照查照算） */
    private static final int SITE_CLOSED = 1;

    /** 属地那一路的分段记号（省—市—县） */
    private static final String ROAD_SEP = "—";
    /** 属地那一路最多落到县一层：省、市、县三段 */
    private static final int MAX_ROAD_DEPTH = 3;

    @javax.annotation.Resource
    private TBcastUnitCardMapper bcastUnitCardMapper;

    @javax.annotation.Resource
    private TBcastSiteMapper bcastSiteMapper;

    @Override
    public TBcastUnitCard selectTBcastUnitCardById(Long id) {
        return this.bcastUnitCardMapper.selectOne(new QueryWrapper<TBcastUnitCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    /**
     * 列表只列库里确有的卡：递进来的条件照用，只补一条删除标记；
     * 不在服务层里另设状态过滤或私分页——本层没有就空着，已经在场的那几张不许给抹掉。
     */
    @Override
    public List<TBcastUnitCard> selectTBcastUnitCardList(Wrapper<TBcastUnitCard> queryWrapper) {
        QueryWrapper<TBcastUnitCard> wrapper;
        if (queryWrapper instanceof QueryWrapper) {
            wrapper = (QueryWrapper<TBcastUnitCard>) queryWrapper;
        } else {
            wrapper = new QueryWrapper<TBcastUnitCard>();
        }
        wrapper.eq("del_flag", 0);
        return this.bcastUnitCardMapper.selectList(wrapper);
    }

    /**
     * 立卡（两条来路共用的唯一入口）。卡号、随卡签认号、进展档位、还欠点位数
     * 一律由系统落笔：人自家拟的递进来不入库，也不给手填留位置。
     */
    @Override
    public int insertTBcastUnitCard(TBcastUnitCard record) {
        if (record == null) {
            return 0;
        }
        // 卡往上挂台站底册：底册里查无此台，挂不上去
        TBcastSite refArch = bcastSiteMapper.selectOne(new QueryWrapper<TBcastSite>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        // 已经关停的台站不再收新卡；早前挂在它底下的那几张仍旧查得到，不在这里拦
        if (refArch.getStatus() != null && refArch.getStatus().intValue() == SITE_CLOSED) {
            return 0;
        }

        // 应核/已核两栏先过规矩：不对的卡进不了库，回执上点名是哪一栏
        Integer should = record.getShouldCount();
        if (should == null) {
            throw new BcastCardRuleException("应核点位数填得不对：不能为空");
        }
        if (should.intValue() < 0) {
            throw new BcastCardRuleException("应核点位数填得不对：不能为负");
        }
        Integer done = record.getDoneCount() == null ? Integer.valueOf(0) : record.getDoneCount();
        checkDoneAgainstShould(done, should);

        // 一年之内一座台站只认一张当季的卡：上一张还没走到压卡（含仍在走的旧卡），
        // 新的这张起不来；两家县局同一天各报一遍、或同一台站换人重报，都一样立不起来
        Integer inflight = this.bcastUnitCardMapper.selectCount(new QueryWrapper<TBcastUnitCard>()
                .eq("site_id", record.getSiteId())
                .eq("del_flag", 0)
                .and(w -> w.isNull("status").or().ne("status", STATUS_SEALED)));
        if (inflight != null && inflight.intValue() > 0) {
            log.info("台站{}上一张卡还没走到头，新卡立不起来", record.getSiteId());
            return 0;
        }

        record.setSiteNo(refArch.getSiteNo());
        // 卡号由系统发出，人自家拟的递进来不入库
        record.setBillNo(genBillNo());
        // 随卡签认号由系统走到核讫那一步才签发，立卡时不给手填留位置
        record.setContent(null);
        // 眼下算哪一档由系统按已完成的级数自己数：刚立卡一律待核
        record.setStatus(Integer.valueOf(STATUS_PENDING));
        // 还欠这一栏不由人报也不由人填：应核减已核，落卡上那一栏
        record.setDoneCount(done);
        record.setLackCount(Integer.valueOf(should.intValue() - done.intValue()));
        record.setDelFlag(Integer.valueOf(0));
        return this.bcastUnitCardMapper.insert(record);
    }

    /**
     * 改卡。压了卡的整张封死，字也动不得；在走的卡只许动应核/已核/属地层级序/备注，
     * 卡号、进展、签认号、还欠几栏人插不进手。已核点位数动了、换了属地挂靠的那一层，
     * 还欠都重算一遍落回卡上。
     */
    @Override
    public int updateTBcastUnitCard(TBcastUnitCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TBcastUnitCard cur = this.bcastUnitCardMapper.selectOne(new QueryWrapper<TBcastUnitCard>()
                .eq("id", record.getId()).eq("del_flag", 0));
        if (cur == null) {
            return 0;
        }
        // 走到压卡这卡便封了顶：之后字也动不得，要改只能够另开一张
        if (cur.getStatus() != null && cur.getStatus().intValue() == STATUS_SEALED) {
            return 0;
        }

        // 以库里落定的为底，递进来的哪一栏要动就并哪一栏，并完再过一遍规矩
        Integer should = record.getShouldCount() != null ? record.getShouldCount() : cur.getShouldCount();
        Integer done = record.getDoneCount() != null ? record.getDoneCount() : cur.getDoneCount();
        if (should != null && should.intValue() < 0) {
            throw new BcastCardRuleException("应核点位数填得不对：不能为负");
        }
        if (done != null) {
            checkDoneAgainstShould(done, should);
        }

        UpdateWrapper<TBcastUnitCard> uw = new UpdateWrapper<TBcastUnitCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0);
        if (record.getShouldCount() != null) {
            uw.set("should_count", record.getShouldCount());
        }
        if (record.getDoneCount() != null) {
            uw.set("done_count", record.getDoneCount());
        }
        if (record.getNodeNo() != null) {
            uw.set("node_no", record.getNodeNo());
        }
        if (record.getRemark() != null) {
            uw.set("remark", record.getRemark());
        }
        // 还欠重算的三个时点之二、之三都在这条路上：已核动了、换了属地挂靠的那一层。
        // 两数凑得齐就重算落回卡上，栏位只读不写，人递进来的还欠不作数
        if (should != null && done != null) {
            uw.set("lack_count", should.intValue() - done.intValue());
        }
        uw.set("update_time", new Date());
        return this.bcastUnitCardMapper.update(null, uw);
    }

    /**
     * 推进一态：待核→核讫→压卡，一步一档，不得乱踏。旧卡空步数按待核容下。
     * 推进到核讫，系统签发随卡签认号；推进到压卡，这卡便封了顶。
     */
    @Override
    public int advanceTBcastUnitCard(Long id) {
        if (id == null) {
            return 0;
        }
        TBcastUnitCard cur = this.bcastUnitCardMapper.selectOne(new QueryWrapper<TBcastUnitCard>()
                .eq("id", id).eq("del_flag", 0));
        if (cur == null) {
            return 0;
        }
        // 旧卡步数空着：按待核容下，碰一下就抛错不行
        int st = cur.getStatus() == null ? STATUS_PENDING : cur.getStatus().intValue();
        // 压卡封顶，后头的路一概不认
        if (st >= STATUS_SEALED) {
            return 0;
        }
        int next = st + 1;
        UpdateWrapper<TBcastUnitCard> uw = new UpdateWrapper<TBcastUnitCard>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", next);
        if (next == STATUS_VERIFIED) {
            // 随卡签认号由系统发出：走到核讫这一步才签发，人自家拟的不入库
            uw.set("content", genSignNo(cur.getBillNo()));
        }
        uw.set("update_time", new Date());
        return this.bcastUnitCardMapper.update(null, uw);
    }

    /**
     * 退回一态：只有核讫能退回待核重来；待核没得退，压了卡的封死退不动。
     * 退回来上一趟记下的签认号仍旧保留，不跟后补的那笔并排摆——重走到核讫，
     * 系统另签一笔盖在原位上。
     */
    @Override
    public int revertTBcastUnitCard(Long id) {
        if (id == null) {
            return 0;
        }
        TBcastUnitCard cur = this.bcastUnitCardMapper.selectOne(new QueryWrapper<TBcastUnitCard>()
                .eq("id", id).eq("del_flag", 0));
        if (cur == null) {
            return 0;
        }
        int st = cur.getStatus() == null ? STATUS_PENDING : cur.getStatus().intValue();
        if (st != STATUS_VERIFIED) {
            return 0;
        }
        UpdateWrapper<TBcastUnitCard> uw = new UpdateWrapper<TBcastUnitCard>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("status", STATUS_PENDING);
        // 上一趟记下的东西仍旧保留：签认号不清
        uw.set("update_time", new Date());
        return this.bcastUnitCardMapper.update(null, uw);
    }

    @Override
    public int deleteTBcastUnitCardByIds(String ids) {
        if (StringUtils.isEmpty(ids)) {
            return 0;
        }
        Long[] idArr = ConvertUtil.toLongArray(ids);
        int rows = 0;
        for (Long id : idArr) {
            if (id != null) {
                rows += deleteTBcastUnitCardById(id);
            }
        }
        return rows;
    }

    /**
     * 按主键撤卡：压了卡的整张撤不掉，要改只能够另开一张；
     * 在走的卡（含旧卡）可就地叫停，名额随即空出来。
     */
    @Override
    public int deleteTBcastUnitCardById(Long id) {
        if (id == null) {
            return 0;
        }
        TBcastUnitCard cur = this.bcastUnitCardMapper.selectOne(new QueryWrapper<TBcastUnitCard>()
                .eq("id", id).eq("del_flag", 0));
        if (cur == null) {
            return 0;
        }
        if (cur.getStatus() != null && cur.getStatus().intValue() == STATUS_SEALED) {
            return 0;
        }
        return this.bcastUnitCardMapper.deleteById(id);
    }

    /**
     * 寻卡：卡号、台站代号、台站类别三样给全给半都行，整段咬得上算寻着，
     * 只咬上中间几个字也算寻着。该露哪几张只看本级有权的那一层——scopeRoad
     * 一压，邻县邻市的行一概翻不出来；本层一张没有，就空着，不拿旁的行来凑。
     */
    @Override
    public List<BcastUnitCardHit> searchUnitCards(String scopeRoad, String billNo, String siteNo, String siteType) {
        List<BcastUnitCardHit> hits = new ArrayList<BcastUnitCardHit>();
        String scope = trim(scopeRoad);
        String bill = trim(billNo);
        String sNo = trim(siteNo);
        String sType = trim(siteType);
        boolean siteConstrained = scope != null || sNo != null || sType != null;

        // 先把本级有权的那一层底册点出来：台站代号、台站类别也在这上头咬字
        QueryWrapper<TBcastSite> sw = new QueryWrapper<TBcastSite>().eq("del_flag", 0);
        if (scope != null) {
            sw.likeRight("road_name", scope);
        }
        if (sNo != null) {
            sw.like("site_no", sNo);
        }
        if (sType != null) {
            sw.like("site_type", sType);
        }
        List<TBcastSite> sites = bcastSiteMapper.selectList(sw);
        Map<Long, TBcastSite> siteById = new HashMap<Long, TBcastSite>();
        List<Object> matchedSiteIds = new ArrayList<Object>();
        for (TBcastSite s : sites) {
            siteById.put(s.getId(), s);
            matchedSiteIds.add(s.getId());
        }
        // 压着属地/代号/类别来的，本层一座台站都对不上，那一屏就空着
        if (siteConstrained && matchedSiteIds.isEmpty()) {
            return hits;
        }

        QueryWrapper<TBcastUnitCard> cw = new QueryWrapper<TBcastUnitCard>().eq("del_flag", 0);
        if (bill != null) {
            cw.like("bill_no", bill);
        }
        if (siteConstrained) {
            cw.in("site_id", matchedSiteIds);
        }
        cw.orderByAsc("id");
        List<TBcastUnitCard> cards = this.bcastUnitCardMapper.selectList(cw);
        for (TBcastUnitCard c : cards) {
            // 寻到的那一张，所属台站与它压在哪一层一并带回来，不必再让人抄第二回
            TBcastSite s = c.getSiteId() == null ? null : siteById.get(Long.valueOf(c.getSiteId().longValue()));
            hits.add(new BcastUnitCardHit(c,
                    s == null ? null : s.getSiteName(),
                    s == null ? null : s.getSiteType(),
                    s == null ? null : s.getRoadName()));
        }
        return hits;
    }

    /**
     * 季表覆盖率：属地里台站的总数做分母、已经核讫的张数做分子。
     * 底册与在册卡同一次点上来，县并市、市并省一层层往上出——上一层的数与
     * 下一层各屏点出来的是同一次算的，不分两回数。
     */
    @Override
    public List<BcastCoverageRow> coverageStat() {
        List<TBcastSite> sites = bcastSiteMapper.selectList(new QueryWrapper<TBcastSite>().eq("del_flag", 0));
        List<TBcastUnitCard> cards = this.bcastUnitCardMapper.selectList(new QueryWrapper<TBcastUnitCard>().eq("del_flag", 0));

        Map<Long, TBcastSite> siteById = new HashMap<Long, TBcastSite>();
        // 每一路（省/省—市/省—市—县）一笔账：[台站总数, 已经核讫的张数]
        Map<String, int[]> agg = new LinkedHashMap<String, int[]>();
        Map<String, Integer> levelOf = new LinkedHashMap<String, Integer>();

        // 分母先落：属地里台站的总数，在册的、已关停的都算在册
        for (TBcastSite s : sites) {
            String leaf = leafRoad(s.getRoadName());
            if (leaf == null) {
                continue;
            }
            registerChain(leaf, agg, levelOf);
            siteById.put(s.getId(), s);
            agg.get(leaf)[0]++;
        }
        // 分子后落：已经核讫的张数（核讫、压卡都算已经核讫；仍在走的旧卡不算），
        // 挂到哪一座台站就算进哪一路，关停台站底下的旧卡仍旧算在册
        for (TBcastUnitCard c : cards) {
            Integer st = c.getStatus();
            if (st == null || st.intValue() < STATUS_VERIFIED) {
                continue;
            }
            if (c.getSiteId() == null) {
                continue;
            }
            TBcastSite s = siteById.get(Long.valueOf(c.getSiteId().longValue()));
            if (s == null) {
                continue;
            }
            String leaf = leafRoad(s.getRoadName());
            if (leaf == null) {
                continue;
            }
            agg.get(leaf)[1]++;
        }

        // 县里的数并到市域、市域并到省域：每一路的账往它上头各层一并
        Map<String, int[]> rolled = new LinkedHashMap<String, int[]>();
        Map<String, Integer> rolledLevel = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            String[] segs = e.getKey().split(ROAD_SEP);
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < segs.length; i++) {
                if (i > 0) {
                    p.append(ROAD_SEP);
                }
                p.append(segs[i]);
                String key = p.toString();
                int[] a = rolled.get(key);
                if (a == null) {
                    a = new int[2];
                    rolled.put(key, a);
                    rolledLevel.put(key, Integer.valueOf(Math.min(i, BcastCoverageRow.LEVEL_COUNTY)));
                }
                a[0] += e.getValue()[0];
                a[1] += e.getValue()[1];
            }
        }

        List<BcastCoverageRow> rows = new ArrayList<BcastCoverageRow>();
        for (Map.Entry<String, int[]> e : rolled.entrySet()) {
            rows.add(new BcastCoverageRow(e.getKey(),
                    rolledLevel.get(e.getKey()).intValue(), e.getValue()[0], e.getValue()[1]));
        }
        return rows;
    }

    /** 已核点位数的规矩：不能为负、不能大过应核点位数；犯了哪条，回执上点名已核点位数 */
    private void checkDoneAgainstShould(Integer done, Integer should) {
        if (done.intValue() < 0) {
            throw new BcastCardRuleException("已核点位数填得不对：不能为负");
        }
        if (should != null && done.intValue() > should.intValue()) {
            throw new BcastCardRuleException("已核点位数填得不对：大过应核点位数");
        }
    }

    /** 卡号由系统发出：KD+立卡时刻+一段随机码，人自家拟的递进来不入库 */
    private String genBillNo() {
        return "KD" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    /** 随卡签认号由系统发出：走到核讫那一步签发到卡上 */
    private String genSignNo(String billNo) {
        return "QR-" + (billNo == null ? "" : billNo) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /** 空白按没给算：寻卡三样给全给半都行 */
    private String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 属地那一路的叶子：省—市—县最多三段，再深的往县一层并 */
    private String leafRoad(String roadName) {
        String road = trim(roadName);
        if (road == null) {
            return null;
        }
        String[] segs = road.split(ROAD_SEP);
        StringBuilder leaf = new StringBuilder();
        int n = Math.min(segs.length, MAX_ROAD_DEPTH);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                leaf.append(ROAD_SEP);
            }
            leaf.append(segs[i].trim());
        }
        return leaf.toString();
    }

    /** 把这一路上头各层（省、省—市、省—市—县）都在账本上登记出来 */
    private void registerChain(String leaf, Map<String, int[]> agg, Map<String, Integer> levelOf) {
        String[] segs = leaf.split(ROAD_SEP);
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) {
                p.append(ROAD_SEP);
            }
            p.append(segs[i]);
            String key = p.toString();
            if (!agg.containsKey(key)) {
                agg.put(key, new int[2]);
                levelOf.put(key, Integer.valueOf(Math.min(i, BcastCoverageRow.LEVEL_COUNTY)));
            }
        }
    }
}
