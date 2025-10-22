package com.ipn.escom.conversor_sql.service.impl;

import org.springframework.stereotype.Service;

import com.ipn.escom.conversor_sql.conversion.ConversionPipeline;
import com.ipn.escom.conversor_sql.models.SqlRequest;
import com.ipn.escom.conversor_sql.service.ArConverterService;
import com.ipn.escom.conversor_sql.validation.schema.SchemaBuilder;

@Service
public class ArConverterServiceImpl implements ArConverterService {

	private final ConversionPipeline pipeline = new ConversionPipeline();

	@Override
	public String toAlgebraRelacional(SqlRequest req) {
		try {
			var schema = SchemaBuilder.build(req.getTables()); // <- limpio
			var stmt = pipeline.parse(req.getSqlQuery());
			var core = pipeline.normalizeToCore(stmt, schema);
			var vr = pipeline.validate(core, schema);
			if (!vr.isValido()) {
				throw new IllegalArgumentException("Validación falló: " + vr.getMensajes());
			}
			return pipeline.render(pipeline.toAlgebra(core));
		} catch (Exception e) {
			throw new RuntimeException("Error al convertir SQL a AR: " + e.getMessage(), e);
		}
	}
}
