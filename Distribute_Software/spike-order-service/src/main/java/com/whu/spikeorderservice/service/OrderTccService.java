package com.whu.spikeorderservice.service;

import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import java.math.BigDecimal;

@LocalTCC
public interface OrderTccService {

    @TwoPhaseBusinessAction(name = "prepareOrder", commitMethod = "commit", rollbackMethod = "rollback")
    boolean prepareOrder(BusinessActionContext actionContext,
                         @BusinessActionContextParameter(paramName = "orderId") Long orderId,
                         @BusinessActionContextParameter(paramName = "userId") Long userId,
                         @BusinessActionContextParameter(paramName = "productId") Long productId,
                         @BusinessActionContextParameter(paramName = "amount") Integer amount,
                         @BusinessActionContextParameter(paramName = "price") BigDecimal price);

    boolean commit(BusinessActionContext actionContext);

    boolean rollback(BusinessActionContext actionContext);
}
