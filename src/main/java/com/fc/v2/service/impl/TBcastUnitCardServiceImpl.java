package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.mapper.auto.TBcastUnitCardMapper;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverage;
import com.fc.v2.model.custom.BcastUnitCardForm;
import com.fc.v2.model.custom.BcastUnitCardHit;
import com.fc.v2.model.custom.BcastUnitCardReceipt;
import com.fc.v2.service.ITBcastUnitCardService;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;

/**
 * 台站核点卡 Service业务层处理。
 *
 * <p>合不合规、算哪一步、能不能进退、还欠折几、按属地露哪几张，只在本层定调。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TBcastUnitCardServiceImpl implements ITBcastUnitCardService {

    /** 非负整数（不许带小数、不许为负） */
    private static final Pattern INTEGER = Pattern.compile("^\\d+$");

    /** 卡号前缀（卡号由系统发） */
    private static final String BILL_PREFIX = "HK";
    /** 签认号前缀（推进到核讫由系统发） */
    private static final String SIGN_PREFIX = "QR";

    @javax.annotation.Resource
    private TBcastUnitCardMapper bcastUnitCardMapper;

    @javax.annotation.Resource
    private TBcastSiteMapper bcastSiteMapper;

    @Override
    public TBcastUnitCard selectTBcastUnitCardById(Long id) {
        if (id == null) {
            return null;
        }
        return this.bcastUnitCardMapper.selectOne(new QueryWrapper<TBcastUnitCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TBcastSite> listLiveSites() {
        // 已关停（status=1）的不再收新卡，立卡页上不摆它
        return this.bcastSiteMapper.selectList(new QueryWrapper<TBcastSite>()
                .eq("del_flag", 0)
                .and(w -> w.ne("status", 1).or().isNull("status"))
                .orderByAsc("site_no"));
    }

    @Override
    public List<TBcastUnitCard> selectTBcastUnitCardList(Wrapper<TBcastUnitCard> queryWrapper) {
        // 条件是什么就查什么，不写死只查某一档；在册口径由这一条 del_flag 收住
        QueryWrapper<TBcastUnitCard> wrapper;
        if (queryWrapper instanceof QueryWrapper) {
            wrapper = (QueryWrapper<TBcastUnitCard>) queryWrapper;
        } else {
            wrapper = new QueryWrapper<TBcastUnitCard>();
        }
        wrapper.eq("del_flag", 0);
        wrapper.orderByAsc("node_no", "bill_no");
        return this.bcastUnitCardMapper.selectList(wrapper);
    }

    // ============================ 旧签名（照旧不动） ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTBcastUnitCard(TBcastUnitCard record) {
        if (record == null) {
            return 0;
        }
        // 人自家拟的卡号、步次、还欠、签认号递进来一律不作数
        record.setBillNo(null);
        record.setStatus(null);
        record.setLackCount(null);
        record.setSignNo(null);
        BcastUnitCardForm form = new BcastUnitCardForm();
        form.setSiteId(record.getSiteId() == null ? null : record.getSiteId().longValue());
        form.setNodeNo(record.getNodeNo() == null ? null : String.valueOf(record.getNodeNo()));
        form.setShouldCount(record.getShouldCount() == null ? null : String.valueOf(record.getShouldCount()));
        form.setDoneCount(record.getDoneCount() == null ? null : String.valueOf(record.getDoneCount()));
        form.setContent(record.getContent());
        return fileCard(form).isAccepted() ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTBcastUnitCard(TBcastUnitCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TBcastUnitCard db = this.bcastUnitCardMapper.selectById(record.getId());
        if (db == null || isDeleted(db)) {
            return 0;
        }
        // 压过卡的：添不进、字动不得、整张撤不掉
        if (stageOf(db) == STAGE_SEALED) {
            return 0;
        }
        BcastUnitCardForm form = new BcastUnitCardForm();
        form.setId(db.getId());
        form.setSiteId(db.getSiteId() == null ? null : db.getSiteId().longValue());
        form.setNodeNo(record.getNodeNo() != null ? String.valueOf(record.getNodeNo())
                : (db.getNodeNo() == null ? null : String.valueOf(db.getNodeNo())));
        form.setShouldCount(record.getShouldCount() != null ? String.valueOf(record.getShouldCount())
                : (db.getShouldCount() == null ? null : String.valueOf(db.getShouldCount())));
        form.setDoneCount(record.getDoneCount() != null ? String.valueOf(record.getDoneCount())
                : (db.getDoneCount() == null ? null : String.valueOf(db.getDoneCount())));
        // 要件：没递新的来就照旧
        form.setContent(record.getContent() != null ? record.getContent() : db.getContent());
        return fileCard(form).isAccepted() ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTBcastUnitCardByIds(String ids) {
        if (StringUtils.isEmpty(ids)) {
            return 0;
        }
        int n = 0;
        for (String part : ids.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                if (haltCard(Long.valueOf(s))) {
                    n++;
                }
            } catch (NumberFormatException ignore) {
                // 号都不成号，跳过它，不牵连同批别的卡
            }
        }
        return n;
    }

    @Override
    public int deleteTBcastUnitCardById(Long id) {
        return haltCard(id) ? 1 : 0;
    }

    // ============================ 步子：三态逐级 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int advanceTBcastUnitCard(Long id) {
        if (id == null) {
            return 0;
        }
        TBcastUnitCard cur = this.bcastUnitCardMapper.selectById(id);
        if (cur == null || isDeleted(cur)) {
            return 0;
        }
        int st = stageOf(cur);
        // 步子不得乱踏：只准 +1；压卡封了顶，再迈不动
        if (st >= STAGE_SEALED) {
            return 0;
        }
        int next = st + 1;
        UpdateWrapper<TBcastUnitCard> uw = new UpdateWrapper<TBcastUnitCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("status", next)
                .set("update_time", new Date());
        // 待核→核讫：系统发签认号；已经发过的（退回重来）沿用原号，不另发
        if (next == STAGE_VERIFIED && StringUtils.isEmpty(cur.getSignNo())) {
            String signNo = newSignNo();
            uw.set("sign_no", signNo);
        }
        return this.bcastUnitCardMapper.update(null, uw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revertTBcastUnitCard(Long id) {
        if (id == null) {
            return 0;
        }
        TBcastUnitCard cur = this.bcastUnitCardMapper.selectById(id);
        if (cur == null || isDeleted(cur)) {
            return 0;
        }
        int st = stageOf(cur);
        // 只准核讫退回待核；待核无处可退，压卡封了顶退不动
        if (st != STAGE_VERIFIED) {
            return 0;
        }
        // 上一趟记下的要件、签认号仍旧保留：不清空、不另起一笔并排摆
        return this.bcastUnitCardMapper.update(null, new UpdateWrapper<TBcastUnitCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                .set("status", STAGE_PENDING)
                .set("update_time", new Date()));
    }

    // ============================ 递卡：唯一口子 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BcastUnitCardReceipt fileCard(BcastUnitCardForm form) {
        BcastUnitCardReceipt receipt = new BcastUnitCardReceipt();
        if (form == null) {
            receipt.getErrors().add("递卡为空");
            return receipt;
        }

        // 改卡先把旧卡请出来；压过卡的动不得。台站以库里挂着的为准，改卡不必再带一遍
        TBcastUnitCard db = null;
        if (form.getId() != null) {
            db = this.bcastUnitCardMapper.selectById(form.getId());
            if (db == null || isDeleted(db)) {
                receipt.getErrors().add("这张卡不在册，改无可改");
            } else if (stageOf(db) == STAGE_SEALED) {
                receipt.getErrors().add("已压卡封顶：添不进、字动不得、整张撤不掉，要改只能另开一张");
            }
        }
        Long siteId = form.getSiteId();
        if (siteId == null && db != null && db.getSiteId() != null) {
            siteId = db.getSiteId().longValue();
        }

        // 台站：对得上底册在册（未关停）才挂得上
        TBcastSite site = null;
        if (siteId == null) {
            receipt.getErrors().add(BcastUnitCardReceipt.FIELD_SITE + "空缺，挂不上底册");
        } else {
            site = this.bcastSiteMapper.selectOne(new QueryWrapper<TBcastSite>()
                    .eq("id", siteId).eq("del_flag", 0));
            if (site == null) {
                receipt.getErrors().add(BcastUnitCardReceipt.FIELD_SITE + "对不上发射台站底册");
            } else if (site.getStatus() != null && site.getStatus().intValue() == 1) {
                receipt.getErrors().add(BcastUnitCardReceipt.FIELD_SITE + "已关停，不再收新卡");
            }
        }

        // 属地层级序：0省 1市 2县
        Integer nodeNo = parseNode(form.getNodeNo(), receipt);

        // 两个点位数：非负整数，且已核不得大过应核
        Integer should = parseCount(form.getShouldCount(), BcastUnitCardReceipt.FIELD_SHOULD, receipt);
        Integer done = parseCount(form.getDoneCount(), BcastUnitCardReceipt.FIELD_DONE, receipt);
        if (should != null && done != null && done.intValue() > should.intValue()) {
            receipt.getErrors().add(BcastUnitCardReceipt.FIELD_DONE + "大过" + BcastUnitCardReceipt.FIELD_SHOULD);
        }

        // 同一座台站同时只容一张在走的卡（压卡/叫停之后名额才空出来）
        if (site != null && receipt.getErrors().isEmpty()) {
            QueryWrapper<TBcastUnitCard> qw = new QueryWrapper<TBcastUnitCard>()
                    .eq("site_id", site.getId())
                    .eq("del_flag", 0)
                    // 未压卡的都占名额；旧卡步数空着（null）也是在走的卡，照样占
                    .and(w -> w.ne("status", STAGE_SEALED).or().isNull("status"));
            if (db != null) {
                qw.ne("id", db.getId());
            }
            Integer walking = this.bcastUnitCardMapper.selectCount(qw);
            if (walking != null && walking.intValue() > 0) {
                receipt.getErrors().add("同一座台站同时只容一张在走的卡：上一张未压卡、未叫停，新卡立不起来");
            }
        }

        if (!receipt.getErrors().isEmpty()) {
            // 进不了库：原样退回，回执点名
            return receipt;
        }

        // 还欠 = 应核 - 已核，三个时点（立卡、已核变动、换挂靠层）都在这一处折，落只读栏
        int lack = should.intValue() - done.intValue();

        TBcastUnitCard card;
        if (db == null) {
            card = new TBcastUnitCard();
            // 卡号由系统发，人自家拟的不入库，也没有手填的位置
            String billNo = newBillNo();
            card.setBillNo(billNo);
            card.setSiteId(site.getId().intValue());
            card.setSiteNo(site.getSiteNo());
            card.setNodeNo(nodeNo);
            card.setShouldCount(should);
            card.setDoneCount(done);
            card.setLackCount(Integer.valueOf(lack));
            card.setContent(form.getContent());
            card.setStatus(Integer.valueOf(STAGE_PENDING));
            card.setDelFlag(Integer.valueOf(0));
            card.setCreateBy(BILL_PREFIX);
            card.setCreateTime(new Date());
            this.bcastUnitCardMapper.insert(card);
            receipt.setBillNo(billNo);
        } else {
            card = db;
            // 卡号、签认号不由改卡这条动；换了挂靠层也只落层级，欠数照折
            UpdateWrapper<TBcastUnitCard> uw = new UpdateWrapper<TBcastUnitCard>()
                    .eq("id", db.getId())
                    .eq("del_flag", 0)
                    .set("node_no", nodeNo)
                    .set("should_count", should)
                    .set("done_count", done)
                    .set("lack_count", lack)
                    .set("content", form.getContent())
                    .set("update_time", new Date());
            // 点位数动过/换过挂靠层：核讫过的也退回待核重走（签认号仍旧保留，不另发）
            if (stageOf(db) == STAGE_VERIFIED) {
                uw.set("status", STAGE_PENDING);
                card.setStatus(Integer.valueOf(STAGE_PENDING));
            }
            this.bcastUnitCardMapper.update(null, uw);
            card.setNodeNo(nodeNo);
            card.setShouldCount(should);
            card.setDoneCount(done);
            card.setLackCount(Integer.valueOf(lack));
            card.setContent(form.getContent());
            receipt.setBillNo(card.getBillNo());
            receipt.setSignNo(card.getSignNo());
        }
        receipt.setAccepted(true);
        receipt.setLackCount(Integer.valueOf(lack));
        receipt.setCard(card);
        return receipt;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean haltCard(Long id) {
        if (id == null) {
            return false;
        }
        TBcastUnitCard cur = this.bcastUnitCardMapper.selectById(id);
        if (cur == null || isDeleted(cur)) {
            return false;
        }
        // 压了卡的撤不掉；只有在走的（待核/核讫）叫得停
        if (stageOf(cur) == STAGE_SEALED) {
            return false;
        }
        return this.bcastUnitCardMapper.update(null, new UpdateWrapper<TBcastUnitCard>()
                .eq("id", id)
                .eq("del_flag", 0)
                // 旧卡步数空着也叫得停：未压卡（含 null 档）才叫停
                .and(w -> w.ne("status", STAGE_SEALED).or().isNull("status"))
                .set("del_flag", 1)
                .set("update_time", new Date())) > 0;
    }

    // ============================ 档：系统数 ============================

    @Override
    public String statusWord(TBcastUnitCard card) {
        if (card == null) {
            return "";
        }
        switch (stageOf(card)) {
            case STAGE_VERIFIED:
                return "核讫";
            case STAGE_SEALED:
                return "压卡";
            case STAGE_PENDING:
            default:
                return "待核";
        }
    }

    // ============================ 按属地列卡 / 寻卡 ============================

    @Override
    public List<TBcastUnitCard> listByScope(int nodeNo, String roadPrefix) {
        if (StringUtils.isEmpty(roadPrefix)) {
            // 说不出本级属地那一路，一屏空着，不拿旁人的行凑
            return new ArrayList<TBcastUnitCard>();
        }
        List<Integer> siteIds = scopeSiteIds(roadPrefix, null, null);
        if (siteIds.isEmpty()) {
            // 本层这一季没立过卡、或属地底下没有台站：屏上空着，不拿旁的行凑
            return new ArrayList<TBcastUnitCard>();
        }
        return this.bcastUnitCardMapper.selectList(new QueryWrapper<TBcastUnitCard>()
                .eq("del_flag", 0)
                .in("site_id", siteIds)
                .orderByAsc("node_no", "bill_no"));
    }

    @Override
    public List<BcastUnitCardHit> searchCards(String billNo, String siteNo, String siteType,
                                              int nodeNo, String roadPrefix) {
        List<BcastUnitCardHit> hits = new ArrayList<BcastUnitCardHit>();
        if (StringUtils.isEmpty(roadPrefix)) {
            return hits;
        }
        // 先在本级属地那一路底下按代号、类别圈台站，再回头点卡：全程参数化，不拼半句原句
        List<Integer> siteIds = scopeSiteIds(roadPrefix, siteNo, siteType);
        if (siteIds.isEmpty()) {
            return hits;
        }
        QueryWrapper<TBcastUnitCard> qw = new QueryWrapper<TBcastUnitCard>()
                .eq("del_flag", 0)
                .in("site_id", siteIds)
                .orderByAsc("node_no", "bill_no");
        if (StringUtils.isNotEmpty(billNo)) {
            // 整段咬得上算寻着，只咬中间几个字也算寻着
            qw.like("bill_no", billNo.trim());
        }
        List<TBcastUnitCard> cards = this.bcastUnitCardMapper.selectList(qw);
        if (cards.isEmpty()) {
            return hits;
        }
        List<Object> ids = new ArrayList<Object>();
        for (TBcastUnitCard c : cards) {
            if (c.getSiteId() != null) {
                ids.add(c.getSiteId());
            }
        }
        Map<Integer, TBcastSite> siteMap = new HashMap<Integer, TBcastSite>();
        if (!ids.isEmpty()) {
            List<TBcastSite> sites = this.bcastSiteMapper.selectList(new QueryWrapper<TBcastSite>()
                    .in("id", ids));
            for (TBcastSite s : sites) {
                siteMap.put(s.getId().intValue(), s);
            }
        }
        for (TBcastUnitCard c : cards) {
            TBcastSite s = c.getSiteId() == null ? null : siteMap.get(c.getSiteId());
            hits.add(new BcastUnitCardHit(
                    c,
                    s == null ? c.getSiteNo() : s.getSiteNo(),
                    s == null ? null : s.getSiteName(),
                    s == null ? null : s.getSiteType(),
                    s == null ? null : s.getRoadName(),
                    statusWord(c)));
        }
        return hits;
    }

    // ============================ 季表覆盖率 ============================

    @Override
    public BcastCoverage coverage(int nodeNo, String roadPrefix, Date at) {
        BcastCoverage cov = new BcastCoverage();
        cov.setNodeNo(Integer.valueOf(nodeNo));
        cov.setRoadPrefix(roadPrefix);
        Date point = at == null ? new Date() : at;
        Date[] q = quarterRange(point);

        // 分母：属地里台站的总数（底册里的都算，不是已建卡数）
        List<TBcastSite> scopeSites = this.bcastSiteMapper.selectList(new QueryWrapper<TBcastSite>()
                .eq("del_flag", 0)
                .likeRight("road_name", roadPrefix == null ? "" : roadPrefix));
        cov.setSiteTotal(scopeSites.size());
        if (scopeSites.isEmpty()) {
            cov.setVerifiedCards(0);
            cov.calc();
            return cov;
        }
        List<Integer> scopeIds = new ArrayList<Integer>();
        for (TBcastSite s : scopeSites) {
            if (s.getId() != null) {
                scopeIds.add(s.getId().intValue());
            }
        }

        // 分子：本季立的卡里已经走到核讫、压卡两档的张数；与分母同一次圈定的台站集合，不分两回数
        Integer verified = this.bcastUnitCardMapper.selectCount(new QueryWrapper<TBcastUnitCard>()
                .eq("del_flag", 0)
                .in("status", new Integer[] {STAGE_VERIFIED, STAGE_SEALED})
                .in("site_id", scopeIds)
                .ge("create_time", q[0])
                .lt("create_time", q[1]));
        cov.setVerifiedCards(verified == null ? 0 : verified.intValue());
        cov.calc();
        return cov;
    }

    // ============================ 内里的规矩 ============================

    /** 旧卡步数空着不抛错，按待核起算；绝不当成已压卡 */
    private int stageOf(TBcastUnitCard card) {
        Integer st = card.getStatus();
        if (st == null) {
            return STAGE_PENDING;
        }
        int v = st.intValue();
        return v < STAGE_PENDING || v > STAGE_SEALED ? STAGE_PENDING : v;
    }

    private boolean isDeleted(TBcastUnitCard card) {
        return card.getDelFlag() != null && card.getDelFlag().intValue() == 1;
    }

    private Integer parseNode(String raw, BcastUnitCardReceipt receipt) {
        String s = raw == null ? "" : raw.trim();
        if (!INTEGER.matcher(s).matches()) {
            receipt.getErrors().add(BcastUnitCardReceipt.FIELD_NODE + "须填 0省/1市/2县");
            return null;
        }
        int v = Integer.parseInt(s);
        if (v < 0 || v > 2) {
            receipt.getErrors().add(BcastUnitCardReceipt.FIELD_NODE + "只认 0省/1市/2县");
            return null;
        }
        return Integer.valueOf(v);
    }

    /** 点位数逐字验：空缺、为负、带小数都在回执上点这一栏的名 */
    private Integer parseCount(String raw, String field, BcastUnitCardReceipt receipt) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            receipt.getErrors().add(field + "空缺");
            return null;
        }
        if (!INTEGER.matcher(s).matches()) {
            receipt.getErrors().add(field + "须为非负整数，不许为负、不许带小数：" + s);
            return null;
        }
        return Integer.valueOf(Integer.parseInt(s));
    }

    /**
     * 本级属地范围里的台站 id：底册 road_name 以前缀起头的；寻卡时可再压代号、类别
     * （给全给半、咬中间也算）。全程走参数化条件，不拼半句原句。
     */
    private List<Integer> scopeSiteIds(String roadPrefix, String siteNoKw, String siteTypeKw) {
        QueryWrapper<TBcastSite> sw = new QueryWrapper<TBcastSite>()
                .eq("del_flag", 0)
                .likeRight("road_name", roadPrefix);
        if (StringUtils.isNotEmpty(siteNoKw)) {
            sw.like("site_no", siteNoKw.trim());
        }
        if (StringUtils.isNotEmpty(siteTypeKw)) {
            sw.like("site_type", siteTypeKw.trim());
        }
        List<TBcastSite> ss = this.bcastSiteMapper.selectList(sw);
        List<Integer> ids = new ArrayList<Integer>(ss.size());
        for (TBcastSite s : ss) {
            if (s.getId() != null) {
                ids.add(s.getId().intValue());
            }
        }
        return ids;
    }

    /** 某一刻所在季度的起止 [起, 止) */
    private Date[] quarterRange(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        int year = c.get(Calendar.YEAR);
        int q = c.get(Calendar.MONTH) / 3;
        Calendar start = Calendar.getInstance();
        start.clear();
        start.set(year, q * 3, 1, 0, 0, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 3);
        return new Date[] {start.getTime(), end.getTime()};
    }

    private String newBillNo() {
        return BILL_PREFIX + SnowflakeIdWorker.getUUID();
    }

    private String newSignNo() {
        return SIGN_PREFIX + SnowflakeIdWorker.getUUID();
    }
}
