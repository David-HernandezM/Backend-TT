package com.ipn.escom.conversor_sql.service;

import com.ipn.escom.conversor_sql.models.ArConvertResponse;
import com.ipn.escom.conversor_sql.models.SqlRequest;

public interface ArConverterService {
    ArConvertResponse toAlgebraRelacionalConPasos(SqlRequest req);
}