package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.BcastCardRuleException;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.mapper.auto.TBcastUnitCardMapper;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverageRow;
import com.fc.v2.model.custom.BcastUnitCardHit;

/**
 * 台站核点卡：收口规矩的单测。库用 mock 顶替，落库的卡都截下来当面点验；
 * 寻卡、列表这些带条件出门的，条件由一面小镜子照着评，跟从库里点上来一个意思。
 */
@ExtendWith(MockitoExtension.class)
class TBcastUnitCardServiceImplTest {

    @Mock
    private TBcastUnitCardMapper cardMapper;

    @Mock
    private TBcastSiteMapper siteMapper;

    @org.mockito.InjectMocks
    private TBcastUnitCardServiceImpl service;

    /** 真正"落库"的卡都进这个篮子，卡面各栏从篮子里点验 */
    private final List<TBcastUnitCard> landed = new ArrayList<TBcastUnitCard>();

    /** 真正"写回"的更新都进这个篮子，SET 了哪几栏、落的什么值当面点 */
    private final List<UpdateWrapper<?>> updates = new ArrayList<UpdateWrapper<?>>();

    // ================= 家什 =================

    private TBcastSite site(long id, String siteNo, String name, String type, String road, Integer status) {
        TBcastSite s = new TBcastSite();
        s.setId(id);
        s.setSiteNo(siteNo);
        s.setSiteName(name);
        s.setSiteType(type);
        s.setRoadName(road);
        s.setStatus(status);
        s.setDelFlag(Integer.valueOf(0));
        return s;
    }

    private TBcastUnitCard card(Long id, Integer siteId, String billNo, Integer should, Integer done, Integer status) {
        TBcastUnitCard c = new TBcastUnitCard();
        c.setId(id);
        c.setSiteId(siteId);
        c.setBillNo(billNo);
        c.setShouldCount(should);
        c.setDoneCount(done);
        c.setStatus(status);
        c.setDelFlag(Integer.valueOf(0));
        return c;
    }

    /** 立卡递单：只填台站、层级、应核、已核这几样人该填的 */
    private TBcastUnitCard draft(Integer siteId, Integer should, Integer done) {
        TBcastUnitCard r = new TBcastUnitCard();
        r.setSiteId(siteId);
        r.setNodeNo(Integer.valueOf(2));
        r.setShouldCount(should);
        r.setDoneCount(done);
        return r;
    }

