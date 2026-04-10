package com.whu.spikeinventoryservice.repository;

import com.whu.spikeinventoryservice.entity.TccTransactionLog;
import com.whu.spikeinventoryservice.entity.TccTransactionLogId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TccTransactionLogRepository extends JpaRepository<TccTransactionLog, TccTransactionLogId> {
}
