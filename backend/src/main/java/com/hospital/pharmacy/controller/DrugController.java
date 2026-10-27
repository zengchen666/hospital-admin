package com.hospital.pharmacy.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.pharmacy.dto.DrugQuery;
import com.hospital.pharmacy.dto.DrugRequest;
import com.hospital.pharmacy.dto.DrugVO;
import com.hospital.pharmacy.dto.PurchaseInRequest;
import com.hospital.pharmacy.dto.PurchaseQuery;
import com.hospital.pharmacy.dto.PurchaseRequest;
import com.hospital.pharmacy.dto.PurchaseVO;
import com.hospital.pharmacy.dto.StockInRequest;
import com.hospital.pharmacy.dto.StockOutRequest;
import com.hospital.pharmacy.dto.StockQuery;
import com.hospital.pharmacy.dto.StockVO;
import com.hospital.pharmacy.dto.SupplierQuery;
import com.hospital.pharmacy.dto.SupplierRequest;
import com.hospital.pharmacy.dto.SupplierVO;
import com.hospital.pharmacy.dto.WarehouseRequest;
import com.hospital.pharmacy.dto.WarehouseVO;
import com.hospital.pharmacy.service.DrugService;
import com.hospital.pharmacy.service.PurchaseService;
import com.hospital.pharmacy.service.SupplierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 药品与库存接口：药品字典、库存、库房、供应商、采购。
 *
 * <p>权限按动作拆：{@code pharmacy:query} 只看、
 * {@code pharmacy:drug:edit} 维护字典与库房、
 * {@code pharmacy:stock:in} / {@code pharmacy:stock:out} 动库存、
 * {@code pharmacy:purchase:edit} / {@code pharmacy:purchase:audit} 走采购审批。</p>
 *
 * <p><b>发药不在这里</b>：发药要写发药单并推进处方状态，走 {@code /pharmacy/dispense}。</p>
 */
@Validated
@RestController
@RequestMapping("/drug")
public class DrugController {

    private final DrugService drugService;
    private final SupplierService supplierService;
    private final PurchaseService purchaseService;

    public DrugController(DrugService drugService,
                          SupplierService supplierService,
                          PurchaseService purchaseService) {
        this.drugService = drugService;
        this.supplierService = supplierService;
        this.purchaseService = purchaseService;
    }

