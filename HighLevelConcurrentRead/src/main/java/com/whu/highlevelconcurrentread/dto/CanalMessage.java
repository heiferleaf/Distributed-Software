package com.whu.highlevelconcurrentread.dto;

import lombok.Data;

import java.util.List;

@Data
public class CanalMessage<T> {
    // 发生变更的数据库名
    private String database;
    // 发生变更的数据表名
    private String table;
    // 动作类型 insert / delete / update
    private String type;
    // 变化后的数据
    private List<T> data;
    // 发生变更的sql
    private String sql;
    // 主键字段
    private List<String> pkNames;
    // 是否是DDL语句
    private Boolean isDdl;
}
