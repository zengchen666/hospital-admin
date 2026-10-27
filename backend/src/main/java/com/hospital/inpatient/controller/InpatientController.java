package com.hospital.inpatient.controller;

import com.hospital.common.api.PageResult;
import com.hospital.common.api.Result;
import com.hospital.common.audit.AuditLog;
import com.hospital.inpatient.dto.AdmissionQuery;
import com.hospital.inpatient.dto.AdmissionRequest;
import com.hospital.inpatient.dto.AdmissionVO;
import com.hospital.inpatient.dto.BedQuery;
import com.hospital.inpatient.dto.BedVO;
import com.hospital.inpatient.dto.DepositRequest;
import com.hospital.inpatient.dto.OrderRequest;
import com.hospital.inpatient.dto.OrderVO;
import com.hospital.inpatient.dto.NursingQuery;
import com.hospital.inpatient.dto.NursingRequest;
import com.hospital.inpatient.dto.NursingVO;
import com.hospital.inpatient.dto.TransferRequest;
import com.hospital.inpatient.dto.WardVO;
import com.hospital.inpatient.service.AdmissionService;
import com.hospital.inpatient.service.MedicalOrderService;
import com.hospital.inpatient.service.NursingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * 住院管理接口。
 *
 * <p>权限按岗位拆：{@code inpatient:admission:add} 办入院/分床/出院、
 * {@code inpatient:order:add} 开医嘱、{@code inpatient:order:exec} 执行医嘱、
 * {@code inpatient:query} 查询。</p>
 */
@Validated
@RestController
@RequestMapping("/inpatient")
public class InpatientController {

    private final AdmissionService admissionService;
    private final MedicalOrderService orderService;
    private final NursingService nursingService;

    public InpatientController(AdmissionService admissionService,
                               MedicalOrderService orderService,
                               NursingService nursingService) {
        this.admissionService = admissionService;
        this.orderService = orderService;
        this.nursingService = nursingService;
    }

    @PostMapping("/admission")
    @PreAuthorize("hasAuthority('inpatient:admission:add')")
    @AuditLog(module = "住院管理", operation = "入院登记")
    public Result<String> admit(@Valid @RequestBody AdmissionRequest request) {
        return Result.ok(String.valueOf(admissionService.admit(request)));
    }

    @GetMapping("/admission/page")
    @PreAuthorize("hasAuthority('inpatient:query')")
    public Result<PageResult<AdmissionVO>> admissionPage(@Valid AdmissionQuery query) {
        return Result.ok(admissionService.page(query));
    }

    @GetMapping("/admission/{id}")
    @PreAuthorize("hasAuthority('inpatient:query')")
    public Result<AdmissionVO> admissionDetail(@PathVariable Long id) {
        return Result.ok(admissionService.detail(id));
    }

    /** 分床 / 换床 */
    @PutMapping("/admission/{id}/bed")
    @PreAuthorize("hasAuthority('inpatient:admission:add')")
    @AuditLog(module = "住院管理", operation = "分床")
    public Result<Void> assignBed(@PathVariable Long id,
                                  @RequestParam @NotNull(message = "床位不能为空") Long bedId) {
        admissionService.assignBed(id, bedId);
        return Result.ok();
    }

    /** 转科：换科室 / 换医生，原床位释放 */
    @PostMapping("/admission/{id}/transfer")
    @PreAuthorize("hasAuthority('inpatient:admission:add')")
    @AuditLog(module = "住院管理", operation = "转科")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        admissionService.transfer(id, request.getDeptId(), request.getDoctorId());
        return Result.ok();
    }

    @PutMapping("/admission/{id}/discharge")
    @PreAuthorize("hasAuthority('inpatient:admission:add')")
    @AuditLog(module = "住院管理", operation = "办理出院")
    public Result<Void> discharge(@PathVariable Long id) {
        admissionService.discharge(id);
        return Result.ok();
    }

    /** 预交金充值（只增不减，退款走退费流程） */
    @PostMapping("/admission/{id}/deposit")
    @PreAuthorize("hasAuthority('inpatient:admission:add')")
    @AuditLog(module = "住院管理", operation = "预交金充值")
    public Result<Void> addDeposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        admissionService.addDeposit(id, request.getAmount());
        return Result.ok();
    }

    /** 床位图：某病区的床位与占用情况 */
    @GetMapping("/bed/page")
    @PreAuthorize("hasAuthority('inpatient:query')")
    public Result<PageResult<BedVO>> beds(@Valid BedQuery query) {
        return Result.ok(admissionService.beds(query));
    }

    /** 病区列表：床位图与入院登记的下拉框共用 */
    @GetMapping("/ward/list")
    @PreAuthorize("hasAuthority('inpatient:query')")
    public Result<List<WardVO>> wards() {
        return Result.ok(admissionService.wards());
    }

    /** 可分配床位：按病区过滤，只返回空闲床 */
    @GetMapping("/bed/available")
    @PreAuthorize("hasAuthority('inpatient:query')")
    public Result<List<BedVO>> availableBeds(
            @RequestParam(required = false) Long wardId) {
        return Result.ok(admissionService.availableBeds(wardId));
    }

    /**
     * 新增护理记录（体温单 / 生命体征 / 护理记录）。
     *
     * <p>记录护士取当前登录用户，前端传什么都不认 —— 护理记录是病历的一部分，必须能追责。</p>
     */
    @PostMapping("/nursing")
    @PreAuthorize("hasAuthority('inpatient:nursing:add')")
    @AuditLog(module = "住院管理", operation = "新增护理记录")
    public Result<String> addNursing(@Valid @RequestBody NursingRequest request) {
        return Result.ok(String.valueOf(nursingService.add(request)));
    }

    /**
     * 护理记录分页。
     *
     * <p>{@code recordType} 为可选，但前端体温单页面必须传 ——
     * 三种记录在一张表里，不过滤就是三类混着出。</p>
     */
    @GetMapping("/nursing/page")
    @PreAuthorize("hasAuthority('inpatient:query')")
    public Result<PageResult<NursingVO>> nursingPage(@Valid NursingQuery query) {
        return Result.ok(nursingService.page(query));
    }

    @PostMapping("/order")
    @PreAuthorize("hasAuthority('inpatient:order:add')")
    @AuditLog(module = "住院管理", operation = "开立医嘱")
    public Result<String> createOrder(@Valid @RequestBody OrderRequest request) {
        return Result.ok(String.valueOf(orderService.create(request)));
    }

    @GetMapping("/order/list")
    @PreAuthorize("hasAuthority('inpatient:query')")
    public Result<List<OrderVO>> orders(@RequestParam @NotNull(message = "住院记录不能为空") Long admissionId) {
        return Result.ok(orderService.listByAdmission(admissionId));
    }

    @PutMapping("/order/{id}/execute")
    @PreAuthorize("hasAuthority('inpatient:order:exec')")
    @AuditLog(module = "住院管理", operation = "执行医嘱")
    public Result<Void> executeOrder(@PathVariable Long id) {
        orderService.execute(id);
        return Result.ok();
    }

    @PutMapping("/order/{id}/stop")
    @PreAuthorize("hasAuthority('inpatient:order:add')")
    @AuditLog(module = "住院管理", operation = "停止医嘱")
    public Result<Void> stopOrder(@PathVariable Long id) {
        orderService.stop(id);
        return Result.ok();
    }
}
