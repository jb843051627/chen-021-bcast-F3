package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.mapper.auto.TBcastUnitCardMapper;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverage;
import com.fc.v2.model.custom.BcastUnitCardForm;
import com.fc.v2.model.custom.BcastUnitCardHit;
import com.fc.v2.model.custom.BcastUnitCardReceipt;

/**
 * 台站核点卡：立卡、折算、步子、名额、属地与季表的规矩，一条一钉。
 * 库用 mock 顶替，落库的卡都进内存篮子，从篮子里点回来验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TBcastUnitCardServiceImplTest {

    @Mock
    private TBcastSiteMapper cardSiteMapper;

    @Mock
    private TBcastUnitCardMapper cardMapper;

    @org.mockito.InjectMocks
    private TBcastUnitCardServiceImpl service;

    private final Map<Long, TBcastSite> sites = new TreeMap<Long, TBcastSite>();
    private final Map<Long, TBcastUnitCard> store = new LinkedHashMap<Long, TBcastUnitCard>();
    private long seq = 1000;

    private static final String ROAD_HE = "长岭省—河湾区";
    private static final String ROAD_SHAN = "长岭省—山南区";

    @BeforeEach
    void setUp() {
        wireMappers();
        // 河湾区两座在册、一座关停；山南区一座在册
        putSite(0L, "TZ00", "苗岭转播台", "无线发射台", ROAD_HE + "—苗岭县", 0);
        putSite(1L, "TZ01", "滨河北微波站", "微波站", ROAD_HE + "—滨河北", 1);
        putSite(2L, "TZ02", "河湾转播台", "转播台", ROAD_HE + "—河湾县", 0);
        putSite(20L, "TZ20", "山南发射台", "无线发射台", ROAD_SHAN + "—甲县", 0);
    }

    // ===================== 立卡：卡号系统发、还欠折出 =====================

    @Test
    void 立卡三十七已核三十_还欠落七_卡号系统发() {
        BcastUnitCardReceipt r = service.fileCard(form(null, 0L, "2", "37", "30", "要件"));
        assertTrue(r.isAccepted(), () -> r.getErrors().toString());
        assertEquals(Integer.valueOf(7), r.getLackCount());
        assertNotNull(r.getBillNo());
        assertTrue(r.getBillNo().startsWith("HK"));
        TBcastUnitCard c = onlyCard();
        assertEquals(r.getBillNo(), c.getBillNo());
        assertEquals(Integer.valueOf(7), c.getLackCount());
        assertEquals(Integer.valueOf(0), c.getStatus());
        assertEquals(Integer.valueOf(2), c.getNodeNo());
        assertEquals("TZ00", c.getSiteNo());
    }

    @Test
    void 还欠三笔折算_六十减十六落四十四_核齐落零() {
        assertEquals(Integer.valueOf(7), service.fileCard(form(null, 0L, "2", "37", "30", null)).getLackCount());
        assertEquals(Integer.valueOf(44), service.fileCard(form(null, 2L, "2", "60", "16", null)).getLackCount());
        BcastUnitCardReceipt r = service.fileCard(form(null, 20L, "1", "50", "50", null));
        assertEquals(Integer.valueOf(0), r.getLackCount());
    }

    @Test
    void 人自家拟的卡号不入库_由系统另发() {
        // 表单上根本没有卡号这一栏；即便有人绕过页面塞到旧签名实体里，也不作数
        TBcastUnitCard raw = new TBcastUnitCard();
        raw.setSiteId(Integer.valueOf(0));
        raw.setNodeNo(Integer.valueOf(2));
        raw.setShouldCount(Integer.valueOf(10));
        raw.setDoneCount(Integer.valueOf(0));
        raw.setBillNo("我自己编的号");
        raw.setStatus(Integer.valueOf(2));
        raw.setLackCount(Integer.valueOf(999));
        assertEquals(1, service.insertTBcastUnitCard(raw));
        // 落库的是内部立卡对象，从在册篮子里点回这一张验
        TBcastUnitCard c = onlyCard();
        assertFalse("我自己编的号".equals(c.getBillNo()));
        assertTrue(c.getBillNo().startsWith("HK"));
        assertEquals(Integer.valueOf(0), c.getStatus());
        assertEquals(Integer.valueOf(10), c.getLackCount());
    }

    // ===================== 校验：回执点名 =====================

    @Test
    void 已核为负带小数大过应核_卡不入库_回执点名已核栏() {
        BcastUnitCardReceipt neg = service.fileCard(form(null, 0L, "2", "37", "-1", null));
        assertFalse(neg.isAccepted());
        assertTrue(join(neg).contains(BcastUnitCardReceipt.FIELD_DONE));

        BcastUnitCardReceipt dot = service.fileCard(form(null, 0L, "2", "37", "30.5", null));
        assertFalse(dot.isAccepted());
        assertTrue(join(dot).contains("已核点位数"));

        BcastUnitCardReceipt over = service.fileCard(form(null, 0L, "2", "37", "38", null));
        assertFalse(over.isAccepted());
        assertTrue(join(over).contains("已核点位数大过应核点位数"));
        verify(cardMapper, never()).insert(any());
    }

    @Test
    void 应核为负带小数空缺_回执点名应核栏() {
        assertFalse(service.fileCard(form(null, 0L, "2", "-3", "0", null)).isAccepted());
        BcastUnitCardReceipt dot = service.fileCard(form(null, 0L, "2", "3.0", "0", null));
        assertTrue(join(dot).contains(BcastUnitCardReceipt.FIELD_SHOULD));
        BcastUnitCardReceipt blank = service.fileCard(form(null, 0L, "2", "", "0", null));
        assertTrue(join(blank).contains(BcastUnitCardReceipt.FIELD_SHOULD));
    }

    @Test
    void 层级序越界与非整数_点层级名() {
        assertTrue(join(service.fileCard(form(null, 0L, "3", "10", "0", null))).contains("属地层级序"));
        assertTrue(join(service.fileCard(form(null, 0L, "县", "10", "0", null))).contains("属地层级序"));
    }

    @Test
    void 关停台站不收新卡_对不上底册也挂不上() {
        BcastUnitCardReceipt closed = service.fileCard(form(null, 1L, "2", "10", "0", null));
        assertFalse(closed.isAccepted());
        assertTrue(join(closed).contains("关停"));
        BcastUnitCardReceipt noSite = service.fileCard(form(null, 99L, "2", "10", "0", null));
        assertTrue(join(noSite).contains("底册"));
    }

    // ===================== 同台站一张在走卡 =====================

    @Test
    void 头一张没压卡没叫停_第二张立不起来_两家同报换人重报都不许叠() {
        assertTrue(service.fileCard(form(null, 0L, "2", "37", "30", null)).isAccepted());
        BcastUnitCardReceipt second = service.fileCard(form(null, 0L, "2", "37", "31", null));
        assertFalse(second.isAccepted());
        assertTrue(join(second).contains("只容一张"));
        assertEquals(1, store.size());
    }

    @Test
    void 压卡之后名额空出_同台站才接得上新卡() {
        BcastUnitCardReceipt first = service.fileCard(form(null, 0L, "2", "37", "30", null));
        Long id = first.getCard().getId();
        assertEquals(1, service.advanceTBcastUnitCard(id));
        assertEquals(1, service.advanceTBcastUnitCard(id));
        assertEquals(Integer.valueOf(2), store.get(id).getStatus());
        // 压了卡：第二张立得起来
        assertTrue(service.fileCard(form(null, 0L, "2", "37", "0", null)).isAccepted());
        assertEquals(2, store.size());
    }

    @Test
    void 叫停在走的卡_名额也空出_压卡的叫停不掉() {
        Long a = service.fileCard(form(null, 0L, "2", "10", "0", null)).getCard().getId();
        Long b = service.fileCard(form(null, 2L, "2", "10", "0", null)).getCard().getId();
        assertTrue(service.haltCard(a));
        assertEquals(Integer.valueOf(1), store.get(a).getDelFlag());
        assertTrue(service.fileCard(form(null, 0L, "2", "10", "0", null)).isAccepted());

        service.advanceTBcastUnitCard(b);
        service.advanceTBcastUnitCard(b);
        assertFalse(service.haltCard(b));
        assertEquals(0, service.deleteTBcastUnitCardById(b));
        assertEquals(Integer.valueOf(0), store.get(b).getDelFlag());
    }

    // ===================== 步子：三态逐级 =====================

    @Test
    void 步子只准逐级_待核到核讫发签认号_越级踏空() {
        Long id = service.fileCard(form(null, 0L, "2", "10", "0", null)).getCard().getId();
        assertEquals(1, service.advanceTBcastUnitCard(id));
        assertEquals(Integer.valueOf(1), store.get(id).getStatus());
        assertNotNull(store.get(id).getSignNo());
        assertTrue(store.get(id).getSignNo().startsWith("QR"));
        assertEquals(1, service.advanceTBcastUnitCard(id));
        assertEquals(Integer.valueOf(2), store.get(id).getStatus());
        // 封了顶再迈不动
        assertEquals(0, service.advanceTBcastUnitCard(id));
    }

    @Test
    void 退回只准核讫到待核_要件与签认号仍旧保留_再推进不另发号() {
        Long id = service.fileCard(form(null, 0L, "2", "10", "0", "头一趟要件")).getCard().getId();
        // 待核无处可退
        assertEquals(0, service.revertTBcastUnitCard(id));
        service.advanceTBcastUnitCard(id);
        String sign = store.get(id).getSignNo();
        assertEquals(1, service.revertTBcastUnitCard(id));
        assertEquals(Integer.valueOf(0), store.get(id).getStatus());
        // 上一趟记下的不抹掉、不并排另存
        assertEquals(sign, store.get(id).getSignNo());
        assertEquals("头一趟要件", store.get(id).getContent());
        // 重走一遍：沿用原签认号
        service.advanceTBcastUnitCard(id);
        assertEquals(sign, store.get(id).getSignNo());
        // 压卡后退不动
        service.advanceTBcastUnitCard(id);
        assertEquals(0, service.revertTBcastUnitCard(id));
    }

    @Test
    void 压卡封顶_添不进改不动撤不掉_要改只能另开() {
        Long id = service.fileCard(form(null, 0L, "2", "10", "0", null)).getCard().getId();
        service.advanceTBcastUnitCard(id);
        service.advanceTBcastUnitCard(id);
        BcastUnitCardReceipt edit = service.fileCard(form(id, 0L, "2", "11", "1", null));
        assertFalse(edit.isAccepted());
        assertTrue(join(edit).contains("压卡"));
        assertEquals(Integer.valueOf(10), store.get(id).getShouldCount());
    }

    // ===================== 旧卡兼容：步数/版次/签认串空着 =====================

    @Test
    void 旧卡步数空着_碰一下不抛错_按待核起算_不当成已压卡() {
        TBcastUnitCard old = landedCard(0L, null, "HK-OLD", 12, 0, null, null);
        // 旧卡占着同台站名额：它仍在走，新卡立不起
        assertFalse(service.fileCard(form(null, 0L, "2", "12", "0", null)).isAccepted());
        // 推进不抛错，null→核讫，发签认号
        assertEquals(1, service.advanceTBcastUnitCard(old.getId()));
        assertEquals(Integer.valueOf(1), old.getStatus());
        assertNotNull(old.getSignNo());
        assertEquals(1, service.advanceTBcastUnitCard(old.getId()));
        assertEquals(Integer.valueOf(2), old.getStatus());
        // null 档待核退不回
        TBcastUnitCard old2 = landedCard(2L, null, "HK-OLD2", 5, 0, null, null);
        assertEquals(0, service.revertTBcastUnitCard(old2.getId()));
        // null 档叫得停
        assertTrue(service.haltCard(old2.getId()));
    }

    @Test
    void 系统数出来的档_屏上手填的不作数() {
        assertEquals("待核", service.statusWord(cardWithStatus(null)));
        assertEquals("待核", service.statusWord(cardWithStatus(0)));
        assertEquals("核讫", service.statusWord(cardWithStatus(1)));
        assertEquals("压卡", service.statusWord(cardWithStatus(2)));
    }

    // ===================== 已核动了、换层：还欠重算（改卡走同一口子） =====================

    @Test
    void 改卡已核变动与换挂靠层_还欠当场重折_卡号签认号不动() {
        Long id = service.fileCard(form(null, 0L, "2", "37", "30", null)).getCard().getId();
        service.advanceTBcastUnitCard(id);
        String bill = store.get(id).getBillNo();
        String sign = store.get(id).getSignNo();

        BcastUnitCardReceipt r = service.fileCard(form(id, 0L, "1", "37", "37", "新要件"));
        assertTrue(r.isAccepted(), () -> r.getErrors().toString());
        assertEquals(Integer.valueOf(0), r.getLackCount());
        assertEquals(Integer.valueOf(1), store.get(id).getNodeNo());
        assertEquals(bill, store.get(id).getBillNo());
        // 点位数动过：核讫退回待核重走，签认号不动
        assertEquals(Integer.valueOf(0), store.get(id).getStatus());
        assertEquals(sign, store.get(id).getSignNo());
    }

    // ===================== 本层在册：属地隔离、空屏不凑行 =====================

    @Test
    void 本层列表只露属地那一路底下的_空前缀整屏空着不查库() {
        landedCard(0L, 0, "HK100", 1, 0, null, null);
        landedCard(2L, 0, "HK101", 1, 0, null, null);
        landedCard(20L, 0, "HK102", 1, 0, null, null);

        List<TBcastUnitCard> he = service.listByScope(2, ROAD_HE);
        assertEquals(2, he.size());
        for (TBcastUnitCard c : he) {
            assertTrue(Arrays.asList("TZ00", "TZ02").contains(c.getSiteNo()));
        }
        List<TBcastUnitCard> shan = service.listByScope(1, ROAD_SHAN);
        assertEquals(1, shan.size());
        assertEquals("TZ20", shan.get(0).getSiteNo());

        store.clear();
        List<TBcastUnitCard> empty = service.listByScope(2, ROAD_HE);
        assertTrue(empty.isEmpty());

        // 说不出本级属地那一路，一屏空着，不翻库、不拿旁人凑
        assertTrue(service.listByScope(2, "  ").isEmpty());
    }

    // ===================== 寻卡 =====================

    @Test
    void 寻卡给全给半_整段与中间几个字都算_连台站与层级一并带回_且不出本层() {
        landedCard(0L, 2, "HK2026A", 1, 0, null, null);
        landedCard(2L, 2, "HK2026B", 1, 0, null, null);
        landedCard(20L, 2, "HK2026C", 1, 0, null, null);

        // 只咬卡号中间几个字，限本层河湾
        List<BcastUnitCardHit> hits = service.searchCards("2026B", null, null, 2, ROAD_HE);
        assertEquals(1, hits.size());
        BcastUnitCardHit h = hits.get(0);
        assertEquals("HK2026B", h.getCard().getBillNo());
        assertEquals("TZ02", h.getSiteNo());
        assertEquals("转播台", h.getSiteType());
        assertEquals(ROAD_HE + "—河湾县", h.getRoadName());
        assertEquals("待核", h.getStatusWord());

        // 按台站代号中间片段，限到苗岭县一路
        assertEquals(1, service.searchCards(null, "Z0", null, 2, ROAD_HE + "—苗岭县").size());
        // 按类别，河湾底下转播台只有一座
        assertEquals(1, service.searchCards(null, null, "转播", 2, ROAD_HE).size());
        // 山南区那张混不进河湾的名单
        assertEquals(2, service.searchCards("2026", null, null, 2, ROAD_HE).size());
        assertEquals(1, service.searchCards("2026", null, null, 2, ROAD_SHAN).size());
        // 本层没有，空着
        assertTrue(service.searchCards(null, null, null, 2, "长岭省—没有的市").isEmpty());
    }

    // ===================== 季表覆盖率 =====================

    @Test
    void 覆盖率只认台站总数与核讫张数_八十座核讫七十四落七四比八十() {
        // 80 座台站另挂在核点市一路，避免与种子台站并数
        String covRoad = "长岭省—核点市";
        for (long i = 100; i < 174; i++) {
            putSite(i, "TZ" + i, "台" + i, "无线发射台", covRoad + "—县" + i, 0);
            landedCard(i, 2, "HKV" + i, 10, 10, 1, "QRx");
        }
        for (long i = 174; i < 180; i++) {
            putSite(i, "TZ" + i, "台" + i, "无线发射台", covRoad + "—县" + i, 0);
            // 已立卡但还在待核的 6 座：分母仍取 80，分子不算它们
            landedCard(i, 2, "HKP" + i, 10, 1, 0, null);
        }

        BcastCoverage cov = service.coverage(1, covRoad, new Date());
        assertEquals(80, cov.getSiteTotal());
        assertEquals(74, cov.getVerifiedCards());
        assertEquals("74/80", cov.getRateText());
        assertEquals(0, new BigDecimal("0.9250").compareTo(cov.getRate()));
    }

    // ===================== 帮手 =====================

    private BcastUnitCardForm form(Long id, Long siteId, String node, String should, String done, String content) {
        BcastUnitCardForm f = new BcastUnitCardForm();
        f.setId(id);
        f.setSiteId(siteId);
        f.setNodeNo(node);
        f.setShouldCount(should);
        f.setDoneCount(done);
        f.setContent(content);
        return f;
    }

    private TBcastUnitCard onlyCard() {
        assertEquals(1, store.size());
        return store.values().iterator().next();
    }

    private String join(BcastUnitCardReceipt r) {
        return String.join("；", r.getErrors());
    }

    private TBcastUnitCard cardWithStatus(Integer st) {
        TBcastUnitCard c = new TBcastUnitCard();
        c.setStatus(st);
        return c;
    }

    private void putSite(long id, String no, String name, String type, String road, int status) {
        TBcastSite s = new TBcastSite();
        s.setId(Long.valueOf(id));
        s.setSiteNo(no);
        s.setSiteName(name);
        s.setSiteType(type);
        s.setRoadName(road);
        s.setStatus(Integer.valueOf(status));
        s.setDelFlag(Integer.valueOf(0));
        sites.put(Long.valueOf(id), s);
    }

    private TBcastUnitCard landedCard(long siteId, Integer nodeNo, String billNo,
                                      int should, int done, Integer stage, String sign) {
        TBcastUnitCard c = new TBcastUnitCard();
        long id = ++seq;
        c.setId(Long.valueOf(id));
        c.setSiteId(Integer.valueOf((int) siteId));
        TBcastSite s = sites.get(siteId);
        c.setSiteNo(s == null ? ("TZ" + siteId) : s.getSiteNo());
        c.setBillNo(billNo);
        c.setNodeNo(nodeNo == null ? Integer.valueOf(2) : nodeNo);
        c.setShouldCount(Integer.valueOf(should));
        c.setDoneCount(Integer.valueOf(done));
        c.setLackCount(Integer.valueOf(should - done));
        c.setStatus(stage);
        c.setSignNo(sign);
        c.setDelFlag(Integer.valueOf(0));
        c.setCreateTime(new Date());
        store.put(Long.valueOf(id), c);
        return c;
    }

    // ===================== 内存库：把两个 mapper 接到篮子上 =====================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireMappers() {
        lenient().when(cardSiteMapper.selectOne(any())).thenAnswer(inv -> {
            AbstractWrapper w = inv.getArgument(0);
            Long id = colLong(w, "id", "=");
            return id == null ? null : sites.get(id);
        });
        lenient().when(cardSiteMapper.selectCount(any())).thenAnswer(inv -> {
            AbstractWrapper w = inv.getArgument(0);
            String prefix = colString(w, "road_name", "LIKE");
            if (prefix != null && prefix.endsWith("%")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            int n = 0;
            for (TBcastSite s : sites.values()) {
                if (s.getDelFlag() != null && s.getDelFlag() == 1) {
                    continue;
                }
                if (prefix != null && (s.getRoadName() == null || !s.getRoadName().startsWith(prefix))) {
                    continue;
                }
                n++;
            }
            return Integer.valueOf(n);
        });
        lenient().when(cardSiteMapper.selectList(any())).thenAnswer(inv -> {
            AbstractWrapper w = inv.getArgument(0);
            List<Object> idVals = colInValues(w, "id");
            List<TBcastSite> out = new ArrayList<TBcastSite>();
            if (!idVals.isEmpty()) {
                for (Object o : idVals) {
                    TBcastSite s = sites.get(Long.valueOf(o.toString()));
                    if (s != null) {
                        out.add(s);
                    }
                }
                return out;
            }
            // 属地圈台站：road_name 前缀匹配，可再压代号、类别（均参数化）
            String road = stripLike(colString(w, "road_name", "LIKE"));
            String noKw = stripLike(colString(w, "site_no", "LIKE"));
            String typeKw = stripLike(colString(w, "site_type", "LIKE"));
            for (TBcastSite s : sites.values()) {
                if (s.getDelFlag() != null && s.getDelFlag() == 1) {
                    continue;
                }
                if (road != null && (s.getRoadName() == null || !s.getRoadName().startsWith(road))) {
                    continue;
                }
                if (noKw != null && (s.getSiteNo() == null || !s.getSiteNo().contains(noKw))) {
                    continue;
                }
                if (typeKw != null && (s.getSiteType() == null || !s.getSiteType().contains(typeKw))) {
                    continue;
                }
                out.add(s);
            }
            return out;
        });

        lenient().when(cardMapper.insert(any())).thenAnswer(inv -> {
            TBcastUnitCard c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(Long.valueOf(++seq));
            }
            if (c.getDelFlag() == null) {
                c.setDelFlag(0);
            }
            store.put(c.getId(), c);
            return Integer.valueOf(1);
        });
        lenient().when(cardMapper.selectById(any())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        lenient().when(cardMapper.selectCount(any())).thenAnswer(inv -> answerCardCount(inv.getArgument(0)));
        lenient().when(cardMapper.selectList(any())).thenAnswer(inv -> answerCardList(inv.getArgument(0)));
        lenient().when(cardMapper.update(any(), any())).thenAnswer(inv -> {
            UpdateWrapper<?> w = inv.getArgument(1);
            Long id = colLong(w, "id", "=");
            TBcastUnitCard c = id == null ? null : store.get(id);
            if (c == null) {
                return Integer.valueOf(0);
            }
            applySet(c, w.getSqlSet(), w.getParamNameValuePairs());
            return Integer.valueOf(1);
        });
    }

    @SuppressWarnings("rawtypes")
    private Integer answerCardCount(AbstractWrapper w) {
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        Map<?, ?> params = w.getParamNameValuePairs();
        if (seg.contains("status IN")) {
            // 覆盖率分子：本季、核讫/压卡、属地台站集合（均参数化）
            List<Integer> statuses = new ArrayList<Integer>();
            for (Object o : colInValues(w, "status")) {
                statuses.add((Integer) o);
            }
            List<Long> scopeIds = new ArrayList<Long>();
            for (Object o : colInValues(w, "site_id")) {
                scopeIds.add(Long.valueOf(o.toString()));
            }
            Date from = boundDate(seg, "create_time >=", params);
            Date to = boundDate(seg, "create_time <", params);
            int n = 0;
            for (TBcastUnitCard c : store.values()) {
                if (c.getDelFlag() != null && c.getDelFlag() == 1) {
                    continue;
                }
                if (c.getStatus() == null || !statuses.contains(c.getStatus())) {
                    continue;
                }
                if (c.getSiteId() == null || !scopeIds.contains(c.getSiteId().longValue())) {
                    continue;
                }
                Date ct = c.getCreateTime();
                if (from != null && ct != null && (ct.before(from) || (to != null && !ct.before(to)))) {
                    continue;
                }
                n++;
            }
            return Integer.valueOf(n);
        }
        // 同台站在走卡计数：site_id=… 且 id<>自身
        Long siteId = colLong(w, "site_id", "=");
        Long excludeId = colLong(w, "id", "<>");
        int n = 0;
        for (TBcastUnitCard c : store.values()) {
            if (c.getDelFlag() != null && c.getDelFlag() == 1) {
                continue;
            }
            if (excludeId != null && c.getId() != null && c.getId().longValue() == excludeId.longValue()) {
                continue;
            }
            if (siteId != null && (c.getSiteId() == null || c.getSiteId().longValue() != siteId.longValue())) {
                continue;
            }
            // 未压卡（含旧卡 null 档）都占名额
            if (c.getStatus() == null || c.getStatus().intValue() != 2) {
                n++;
            }
        }
        return Integer.valueOf(n);
    }

    @SuppressWarnings("rawtypes")
    private List<TBcastUnitCard> answerCardList(AbstractWrapper w) {
        // 卡侧只按 site_id IN（台站已在服务层按属地/代号/类别圈好）与卡号模糊过滤
        List<Long> scopeIds = new ArrayList<Long>();
        for (Object o : colInValues(w, "site_id")) {
            scopeIds.add(Long.valueOf(o.toString()));
        }
        String billKw = stripLike(colString(w, "bill_no", "LIKE"));
        List<TBcastUnitCard> out = new ArrayList<TBcastUnitCard>();
        for (TBcastUnitCard c : store.values()) {
            if (c.getDelFlag() != null && c.getDelFlag() == 1) {
                continue;
            }
            if (!scopeIds.isEmpty() && (c.getSiteId() == null || !scopeIds.contains(c.getSiteId().longValue()))) {
                continue;
            }
            if (billKw != null && (c.getBillNo() == null || !c.getBillNo().contains(billKw))) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    // ---- 从 wrapper 片段里按键取参的确定写法 ----

    /** like 入参剥掉通配符，按"咬得上"比 */
    private String stripLike(String v) {
        return v == null ? null : v.replace("%", "");
    }

    private static final Pattern P = Pattern.compile("MPGENVAL\\d+");

    /** "列 操作 #{...键}" 的入参，按列取 */
    @SuppressWarnings("rawtypes")
    private Object colParam(AbstractWrapper w, String column, String op) {
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        Matcher m = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(column) + "\\s*" + Pattern.quote(op)
                + "\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}", Pattern.CASE_INSENSITIVE).matcher(seg);
        if (!m.find()) {
            return null;
        }
        return w.getParamNameValuePairs().get(m.group(1));
    }

    @SuppressWarnings("rawtypes")
    private Long colLong(AbstractWrapper w, String column, String op) {
        Object v = colParam(w, column, op);
        return v == null ? null : Long.valueOf(v.toString());
    }

    @SuppressWarnings("rawtypes")
    private String colString(AbstractWrapper w, String column, String op) {
        Object v = colParam(w, column, op);
        return v == null ? null : v.toString();
    }

    /** "列 IN (键,键,...)" 的全部入参 */
    @SuppressWarnings("rawtypes")
    private List<Object> colInValues(AbstractWrapper w, String column) {
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        Matcher m = Pattern.compile(Pattern.quote(column) + "\\s+IN\\s*\\(([^)]*)\\)",
                Pattern.CASE_INSENSITIVE).matcher(seg);
        List<Object> out = new ArrayList<Object>();
        if (!m.find()) {
            return out;
        }
        Matcher km = P.matcher(m.group(1));
        while (km.find()) {
            out.add(w.getParamNameValuePairs().get(km.group()));
        }
        return out;
    }

    /** 取日期界点 "列 >= / < #{...键}" */
    private Date boundDate(String seg, String clause, Map<?, ?> params) {
        Matcher m = Pattern.compile(Pattern.quote(clause) + "\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}",
                Pattern.CASE_INSENSITIVE).matcher(seg);
        return m.find() ? (Date) params.get(m.group(1)) : null;
    }

    /** 按 UpdateWrapper 的 set 片段把列值落到内存卡上 */
    private void applySet(TBcastUnitCard c, String sqlSet, Map<?, ?> params) {
        if (sqlSet == null) {
            return;
        }
        Matcher m = Pattern.compile("(\\w+)\\s*=\\s*(#\\{ew\\.paramNameValuePairs\\.(\\w+)}|null)").matcher(sqlSet);
        while (m.find()) {
            String col = m.group(1);
            boolean isNull = m.group(2) == null || "null".equals(m.group(2));
            Object v = isNull ? null : params.get(m.group(3));
            switch (col) {
                case "status":
                    c.setStatus(v == null ? null : Integer.valueOf(v.toString()));
                    break;
                case "sign_no":
                    c.setSignNo(v == null ? null : v.toString());
                    break;
                case "del_flag":
                    c.setDelFlag(v == null ? 0 : Integer.valueOf(v.toString()));
                    break;
                case "node_no":
                    c.setNodeNo(v == null ? null : Integer.valueOf(v.toString()));
                    break;
                case "should_count":
                    c.setShouldCount(v == null ? null : Integer.valueOf(v.toString()));
                    break;
                case "done_count":
                    c.setDoneCount(v == null ? null : Integer.valueOf(v.toString()));
                    break;
                case "lack_count":
                    c.setLackCount(v == null ? null : Integer.valueOf(v.toString()));
                    break;
                case "content":
                    c.setContent(v == null ? null : v.toString());
                    break;
                default:
                    break;
            }
        }
    }
}
