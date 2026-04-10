package com.whu.spikeorderservice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SnowFlakeIDGenerator {
    // 起始时间戳
    private final long twepoch = 1767225600000L;

    // 序列位数 & 标识位数
    private final long sequenceBits = 12l;
    private final long workerIdBits = 10l;

    // 序列最大值 & 标识最大值
    private final long sequenceMax = (-1 ^ (-1 << sequenceBits));
    private final long workerIdMax = (-1 ^ (-1 << workerIdBits));

    // 标识的偏移量 & 事件戳的偏移量
    private final long workerIdShift = sequenceBits;
    private final long timestampShift = workerIdShift + workerIdBits;

    // 序列号起始值 & 机器号 & 上一个时间戳（防止时钟回调）
    private long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowFlakeIDGenerator(@Value("${spring.snowflake.worker-id}") long workerId) {
        if(workerId > workerIdMax || workerId < 0) {
            throw new RuntimeException("Invalid workerId");
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = genTime();

        if(timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回调");
        }

        if(timestamp == lastTimestamp) {
            sequence = (sequence + 1) & sequenceMax;
            if(sequence == 0) {
                timestamp = waitForNextMills(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        // 凭借
        return ((lastTimestamp - twepoch) << timestampShift)
                | (workerId << workerIdShift)
                | sequence ;
    }

    private long waitForNextMills(long lastTimestamp) {
        long timestamp = genTime();
        while(timestamp <= lastTimestamp) {
            timestamp = genTime();
        }
        return timestamp;
    }

    private long genTime() {
        return System.nanoTime();
    }
}
