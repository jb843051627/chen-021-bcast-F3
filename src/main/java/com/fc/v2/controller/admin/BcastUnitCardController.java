package com.fc.v2.controller.admin;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.custom.BcastCoverageRow;
import com.fc.v2.model.custom.BcastUnitCardHit;
import com.fc.v2.service.ITBcastUnitCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 台站核点卡立卡单 Controller。
 *
 * <p>递卡的来路有两条，两条走的是同一个说法：本层只做转递，立卡规矩、档位进退、
 * 还欠折数、该露哪几张，一律由服务层那几条方法独家定调，这里不再开第二道
 * 绕得过去的口子。</p>
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

    @Log(title = "台站核点卡立卡单集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("bcast:bcastUnitCard:list")
    @ResponseBody
    public ResultTable list(TBcastUnitCard record) {
        QueryWrapper<TBcastUnitCard> queryWrapper = new QueryWrapper<TBcastUnitCard>();
        startPage();
        com.github.pagehelper.PageInfo<TBcastUnitCard> page =
                new com.github.pagehelper.PageInfo<TBcastUnitCard>(bcastUnitCardService.selectTBcastUnitCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "台站核点卡立卡单新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("bcast:bcastUnitCard:add")
    @ResponseBody
    public AjaxResult add(TBcastUnitCard record) {
        return toAjax(bcastUnitCardService.insertTBcastUnitCard(record));
    }

    @Log(title = "台站核点卡立卡单修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("bcast:bcastUnitCard:edit")
    @ResponseBody
    public AjaxResult editSave(TBcastUnitCard record) {
        return toAjax(bcastUnitCardService.updateTBcastUnitCard(record));
    }

    @Log(title = "台站核点卡立卡单删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("bcast:bcastUnitCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(bcastUnitCardService.deleteTBcastUnitCardByIds(ids));
    }

    @Log(title = "台站核点卡推进一态", action = "advance")
    @ApiOperation(value = "推进一态", notes = "推进一态（待核→核讫→压卡）")
    @PostMapping("/advance")
    @RequiresPermissions("bcast:bcastUnitCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id) {
        return toAjax(bcastUnitCardService.advanceTBcastUnitCard(id));
    }

    @Log(title = "台站核点卡退回一态", action = "revert")
    @ApiOperation(value = "退回一态", notes = "退回一态（核讫→待核，上一趟记下的保留）")
    @PostMapping("/revert")
    @RequiresPermissions("bcast:bcastUnitCard:revert")
    @ResponseBody
    public AjaxResult revert(Long id) {
        return toAjax(bcastUnitCardService.revertTBcastUnitCard(id));
    }

    @Log(title = "台站核点卡寻卡", action = "search")
    @ApiOperation(value = "寻卡", notes = "卡号、台站代号、台站类别给全给半都行；只看本级有权的那一层")
    @GetMapping("/search")
    @RequiresPermissions("bcast:bcastUnitCard:search")
    @ResponseBody
    public ResultTable search(String scopeRoad, String billNo, String siteNo, String siteType) {
        List<BcastUnitCardHit> hits = bcastUnitCardService.searchUnitCards(scopeRoad, billNo, siteNo, siteType);
        return pageTable(hits, hits.size());
    }

    @Log(title = "台站核点卡季表覆盖率", action = "coverage")
    @ApiOperation(value = "季表覆盖率", notes = "属地里台站的总数做分母、已经核讫的张数做分子，一层层往上出")
    @GetMapping("/coverage")
    @RequiresPermissions("bcast:bcastUnitCard:coverage")
    @ResponseBody
    public ResultTable coverage() {
        List<BcastCoverageRow> rows = bcastUnitCardService.coverageStat();
        return pageTable(rows, rows.size());
    }
}
