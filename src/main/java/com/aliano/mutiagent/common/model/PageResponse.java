package com.aliano.mutiagent.common.model;

import java.util.List;

public record PageResponse<T>(List<T> items, int pageNo, int pageSize, long total) {
}
