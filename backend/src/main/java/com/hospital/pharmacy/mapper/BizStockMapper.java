package com.hospital.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.pharmacy.entity.BizStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface BizStockMapper extends BaseMapper<BizStock> {

    /**
     * 取该药品在该库房的可用批次，<b>近效期优先</b>。
     *
     * <p>排序里 {@code expire_date IS NULL} 放最前：没登记效期的批次应当优先出库，
     * 否则它会永远压在库里，最后变成一笔说不清的呆滞库存。</p>
     */
    @Select("""
            SELECT * FROM biz_stock
             WHERE drug_id = #{drugId}
               AND warehouse_id = #{warehouseId}
               AND quantity > 0
               AND deleted = 0
             ORDER BY (expire_date IS NULL) ASC, expire_date ASC, id ASC
            """)
    List<BizStock> selectAvailable(@Param("drugId") Long drugId,
                                   @Param("warehouseId") Long warehouseId);

    /**
     * 扣减库存：条件更新，余量不足时影响行数为 0。
     *
     * <p>这是「库存不为负」的唯一保证。先查后改在发药窗口排队时必然穿仓。</p>
     *
     * @return 1 成功；0 表示余量不足（被别人抢先发走了）
     */
    @Update("""
            UPDATE biz_stock
               SET quantity = quantity - #{num},
                   update_time = NOW()
             WHERE id = #{id}
               AND quantity >= #{num}
               AND deleted = 0
            """)
    int decrease(@Param("id") Long id, @Param("num") int num);

    /**
     * 按批号回退库存（退药用）。
     *
     * <p><b>必须退回原批号，不能随便找一个批次加回去</b>：不同批号的效期不同，
     * 退错批号会让近效期的药继续压在库里，而退回来的药明明还有两年效期 ——
     * 看上去总量对上了，实际把效期账做乱了。</p>
     *
     * @return 1 成功；0 表示该批次不存在（原始数据被清过）
     */
    @Update("""
            UPDATE biz_stock
               SET quantity = quantity + #{num},
                   update_time = NOW()
             WHERE drug_id = #{drugId}
               AND warehouse_id = #{warehouseId}
               AND batch_no = #{batchNo}
               AND deleted = 0
            """)
    int increaseByBatch(@Param("drugId") Long drugId,
                        @Param("warehouseId") Long warehouseId,
                        @Param("batchNo") String batchNo,
                        @Param("num") int num);
}
