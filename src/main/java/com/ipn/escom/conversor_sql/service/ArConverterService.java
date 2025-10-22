package com.ipn.escom.conversor_sql.service;

import com.ipn.escom.conversor_sql.models.SqlRequest;

public interface ArConverterService {
    String toAlgebraRelacional(SqlRequest request);
}