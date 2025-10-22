package com.ipn.escom.conversor_sql.service;

import com.ipn.escom.conversor_sql.models.SqlRequest;

public interface SqlService {
    String sintaxisSQL(SqlRequest request);
}