    /* ---------------- 药品字典 ---------------- */

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<PageResult<DrugVO>> page(@Valid DrugQuery query) {
        return Result.ok(drugService.pageDrugs(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pharmacy:drug:edit')")
    @AuditLog(module = "药房管理", operation = "新增药品")
    public Result<String> create(@Valid @RequestBody DrugRequest request) {
        return Result.ok(String.valueOf(drugService.createDrug(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pharmacy:drug:edit')")
    @AuditLog(module = "药房管理", operation = "修改药品")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DrugRequest request) {
        drugService.updateDrug(id, request);
        return Result.ok();
    }

    /**
     * 删除药品（逻辑删除）。
     *
     * <p>有库存的药品拒绝删除 —— 药还在架子上却查不到，盘点时对不上账。</p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('pharmacy:drug:edit')")
    @AuditLog(module = "药房管理", operation = "删除药品")
    public Result<Void> delete(@PathVariable Long id) {
        drugService.deleteDrug(id);
        return Result.ok();
    }

    /* ---------------- 库存 ---------------- */

    @GetMapping("/stock/page")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<PageResult<StockVO>> stockPage(@Valid StockQuery query) {
        return Result.ok(drugService.pageStock(query));
    }

    @PostMapping("/stock/in")
    @PreAuthorize("hasAuthority('pharmacy:stock:in')")
    @AuditLog(module = "药房管理", operation = "药品入库")
    public Result<Void> stockIn(@Valid @RequestBody StockInRequest request) {
        drugService.stockIn(request);
        return Result.ok();
    }

    /** 出库：报损 / 科室领用。发药不走这里，发药要写发药单并推进处方状态 */
    @PostMapping("/stock/out")
    @PreAuthorize("hasAuthority('pharmacy:stock:out')")
    @AuditLog(module = "药房管理", operation = "药品出库")
    public Result<Void> stockOut(@Valid @RequestBody StockOutRequest request) {
        drugService.stockOut(request);
        return Result.ok();
    }

    @GetMapping("/stock/warn")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<List<StockVO>> stockWarn() {
        return Result.ok(drugService.warnList());
    }

    @GetMapping("/stock/expire")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<List<StockVO>> stockExpire(
            @RequestParam(required = false) @Min(1) @Max(3650) Integer expireDays) {
        return Result.ok(drugService.expireList(expireDays));
    }

    /* ---------------- 库房 ---------------- */

    @GetMapping("/warehouse/list")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<List<WarehouseVO>> warehouses() {
        return Result.ok(drugService.warehouses());
    }

    @PostMapping("/warehouse")
    @PreAuthorize("hasAuthority('pharmacy:drug:edit')")
    @AuditLog(module = "药房管理", operation = "新增库房")
    public Result<String> createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return Result.ok(String.valueOf(drugService.createWarehouse(request)));
    }

    /* ---------------- 供应商 ---------------- */

    @GetMapping("/supplier/page")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<PageResult<SupplierVO>> supplierPage(@Valid SupplierQuery query) {
        return Result.ok(supplierService.page(query));
    }

    @GetMapping("/supplier/{id}")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<SupplierVO> supplierDetail(@PathVariable Long id) {
        return Result.ok(supplierService.detail(id));
    }

    @PostMapping("/supplier")
    @PreAuthorize("hasAuthority('pharmacy:drug:edit')")
    @AuditLog(module = "药房管理", operation = "新增供应商")
    public Result<String> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return Result.ok(String.valueOf(supplierService.create(request)));
    }

    @PutMapping("/supplier/{id}")
    @PreAuthorize("hasAuthority('pharmacy:drug:edit')")
    @AuditLog(module = "药房管理", operation = "修改供应商")
    public Result<Void> updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        supplierService.update(id, request);
        return Result.ok();
    }

    /* ---------------- 采购 ---------------- */

    @GetMapping("/purchase/page")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<PageResult<PurchaseVO>> purchasePage(@Valid PurchaseQuery query) {
        return Result.ok(purchaseService.page(query));
    }

    @GetMapping("/purchase/{id}")
    @PreAuthorize("hasAuthority('pharmacy:query')")
    public Result<PurchaseVO> purchaseDetail(@PathVariable Long id) {
        return Result.ok(purchaseService.detail(id));
    }

    @PostMapping("/purchase")
    @PreAuthorize("hasAuthority('pharmacy:purchase:edit')")
    @AuditLog(module = "药房管理", operation = "新建采购单")
    public Result<String> createPurchase(@Valid @RequestBody PurchaseRequest request) {
        return Result.ok(String.valueOf(purchaseService.create(request)));
    }

    /**
     * 采购单审核。
     *
     * <p><b>审核不加库存</b>：审核是「同意买」，入库是货到验收后的独立动作。
     * 申请人也不能审自己的单（不相容职务）。</p>
     */
    @PutMapping("/purchase/{id}/audit")
    @PreAuthorize("hasAuthority('pharmacy:purchase:audit')")
    @AuditLog(module = "药房管理", operation = "采购单审核")
    public Result<Void> auditPurchase(@PathVariable Long id) {
        purchaseService.audit(id);
        return Result.ok();
    }

    /** 采购入库：加库存 + 写流水 + 回填实到数量 */
    @PostMapping("/purchase/{id}/in")
    @PreAuthorize("hasAuthority('pharmacy:stock:in')")
    @AuditLog(module = "药房管理", operation = "采购入库")
    public Result<Void> purchaseIn(@PathVariable Long id, @Valid @RequestBody PurchaseInRequest request) {
        purchaseService.stockIn(id, request);
        return Result.ok();
    }

    @PutMapping("/purchase/{id}/void")
    @PreAuthorize("hasAuthority('pharmacy:purchase:edit')")
    @AuditLog(module = "药房管理", operation = "作废采购单")
    public Result<Void> voidPurchase(@PathVariable Long id) {
        purchaseService.voidPurchase(id);
        return Result.ok();
    }
}
