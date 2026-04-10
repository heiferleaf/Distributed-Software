package com.whu.spikeinventoryservice.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * 库存服务 TCC 接口
 */
@LocalTCC
public interface InventoryTccService {

    /**
     * Try 阶段：冻结库存
     *
     * @param actionContext 分布式事务上下文（Seata 注入）
     * @param productId 商品ID
     * @param count 购买数量
     * @return 是否成功
     */
    @TwoPhaseBusinessAction(name = "prepareInventory", commitMethod = "commit", rollbackMethod = "rollback")
    boolean prepare(BusinessActionContext actionContext,
                    @BusinessActionContextParameter(paramName = "productId") Long productId,
                    @BusinessActionContextParameter(paramName = "count") Integer count);

    /**
     * Confirm 阶段：确认执行（此时通常是空操作）
     */
    boolean commit(BusinessActionContext actionContext);

    /**
     * Cancel 阶段：回滚冻结的库存 (由于下单瞬间失败触发)
     */
    boolean rollback(BusinessActionContext actionContext);
}