    /** 落库的卡截进篮子 */
    private void mockInsertBasket() {
        landed.clear();
        when(cardMapper.insert(any())).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                landed.add(inv.getArgument(0));
                return Integer.valueOf(1);
            }
        });
    }

    /** 写回的更新截进篮子 */
    private void mockUpdateCapture() {
        updates.clear();
        when(cardMapper.update(any(), any())).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                updates.add(inv.getArgument(1));
                return Integer.valueOf(1);
            }
        });
    }

    private TBcastUnitCard landedAt(int idx) {
        return landed.get(idx);
    }

    /** 把 UpdateWrapper 的 SET 子句摊成 栏→值 */
    private static Map<String, Object> setValues(UpdateWrapper<?> uw) {
        Map<String, Object> out = new HashMap<String, Object>();
        Map<String, Object> params = uw.getParamNameValuePairs();
        for (String pair : uw.getSqlSet().split(",")) {
            String[] kv = pair.split("=", 2);
            out.put(kv[0].trim(), params.get(paramKey(kv[1].trim())));
        }
        return out;
    }

    private static String paramKey(String ref) {
        Matcher m = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(ref);
        return m.matches() ? m.group(1) : ref;
    }

    // ================= 立卡：系统落笔 =================

    @Test
    void 立卡系统落笔_卡号签认号进展还欠都不由人拟() {
        when(siteMapper.selectOne(any())).thenReturn(
                site(0, "TZ00", "苗岭转播台", "无线发射台", "长岭省—河湾区—苗岭县", 0));
        when(cardMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        mockInsertBasket();

        TBcastUnitCard r = draft(0, 37, 30);
        r.setBillNo("自拟卡号");
        r.setContent("自拟签认");
        r.setStatus(Integer.valueOf(2));
        r.setLackCount(Integer.valueOf(999));

        int ok = service.insertTBcastUnitCard(r);

        assertEquals(1, ok);
        TBcastUnitCard c = landedAt(0);
        // 卡号由系统发出，人自家拟的递进来不入库
        assertTrue(c.getBillNo().startsWith("KD"));
        assertNotEquals("自拟卡号", c.getBillNo());
        // 随卡签认号不给手填留位置：立卡时是空的，走到核讫系统才签发
        assertNull(c.getContent());
        // 眼下算哪一档由系统自己数：刚立卡一律待核，屏上手填的档不作数
        assertEquals(Integer.valueOf(0), c.getStatus());
        // 还欠不由人报也不由人填：应核三十七、已核三十，它落七
        assertEquals(Integer.valueOf(7), c.getLackCount());
        // 所属台站代号从底册挂上来
        assertEquals("TZ00", c.getSiteNo());
        assertEquals(Integer.valueOf(0), c.getDelFlag());
    }

    @Test
    void 还欠三个时点之立卡_应核减已核照实落() {
        when(siteMapper.selectOne(any())).thenReturn(
                site(0, "TZ00", "苗岭转播台", "无线发射台", "长岭省—河湾区—苗岭县", 0));
        when(cardMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        mockInsertBasket();

        service.insertTBcastUnitCard(draft(0, 37, 30));
        service.insertTBcastUnitCard(draft(0, 60, 16));
        service.insertTBcastUnitCard(draft(0, 50, 50));

        assertEquals(Integer.valueOf(7), landedAt(0).getLackCount());
        assertEquals(Integer.valueOf(44), landedAt(1).getLackCount());
        assertEquals(Integer.valueOf(0), landedAt(2).getLackCount());
    }

    @Test
    void 已核空缺按零起算() {
        when(siteMapper.selectOne(any())).thenReturn(
                site(0, "TZ00", "苗岭转播台", "无线发射台", "长岭省—河湾区—苗岭县", 0));
        when(cardMapper.selectCount(any())).thenReturn(Integer.valueOf(0));
        mockInsertBasket();

        service.insertTBcastUnitCard(draft(0, 37, null));

        assertEquals(Integer.valueOf(0), landedAt(0).getDoneCount());
        assertEquals(Integer.valueOf(37), landedAt(0).getLackCount());
    }

    // ================= 立卡：回执点名 =================

    @Test
    void 已核为负或大过应核_卡进不了库_回执点名已核点位数() {
        when(siteMapper.selectOne(any())).thenReturn(
                site(0, "TZ00", "苗岭转播台", "无线发射台", "长岭省—河湾区—苗岭县", 0));

        BcastCardRuleException neg = assertThrows(BcastCardRuleException.class,
                () -> service.insertTBcastUnitCard(draft(0, 37, -1)));
        assertTrue(neg.getMessage().contains("已核点位数"));

        BcastCardRuleException over = assertThrows(BcastCardRuleException.class,
                () -> service.insertTBcastUnitCard(draft(0, 37, 38)));
        assertTrue(over.getMessage().contains("已核点位数"));

        verify(cardMapper, never()).insert(any());
    }

    @Test
    void 应核为负或空缺_卡进不了库_回执点名应核点位数() {
        when(siteMapper.selectOne(any())).thenReturn(
                site(0, "TZ00", "苗岭转播台", "无线发射台", "长岭省—河湾区—苗岭县", 0));

        BcastCardRuleException neg = assertThrows(BcastCardRuleException.class,
                () -> service.insertTBcastUnitCard(draft(0, -5, 0)));
        assertTrue(neg.getMessage().contains("应核点位数"));

        BcastCardRuleException empty = assertThrows(BcastCardRuleException.class,
                () -> service.insertTBcastUnitCard(draft(0, null, 0)));
        assertTrue(empty.getMessage().contains("应核点位数"));

        verify(cardMapper, never()).insert(any());
    }

    // ================= 立卡：往底册上挂 =================

    @Test
    void 已关停的台站不再收新卡_底册查无此台也挂不上() {
        when(siteMapper.selectOne(any())).thenReturn(
                site(1, "TZ01", "滨河北微波站", "微波站", "长岭省—河湾区—滨河北", 1));
        assertEquals(0, service.insertTBcastUnitCard(draft(1, 37, 30)));

        when(siteMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.insertTBcastUnitCard(draft(99, 37, 30)));

        verify(cardMapper, never()).insert(any());
    }

    @Test
    void 一台站只容一张在走的卡_头一张没走到头第二张立不起来() {
        when(siteMapper.selectOne(any())).thenReturn(
                site(0, "TZ00", "苗岭转播台", "无线发射台", "长岭省—河湾区—苗岭县", 0));
        // 头一遍查：没有在走的卡，立起来；紧跟着再报（两家县局同一天各报一遍）：查着一张在走的
        when(cardMapper.selectCount(any())).thenReturn(Integer.valueOf(0), Integer.valueOf(1));
        mockInsertBasket();

        assertEquals(1, service.insertTBcastUnitCard(draft(0, 37, 30)));
        assertEquals(0, service.insertTBcastUnitCard(draft(0, 37, 30)));
        assertEquals(1, landed.size());

        // 在走的卡按"未压卡"点：旧卡空步数也算在走，压了卡的才算名额空出来
        ArgumentCaptor<QueryWrapper> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(cardMapper, org.mockito.Mockito.times(2)).selectCount(cap.capture());
        String seg = cap.getValue().getSqlSegment();
        assertTrue(seg.contains("site_id"));
        assertTrue(seg.contains("status IS NULL"));
        assertTrue(seg.contains("status <>"));
    }

    // ================= 推进：一步一档 =================

    @Test
    void 待核到核讫到压卡一步一档_压卡封顶再不认() {
        TBcastUnitCard c0 = card(11L, 0, "KD-1", 37, 30, 0);
        TBcastUnitCard c1 = card(11L, 0, "KD-1", 37, 30, 1);
        c1.setContent("QR-KD-1-OLD");
        TBcastUnitCard c2 = card(11L, 0, "KD-1", 37, 30, 2);
        c2.setContent("QR-KD-1-OLD");
        when(cardMapper.selectOne(any())).thenReturn(c0, c1, c2);
        mockUpdateCapture();

        assertEquals(1, service.advanceTBcastUnitCard(11L));
        assertEquals(1, service.advanceTBcastUnitCard(11L));
        // 走到压卡这卡便封了顶：再推不认
        assertEquals(0, service.advanceTBcastUnitCard(11L));

        assertEquals(2, updates.size());
        // 头一步：待核→核讫，系统签发随卡签认号
        Map<String, Object> set1 = setValues(updates.get(0));
        assertEquals(Integer.valueOf(1), set1.get("status"));
        String sign = (String) set1.get("content");
        assertTrue(sign.startsWith("QR-"));
        assertTrue(sign.contains("KD-1"));
        // 第二步：核讫→压卡，签认号不另签
        Map<String, Object> set2 = setValues(updates.get(1));
        assertEquals(Integer.valueOf(2), set2.get("status"));
        assertFalse(set2.containsKey("content"));
    }

    @Test
    void 旧卡空步数按待核容下_推进不抛错照走一档() {
        TBcastUnitCard legacy = card(12L, 0, "KD-OLD", 37, 30, null);
        when(cardMapper.selectOne(any())).thenReturn(legacy);
        mockUpdateCapture();

        // 早年的旧卡步数空着：碰一下就抛错不行，被当成已压卡的也不行
        int ok = service.advanceTBcastUnitCard(12L);

        assertEquals(1, ok);
        Map<String, Object> set = setValues(updates.get(0));
        assertEquals(Integer.valueOf(1), set.get("status"));
        assertTrue(((String) set.get("content")).startsWith("QR-"));
    }

    // ================= 退回：只许核讫退待核 =================

    @Test
    void 核讫退回待核_上一趟记下的签认号仍旧保留() {
        TBcastUnitCard c1 = card(11L, 0, "KD-1", 37, 30, 1);
        c1.setContent("QR-KD-1-OLD");
        when(cardMapper.selectOne(any())).thenReturn(c1);
        mockUpdateCapture();

        assertEquals(1, service.revertTBcastUnitCard(11L));

        Map<String, Object> set = setValues(updates.get(0));
        assertEquals(Integer.valueOf(0), set.get("status"));
        // 上一趟记下的东西仍旧保留：退回不清签认号
        assertFalse(set.containsKey("content"));
    }

    @Test
    void 退回重走_后补的签认盖在原位_不跟上一趟并排摆() {
        TBcastUnitCard c1 = card(11L, 0, "KD-1", 37, 30, 1);
        c1.setContent("QR-KD-1-OLD");
        TBcastUnitCard c0 = card(11L, 0, "KD-1", 37, 30, 0);
        c0.setContent("QR-KD-1-OLD");
        when(cardMapper.selectOne(any())).thenReturn(c1, c0);
        mockUpdateCapture();

        service.revertTBcastUnitCard(11L);
        service.advanceTBcastUnitCard(11L);

        // 重走到核讫，系统另签一笔盖在原位上：卡上还是只有一笔签认号
        Map<String, Object> set = setValues(updates.get(1));
        String sign = (String) set.get("content");
        assertTrue(sign.startsWith("QR-"));
        assertNotEquals("QR-KD-1-OLD", sign);
    }

    @Test
    void 待核没得退_压卡退不动_旧卡空步数也不抛错() {
        TBcastUnitCard c0 = card(11L, 0, "KD-1", 37, 30, 0);
        TBcastUnitCard c2 = card(11L, 0, "KD-1", 37, 30, 2);
        TBcastUnitCard legacy = card(12L, 0, "KD-OLD", 37, 30, null);
        when(cardMapper.selectOne(any())).thenReturn(c0, c2, legacy);

        assertEquals(0, service.revertTBcastUnitCard(11L));
        assertEquals(0, service.revertTBcastUnitCard(11L));
        assertEquals(0, service.revertTBcastUnitCard(12L));

        verify(cardMapper, never()).update(any(), any());
    }

    // ================= 改卡：还欠重算、手填的档不作数 =================

    @Test
    void 已核点位数动了_还欠重算落回卡上() {
        TBcastUnitCard cur = card(11L, 0, "KD-1", 60, 16, 0);
        cur.setLackCount(Integer.valueOf(44));
        when(cardMapper.selectOne(any())).thenReturn(cur);
        mockUpdateCapture();

        TBcastUnitCard r = new TBcastUnitCard();
        r.setId(11L);
        r.setDoneCount(Integer.valueOf(25));
        assertEquals(1, service.updateTBcastUnitCard(r));

        Map<String, Object> set = setValues(updates.get(0));
        assertEquals(Integer.valueOf(25), set.get("done_count"));
        // 应核六十、已核二十五，还欠落三十五
        assertEquals(Integer.valueOf(35), set.get("lack_count"));
    }

    @Test
    void 换了属地挂靠的那一层_还欠也重算一遍() {
        TBcastUnitCard cur = card(11L, 0, "KD-1", 50, 50, 1);
        cur.setLackCount(Integer.valueOf(0));
        when(cardMapper.selectOne(any())).thenReturn(cur);
        mockUpdateCapture();

        TBcastUnitCard r = new TBcastUnitCard();
        r.setId(11L);
        r.setNodeNo(Integer.valueOf(1));
        assertEquals(1, service.updateTBcastUnitCard(r));

        Map<String, Object> set = setValues(updates.get(0));
        assertEquals(Integer.valueOf(1), set.get("node_no"));
        // 重算的三个时点都走这一条路：换了挂靠层，还欠照公式再落一回
        assertEquals(Integer.valueOf(0), set.get("lack_count"));
    }

    @Test
    void 手填的档卡号签认还欠一概不作数() {
        TBcastUnitCard cur = card(11L, 0, "KD-1", 37, 30, 0);
        cur.setLackCount(Integer.valueOf(7));
        cur.setContent("QR-KD-1-ABC");
        when(cardMapper.selectOne(any())).thenReturn(cur);
        mockUpdateCapture();

        TBcastUnitCard r = new TBcastUnitCard();
        r.setId(11L);
        r.setStatus(Integer.valueOf(2));
        r.setBillNo("手拟卡号");
        r.setContent("手拟签认");
        r.setLackCount(Integer.valueOf(999));
        r.setDoneCount(Integer.valueOf(30));
        assertEquals(1, service.updateTBcastUnitCard(r));

        Map<String, Object> set = setValues(updates.get(0));
        // 屏上手填的档、人自家拟的卡号签认，都不作数
        assertFalse(set.containsKey("status"));
        assertFalse(set.containsKey("bill_no"));
        assertFalse(set.containsKey("content"));
        // 还欠栏位只读不写：落的是系统重算的数，不是递进来的 999
        assertEquals(Integer.valueOf(7), set.get("lack_count"));
    }

    @Test
    void 改卡已核填得不对_回执照样点名() {
        TBcastUnitCard cur = card(11L, 0, "KD-1", 37, 30, 0);
        when(cardMapper.selectOne(any())).thenReturn(cur);

        TBcastUnitCard over = new TBcastUnitCard();
        over.setId(11L);
        over.setDoneCount(Integer.valueOf(99));
        BcastCardRuleException ex = assertThrows(BcastCardRuleException.class,
                () -> service.updateTBcastUnitCard(over));
        assertTrue(ex.getMessage().contains("已核点位数"));

        TBcastUnitCard negShould = new TBcastUnitCard();
        negShould.setId(11L);
        negShould.setShouldCount(Integer.valueOf(-1));
        BcastCardRuleException ex2 = assertThrows(BcastCardRuleException.class,
                () -> service.updateTBcastUnitCard(negShould));
        assertTrue(ex2.getMessage().contains("应核点位数"));

        verify(cardMapper, never()).update(any(), any());
    }

    // ================= 压卡封顶 =================

    @Test
    void 压了卡的封了顶_字也动不得整张撤不掉() {
        TBcastUnitCard sealed = card(11L, 0, "KD-1", 37, 37, 2);
        when(cardMapper.selectOne(any())).thenReturn(sealed);

        TBcastUnitCard r = new TBcastUnitCard();
        r.setId(11L);
        r.setDoneCount(Integer.valueOf(30));
        assertEquals(0, service.updateTBcastUnitCard(r));
        assertEquals(0, service.deleteTBcastUnitCardById(11L));

        verify(cardMapper, never()).update(any(), any());
        verify(cardMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void 在走的卡可就地叫停_批量撤只撤得动的() {
        final Map<Long, TBcastUnitCard> byId = new HashMap<Long, TBcastUnitCard>();
        byId.put(1L, card(1L, 0, "KD-1", 37, 37, 2));
        byId.put(2L, card(2L, 0, "KD-2", 37, 30, 0));
        byId.put(3L, card(3L, 0, "KD-3", 37, 30, null));
        when(cardMapper.selectOne(any())).thenAnswer(new Answer<TBcastUnitCard>() {
            @Override
            public TBcastUnitCard answer(InvocationOnMock inv) {
                QueryWrapper<?> w = inv.getArgument(0);
                // 参数是生成 SQL 时才落进参数表的，先催一遍再点
                w.getSqlSegment();
                for (Object v : w.getParamNameValuePairs().values()) {
                    if (v instanceof Long) {
                        return byId.get(v);
                    }
                }
                return null;
            }
        });
        when(cardMapper.deleteById(any(Serializable.class))).thenReturn(Integer.valueOf(1));

        // 压了卡的撤不掉；在走的、旧卡空步数的都可撤
        assertEquals(2, service.deleteTBcastUnitCardByIds("1,2,3"));
        verify(cardMapper, never()).deleteById(Long.valueOf(1L));
        verify(cardMapper).deleteById(Long.valueOf(2L));
        verify(cardMapper).deleteById(Long.valueOf(3L));
    }

    // ================= 列表：只列库里确有的卡 =================

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void 该屏只列库里确有的卡_在场的几张不许抹掉_本层没有就空着() {
        List<TBcastUnitCard> all = Arrays.asList(
                card(11L, 0, "KD-1", 37, 30, 0),
                card(12L, 0, "KD-2", 37, 37, 1),
                card(13L, 0, "KD-3", 37, 37, 2));
        when(cardMapper.selectList(any())).thenReturn(all, new ArrayList<TBcastUnitCard>());

        // 待核、核讫、压卡三张都在场，一张不许抹掉
        assertEquals(3, service.selectTBcastUnitCardList(new QueryWrapper<TBcastUnitCard>()).size());
        // 本层这一季还没立过卡，那一屏就空着
        assertEquals(0, service.selectTBcastUnitCardList(new QueryWrapper<TBcastUnitCard>()).size());

        // 出门的条件只补删除标记，不在服务层里另设状态过滤
        ArgumentCaptor<QueryWrapper> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(cardMapper, org.mockito.Mockito.times(2)).selectList(cap.capture());
        String seg = cap.getValue().getSqlSegment();
        assertTrue(seg.contains("del_flag"));
        assertFalse(seg.contains("status"));
    }

    // ================= 寻卡 =================

    /** 寻卡用的底册：苗岭县一台、滨河北一台（已关停）、邻县一台 */
    private List<TBcastSite> searchSites() {
        return Arrays.asList(
                site(0, "TZ00", "苗岭转播台", "无线发射台", "长岭省—河湾区—苗岭县", 0),
                site(1, "TZ01", "滨河北微波站", "微波站", "长岭省—河湾区—滨河北", 1),
                site(2, "TZ02", "邻县转播台", "转播台", "长岭省—河东区—邻县", 0));
    }

    private List<TBcastUnitCard> searchCards() {
        TBcastUnitCard a = card(11L, 0, "KD10A", 37, 30, 0);
        a.setNodeNo(Integer.valueOf(2));
        TBcastUnitCard b = card(12L, 1, "KD20B", 60, 60, 1);
        b.setNodeNo(Integer.valueOf(2));
        TBcastUnitCard c = card(13L, 2, "KD30C", 50, 50, 2);
        c.setNodeNo(Integer.valueOf(1));
        return Arrays.asList(a, b, c);
    }

    /** 底册这面镜子：照着服务层递出来的条件评 */
    private void mockSiteMirror(final List<TBcastSite> sites) {
        when(siteMapper.selectList(any())).thenAnswer(new Answer<List<TBcastSite>>() {
            @Override
            public List<TBcastSite> answer(InvocationOnMock inv) {
                QueryWrapper<?> w = inv.getArgument(0);
                List<TBcastSite> out = new ArrayList<TBcastSite>();
                for (TBcastSite s : sites) {
                    if (mirrorMatches(w, siteRow(s))) {
                        out.add(s);
                    }
                }
                return out;
            }
        });
    }

    /** 卡册这面镜子：同上 */
    private void mockCardMirror(final List<TBcastUnitCard> cards) {
        when(cardMapper.selectList(any())).thenAnswer(new Answer<List<TBcastUnitCard>>() {
            @Override
            public List<TBcastUnitCard> answer(InvocationOnMock inv) {
                QueryWrapper<?> w = inv.getArgument(0);
                List<TBcastUnitCard> out = new ArrayList<TBcastUnitCard>();
                for (TBcastUnitCard c : cards) {
                    if (mirrorMatches(w, cardRow(c))) {
                        out.add(c);
                    }
                }
                return out;
            }
        });
    }

    private interface RowGet {
        Object get(String column);
    }

    private RowGet siteRow(final TBcastSite s) {
        return new RowGet() {
            @Override
            public Object get(String column) {
                if ("id".equals(column)) return s.getId();
                if ("del_flag".equals(column)) return s.getDelFlag();
                if ("road_name".equals(column)) return s.getRoadName();
                if ("site_no".equals(column)) return s.getSiteNo();
                if ("site_type".equals(column)) return s.getSiteType();
                if ("status".equals(column)) return s.getStatus();
                throw new IllegalArgumentException("site 没这一栏: " + column);
            }
        };
    }

    private RowGet cardRow(final TBcastUnitCard c) {
        return new RowGet() {
            @Override
            public Object get(String column) {
                if ("id".equals(column)) return c.getId();
                if ("del_flag".equals(column)) return c.getDelFlag();
                if ("bill_no".equals(column)) return c.getBillNo();
                if ("site_id".equals(column)) return c.getSiteId();
                if ("status".equals(column)) return c.getStatus();
                if ("node_no".equals(column)) return c.getNodeNo();
                throw new IllegalArgumentException("card 没这一栏: " + column);
            }
        };
    }

    /** 小镜子：只评本服务用到的那几种条件形状（=、LIKE、IN），跟库里的 semantics 一个意思 */
    private boolean mirrorMatches(QueryWrapper<?> w, RowGet row) {
        String sql = w.getSqlSegment();
        if (sql == null || sql.trim().isEmpty()) {
            return true;
        }
        String s = sql.trim();
        int orderBy = s.indexOf(" ORDER BY");
        if (orderBy >= 0) {
            s = s.substring(0, orderBy).trim();
        }
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.trim().isEmpty()) {
            return true;
        }
        for (String frag : s.split(" AND ")) {
            if (!mirrorFrag(frag.trim(), w.getParamNameValuePairs(), row)) {
                return false;
            }
        }
        return true;
    }

    private boolean mirrorFrag(String frag, Map<String, Object> params, RowGet row) {
        Matcher in = Pattern.compile("(\\w+) IN \\((.+)\\)").matcher(frag);
        if (in.matches()) {
            Object actual = row.get(in.group(1));
            for (String ref : in.group(2).split(",")) {
                Object v = params.get(paramKey(ref.trim()));
                if (actual != null && v != null && norm(actual) == norm(v)) {
                    return true;
                }
            }
            return false;
        }
        Matcher like = Pattern.compile("(\\w+) LIKE (.+)").matcher(frag);
        if (like.matches()) {
            Object actual = row.get(like.group(1));
            Object v = params.get(paramKey(like.group(2).trim()));
            if (actual == null || v == null) {
                return false;
            }
            String a = actual.toString();
            String p = v.toString();
            if (p.startsWith("%") && p.endsWith("%") && p.length() >= 2) {
                return a.contains(p.substring(1, p.length() - 1));
            }
            if (p.endsWith("%")) {
                return a.startsWith(p.substring(0, p.length() - 1));
            }
            if (p.startsWith("%")) {
                return a.endsWith(p.substring(1));
            }
            return a.equals(p);
        }
        Matcher eq = Pattern.compile("(\\w+) = (.+)").matcher(frag);
        if (eq.matches()) {
            Object actual = row.get(eq.group(1));
            Object v = params.get(paramKey(eq.group(2).trim()));
            if (actual == null || v == null) {
                return actual == v;
            }
            return norm(actual) == norm(v);
        }
        throw new IllegalStateException("镜子不认得的条件: " + frag);
    }

    /** 数字一律拉成长整再比（卡上 site_id 是 int、底册 id 是 bigint） */
    private static long norm(Object v) {
        return ((Number) v).longValue();
    }

    @Test
    void 寻卡咬中间几个字也算寻着_台站与所压层级一并带回来() {
        mockSiteMirror(searchSites());
        mockCardMirror(searchCards());

        // 卡号只咬上中间几个字
        List<BcastUnitCardHit> hits = service.searchUnitCards(null, "D10", null, null);

        assertEquals(1, hits.size());
        BcastUnitCardHit hit = hits.get(0);
        assertEquals("KD10A", hit.getCard().getBillNo());
        // 所属台站与它压在哪一层一并带回来，不必再让人抄第二回
        assertEquals("苗岭转播台", hit.getSiteName());
        assertEquals("无线发射台", hit.getSiteType());
        assertEquals("长岭省—河湾区—苗岭县", hit.getRoadName());
        assertEquals(Integer.valueOf(2), hit.getCard().getNodeNo());
    }

    @Test
    void 寻卡按台站类别咬字_关停台站的旧卡仍旧查得到() {
        mockSiteMirror(searchSites());
        mockCardMirror(searchCards());

        List<BcastUnitCardHit> hits = service.searchUnitCards(null, null, null, "微波");

        assertEquals(1, hits.size());
        assertEquals("KD20B", hits.get(0).getCard().getBillNo());
        assertEquals("微波站", hits.get(0).getSiteType());
    }

    @Test
    void 该露哪几张只看本级有权的那一层() {
        mockSiteMirror(searchSites());
        mockCardMirror(searchCards());

        // 县里这一层：翻不出邻县的卡
        List<BcastUnitCardHit> county = service.searchUnitCards("长岭省—河湾区—苗岭县", null, null, null);
        assertEquals(1, county.size());
        assertEquals("KD10A", county.get(0).getCard().getBillNo());

        // 市域这份名单：本市的两个县都在，别市别区的行不混进来
        List<BcastUnitCardHit> city = service.searchUnitCards("长岭省—河湾区", null, null, null);
        assertEquals(2, city.size());
        assertEquals("KD10A", city.get(0).getCard().getBillNo());
        assertEquals("KD20B", city.get(1).getCard().getBillNo());
    }

    @Test
    void 本层还没立过卡那一屏就空着_不拿旁的行来凑() {
        mockSiteMirror(searchSites());
        // 这一层底册一座台站都对不上：卡册根本不必翻
        assertEquals(0, service.searchUnitCards("长岭省—河湾区—空壳县", null, null, null).size());
        verify(cardMapper, never()).selectList(any());

        // 层是对的、号对不上：照样空着
        mockCardMirror(searchCards());
        assertEquals(0, service.searchUnitCards("长岭省—河东区—邻县", "没这个号", null, null).size());
    }

    @Test
    void 库里立过的每一张都在这一屏上找得着() {
        mockSiteMirror(searchSites());
        mockCardMirror(searchCards());

        // 三样都不给：待核的、核讫的、压了卡的、挂在关停台站底下的，一张不落
        List<BcastUnitCardHit> hits = service.searchUnitCards(null, null, null, null);

        assertEquals(3, hits.size());
        assertEquals("KD10A", hits.get(0).getCard().getBillNo());
        assertEquals("KD20B", hits.get(1).getCard().getBillNo());
        assertEquals("KD30C", hits.get(2).getCard().getBillNo());
    }

    // ================= 季表覆盖率 =================

    @Test
    void 覆盖率一层层往上出_上一层的数与下一层是同一次算的() {
        // 底册七座台：苗岭县三（其一已关停）、滨河县三、望江县一
        List<TBcastSite> sites = Arrays.asList(
                site(0, "TZ00", "苗岭一台", "无线发射台", "长岭省—河湾区—苗岭县", 0),
                site(1, "TZ01", "苗岭二台", "微波站", "长岭省—河湾区—苗岭县", 1),
                site(2, "TZ02", "苗岭三台", "转播台", "长岭省—河湾区—苗岭县", 0),
                site(3, "TZ03", "滨河一台", "无线发射台", "长岭省—河湾区—滨河县", 0),
                site(4, "TZ04", "滨河二台", "微波站", "长岭省—河湾区—滨河县", 0),
                site(6, "TZ06", "滨河三台", "转播台", "长岭省—河湾区—滨河县", 0),
                site(5, "TZ05", "望江一台", "无线发射台", "长岭省—山南市—望江县", 0));
        // 在册卡六张：核讫、压卡都算已经核讫；待核的、旧卡空步数的不算
        List<TBcastUnitCard> cards = Arrays.asList(
                card(11L, 0, "KD1", 37, 37, 1),
                card(12L, 1, "KD2", 37, 37, 2),
                card(13L, 2, "KD3", 37, 30, 0),
                card(14L, 3, "KD4", 37, 30, null),
                card(15L, 4, "KD5", 37, 37, 1),
                card(16L, 5, "KD6", 37, 37, 2));
        mockSiteMirror(sites);
        mockCardMirror(cards);

        List<BcastCoverageRow> rows = service.coverageStat();

        assertEquals(6, rows.size());
        // 省—市—县一层压一层下来
        assertRow(rows.get(0), "长岭省", BcastCoverageRow.LEVEL_PROVINCE, 7, 4);
        assertRow(rows.get(1), "长岭省—河湾区", BcastCoverageRow.LEVEL_CITY, 6, 3);
        assertRow(rows.get(2), "长岭省—河湾区—苗岭县", BcastCoverageRow.LEVEL_COUNTY, 3, 2);
        assertRow(rows.get(3), "长岭省—河湾区—滨河县", BcastCoverageRow.LEVEL_COUNTY, 3, 1);
        assertRow(rows.get(4), "长岭省—山南市", BcastCoverageRow.LEVEL_CITY, 1, 1);
        assertRow(rows.get(5), "长岭省—山南市—望江县", BcastCoverageRow.LEVEL_COUNTY, 1, 1);
        // 上一层的数与下一层各屏点出来的是同一次算的：省=市并、市=县并
        assertEquals(rows.get(0).getTotalStations(),
                rows.get(1).getTotalStations() + rows.get(4).getTotalStations());
        assertEquals(rows.get(0).getVerifiedCards(),
                rows.get(1).getVerifiedCards() + rows.get(4).getVerifiedCards());
        assertEquals(rows.get(1).getTotalStations(),
                rows.get(2).getTotalStations() + rows.get(3).getTotalStations());
        assertEquals(rows.get(1).getVerifiedCards(),
                rows.get(2).getVerifiedCards() + rows.get(3).getVerifiedCards());
    }

    @Test
    void 分母取台站总数不取建卡数_覆盖率按实数出不约成几成() {
        // 两座台站只建了一张卡、还没核讫：分母是 2，不是 1
        List<TBcastSite> sites = Arrays.asList(
                site(0, "TZ00", "苗岭一台", "无线发射台", "长岭省—河湾区—苗岭县", 0),
                site(1, "TZ01", "苗岭二台", "微波站", "长岭省—河湾区—苗岭县", 0));
        List<TBcastUnitCard> cards = Arrays.asList(card(11L, 0, "KD1", 37, 30, 0));
        mockSiteMirror(sites);
        mockCardMirror(cards);

        List<BcastCoverageRow> rows = service.coverageStat();

        BcastCoverageRow county = rows.get(2);
        assertEquals(2, county.getTotalStations());
        assertEquals(0, county.getVerifiedCards());
        // 就按两个实数出，不拿"大约几成"回话
        assertEquals("0/2", county.getCoverageText());
    }

    private void assertRow(BcastCoverageRow row, String road, int level, int total, int verified) {
        assertEquals(road, row.getRoadName());
        assertEquals(level, row.getLevel());
        assertEquals(total, row.getTotalStations());
        assertEquals(verified, row.getVerifiedCards());
        assertEquals(verified + "/" + total, row.getCoverageText());
    }
}
