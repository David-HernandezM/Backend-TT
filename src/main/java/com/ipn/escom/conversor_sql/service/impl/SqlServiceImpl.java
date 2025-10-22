package com.ipn.escom.conversor_sql.service.impl;

import org.springframework.stereotype.Service;

import com.ipn.escom.conversor_sql.models.SqlRequest;
import com.ipn.escom.conversor_sql.service.SqlService;

@Service
public class SqlServiceImpl implements SqlService {

    @Override
    public String sintaxisSQL(SqlRequest request) {
        // Simulación de conversión SQL (Aquí pondrás la lógica real)
        return "Consulta SQL validada exitosamente: " + request.getSqlQuery();
    }
}