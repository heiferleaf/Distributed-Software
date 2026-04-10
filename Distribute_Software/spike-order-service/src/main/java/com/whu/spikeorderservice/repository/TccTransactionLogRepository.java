package com.whu.spikeorderservice.repository;

import com.whu.spikeorderservice.entity.TccTransactionLog;
import com.whu.spikeorderservice.entity.TccTransactionLogId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TccTransactionLogRepository extends JpaRepository<TccTransactionLog, TccTransactionLogId> {
}
