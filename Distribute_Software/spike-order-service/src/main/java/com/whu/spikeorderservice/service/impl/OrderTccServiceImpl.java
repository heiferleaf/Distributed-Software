package com.whu.spikeorderservice.service.impl;

import com.whu.spikeorderservice.entity.Order;
import com.whu.spikeorderservice.entity.TccTransactionLog;
import com.whu.spikeorderservice.entity.TccTransactionLogId;
import com.whu.spikeorderservice.repository.OrderRepository;
import com.whu.spikeorderservice.repository.TccTransactionLogRepository;
import com.whu.spikeorderservice.service.OrderTccService;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTccServiceImpl implements OrderTccService {

    private final OrderRepository orderRepository;
    private final TccTransactionLogRepository tccTransactionLogRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean prepareOrder(BusinessActionContext actionContext, Long orderId, Long userId, Long productId, Integer amount, BigDecimal price) {
        String xid = actionContext.getXid();
        String branchId = String.valueOf(actionContext.getBranchId());
        log.info("订单TCC Try 阶段开始, xid: {}, branchId: {}, orderId: {}", xid, branchId, orderId);

        // 防重检查: 如果存在当前分支的Try记录，说明是重复调用直接打回或者放行
        TccTransactionLogId logId = new TccTransactionLogId(xid, branchId, "Try");
        if (tccTransactionLogRepository.existsById(logId)) {
            log.info("Try存在记录，幂等直接返回true");
            return true;
        }

        // 防悬挂: 如果Cancel比Try先到，已经留下了Cancel日志，这里直接抛出异常拒绝
        TccTransactionLogId cancelLogId = new TccTransactionLogId(xid, branchId, "Cancel");
        if (tccTransactionLogRepository.existsById(cancelLogId)) {
            throw new RuntimeException("检测到Cancel早于Try执行(防悬挂触发)，拒绝Try本地写操作");
        }

        // 插入Try日志记录
        TccTransactionLog tryLog = new TccTransactionLog();
        tryLog.setTxId(xid);
        tryLog.setBranchId(branchId);
        tryLog.setActionType("Try");
        tryLog.setStatus(1);
        tccTransactionLogRepository.save(tryLog);


        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setAmount(amount);
        order.setTotalPrice(price.multiply(BigDecimal.valueOf(amount)));
        order.setStatus(0); // 0-建立中

        orderRepository.save(order);
        log.info("订单TCC Try 阶段落表完成, orderId: {}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean commit(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        String branchId = String.valueOf(actionContext.getBranchId());
        Long orderId = Long.valueOf(actionContext.getActionContext("orderId").toString());
        log.info("订单TCC Confirm 阶段开始, xid: {}, orderId: {}", xid, orderId);

        // 幂等判断
        TccTransactionLogId confirmLogId = new TccTransactionLogId(xid, branchId, "Confirm");
        if (tccTransactionLogRepository.existsById(confirmLogId)) {
             return true;
        }

        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(1); // 1-成功创建并待支付
            orderRepository.save(order);
        });

        // 插入Confirm记录
        TccTransactionLog confirmLog = new TccTransactionLog();
        confirmLog.setTxId(xid);
        confirmLog.setBranchId(branchId);
        confirmLog.setActionType("Confirm");
        confirmLog.setStatus(1);
        tccTransactionLogRepository.save(confirmLog);

        log.info("订单TCC Confirm 阶段完成, orderId: {}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollback(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        String branchId = String.valueOf(actionContext.getBranchId());

        // 幂等判断
        TccTransactionLogId cancelLogId = new TccTransactionLogId(xid, branchId, "Cancel");
        if (tccTransactionLogRepository.existsById(cancelLogId)) {
            return true;
        }

        // 空回滚检查与保护：如果压根没有Try记录，说明Try根本没执行，我们需要记录成功Cancel（让Seata开心），但不碰业务表。
        TccTransactionLogId tryLogId = new TccTransactionLogId(xid, branchId, "Try");
        boolean noTryRecord = !tccTransactionLogRepository.existsById(tryLogId);

        // 写入Cancel日志 (防悬挂底座)
        TccTransactionLog cancelLog = new TccTransactionLog();
        cancelLog.setTxId(xid);
        cancelLog.setBranchId(branchId);
        cancelLog.setActionType("Cancel");
        cancelLog.setStatus(1);
        tccTransactionLogRepository.save(cancelLog);

        if (noTryRecord) {
            log.warn("订单TCC Cancel 发生空回滚保护(无需回滚业务数据，仅保存Cancel空防悬挂日志), xid: {}", xid);
            return true;
        }

        // actionContext parameters might be null if rollback due to suspension
        Object orderIdObj = actionContext.getActionContext("orderId");
        if (orderIdObj == null) {
            return true;
        }

        Long orderId = Long.valueOf(orderIdObj.toString());
        log.info("订单TCC Cancel 阶段开始, xid: {}, orderId: {}", xid, orderId);

        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(2); // 2-创建失败取消
            orderRepository.save(order);
        });

        log.info("订单TCC Cancel 阶段完成, orderId: {}", orderId);
        return true;
    }
}
