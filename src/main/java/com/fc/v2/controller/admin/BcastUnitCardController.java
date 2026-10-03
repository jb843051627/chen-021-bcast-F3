package com.fc.v2.controller.admin;

import java.util.Date;
import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverage;
import com.fc.v2.model.custom.BcastUnitCardForm;
import com.fc.v2.model.custom.BcastUnitCardHit;
import com.fc.v2.model.custom.BcastUnitCardReceipt;
import com.fc.v2.service.ITBcastUnitCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 台站核点卡 Controller。
 *
 * <p>页面与接口都不自己定规矩：合不合规、算哪一步、进退、还欠、该露哪几张，
 * 一律问 {@link ITBcastUnitCardService}。卡号、签认号、还欠、当前档不从页面收。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "台站核点卡立卡单")
@Controller
@RequestMapping("/BcastUnitCardController")
public class BcastUnitCardController extends BaseController {

    private final String prefix = "admin/bcastUnitCard";

    @Autowired
    private ITBcastUnitCardService bcastUnitCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("bcast:bcastUnitCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "台站核点卡本层在册查询", action = "list")
    @ApiOperation(value = "本层在册查询", notes = "按属地那一路圈定，本级没有就空着，不拿旁的行凑")
    @GetMapping("/list")
    @RequiresPermissions("bcast:bcastUnitCard:list")
    @ResponseBody
    public ResultTable list(@RequestParam(required = false) Integer nodeNo,
                            @RequestParam(required = false) String roadPrefix) {
        // 服务层先圈台站再点卡（两步），这里不套 PageHelper，免得分页错配到台站那一步上
        int level = nodeNo == null ? 0 : nodeNo.intValue();
        List<TBcastUnitCard> all = bcastUnitCardService.listByScope(level, roadPrefix);
        com.fc.v2.model.custom.Tablepar tp = com.fc.v2.model.custom.Tablepar.buildPageRequest();
        int pageNum = tp.getPage();
        int limit = tp.getLimit();
        long total = all.size();
        List<TBcastUnitCard> page = all;
        if (pageNum > 0 && limit > 0) {
            int from = Math.min((pageNum - 1) * limit, all.size());
            int to = Math.min(from + limit, all.size());
            page = all.subList(from, to);
        }
        return pageTable(page, total);
    }

    @Log(title = "台站核点卡寻卡", action = "search")
    @ApiOperation(value = "寻卡", notes = "卡号/台站代号/台站类别给全给半，只在本级那一层里找")
    @GetMapping("/search")
    @RequiresPermissions("bcast:bcastUnitCard:list")
    @ResponseBody
    public AjaxResult search(@RequestParam(required = false) String billNo,
                             @RequestParam(required = false) String siteNo,
                             @RequestParam(required = false) String siteType,
                             @RequestParam(required = false) Integer nodeNo,
                             @RequestParam(required = false) String roadPrefix) {
        int level = nodeNo == null ? 0 : nodeNo.intValue();
        List<BcastUnitCardHit> hits = bcastUnitCardService.searchCards(billNo, siteNo, siteType, level, roadPrefix);
        return AjaxResult.success("寻着" + hits.size() + "张").put("data", hits);
    }

    @Log(title = "季度核点覆盖率", action = "coverage")
    @ApiOperation(value = "覆盖率", notes = "分母取属地台站总数，分子取本季已核讫张数")
    @GetMapping("/coverage")
    @RequiresPermissions("bcast:bcastUnitCard:list")
    @ResponseBody
    public AjaxResult coverage(@RequestParam(required = false) Integer nodeNo,
                               @RequestParam(required = false) String roadPrefix) {
        int level = nodeNo == null ? 0 : nodeNo.intValue();
        BcastCoverage cov = bcastUnitCardService.coverage(level, roadPrefix, new Date());
        return AjaxResult.successData(200, cov);
    }

    @ApiOperation(value = "立卡页跳转", notes = "立卡页跳转")
    @GetMapping("/toAdd")
    @RequiresPermissions("bcast:bcastUnitCard:add")
    public String toAdd(ModelMap model) {
        // 只给在册（未关停）的台站供挂；关停站挂不上去
        model.addAttribute("siteList", bcastUnitCardService.listLiveSites());
        return prefix + "/add";
    }

    @Log(title = "台站核点卡递卡", action = "add")
    @ApiOperation(value = "递卡立卡", notes = "唯一递卡口子，回执带系统发出的卡号与折好的还欠")
    @PostMapping("/add")
    @RequiresPermissions("bcast:bcastUnitCard:add")
    @ResponseBody
    public AjaxResult add(BcastUnitCardForm form) {
        BcastUnitCardReceipt receipt = bcastUnitCardService.fileCard(form);
        return toReceipt(receipt);
    }

    @ApiOperation(value = "改卡页跳转", notes = "改卡页跳转：卡号、还欠、当前档只读")
    @GetMapping("/toEdit/{id}")
    @RequiresPermissions("bcast:bcastUnitCard:edit")
    public String toEdit(@PathVariable("id") Long id, ModelMap model) {
        TBcastUnitCard card = bcastUnitCardService.selectTBcastUnitCardById(id);
        model.addAttribute("card", card);
        model.addAttribute("statusWord", card == null ? "" : bcastUnitCardService.statusWord(card));
        return prefix + "/edit";
    }

    @Log(title = "台站核点卡改卡", action = "edit")
    @ApiOperation(value = "改卡保存", notes = "走同一个递卡口子；压卡的改不动")
    @PostMapping("/edit")
    @RequiresPermissions("bcast:bcastUnitCard:edit")
    @ResponseBody
    public AjaxResult editSave(BcastUnitCardForm form) {
        BcastUnitCardReceipt receipt = bcastUnitCardService.fileCard(form);
        return toReceipt(receipt);
    }

    @Log(title = "台站核点卡推进", action = "advance")
    @ApiOperation(value = "推进一态", notes = "待核→核讫发签认号；核讫→压卡封顶")
    @PostMapping("/advance")
    @RequiresPermissions("bcast:bcastUnitCard:edit")
    @ResponseBody
    public AjaxResult advance(Long id) {
        int rows = bcastUnitCardService.advanceTBcastUnitCard(id);
        return rows > 0 ? success("已推进一步") : error("这一步迈不过去：步子不得乱踏，压了卡的也动不得");
    }

    @Log(title = "台站核点卡退回", action = "revert")
    @ApiOperation(value = "退回一态", notes = "只准核讫→待核，上一趟记下的仍保留")
    @PostMapping("/revert")
    @RequiresPermissions("bcast:bcastUnitCard:edit")
    @ResponseBody
    public AjaxResult revert(Long id) {
        int rows = bcastUnitCardService.revertTBcastUnitCard(id);
        return rows > 0 ? success("已退回待核，上一趟记下的仍保留") : error("只有核讫退得回；待核无处可退，压卡退不动");
    }

    @Log(title = "台站核点卡叫停", action = "halt")
    @ApiOperation(value = "就地叫停", notes = "只叫停得动在走的卡；压卡的撤不掉，叫停后同台站名额空出")
    @DeleteMapping("/remove")
    @RequiresPermissions("bcast:bcastUnitCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        int n = bcastUnitCardService.deleteTBcastUnitCardByIds(ids);
        return n > 0 ? success("叫停" + n + "张；压卡的撤不掉") : error("一张也没叫停：压卡的撤不掉，或卡不在册");
    }

    /** 回执回话：收下报系统发的卡号与还欠；不收下逐条点错栏的名 */
    private AjaxResult toReceipt(BcastUnitCardReceipt receipt) {
        if (receipt.isAccepted()) {
            AjaxResult ok = AjaxResult.success("已立卡，卡号" + receipt.getBillNo()
                    + "，还欠点位" + receipt.getLackCount());
            ok.put("data", receipt);
            return ok;
        }
        return AjaxResult.error("卡进不了库：" + String.join("；", receipt.getErrors()));
    }
}
