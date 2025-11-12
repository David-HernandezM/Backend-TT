package com.ipn.escom.conversor_sql.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ipn.escom.conversor_sql.ar.ArPrinter;
import com.ipn.escom.conversor_sql.conversion.ConversionPipeline;
import com.ipn.escom.conversor_sql.conversion.CoreToAr;
import com.ipn.escom.conversor_sql.core.CoreInvariants;
import com.ipn.escom.conversor_sql.core.SchemaGuards;
import com.ipn.escom.conversor_sql.models.ArConvertResponse;
import com.ipn.escom.conversor_sql.models.SqlRequest;
import com.ipn.escom.conversor_sql.service.ArConverterService;
import com.ipn.escom.conversor_sql.validation.schema.SchemaBuilder;

@Service
public class ArConverterServiceImpl implements ArConverterService {

    private final ConversionPipeline pipeline = new ConversionPipeline();

    /** Devuelve AR + pasos. **/
    public ArConvertResponse toAlgebraRelacionalConPasos(SqlRequest req) {
        try {
            // 1) Schema
            var schema = SchemaBuilder.build(req.getTables()); // valida null/empty adentro

            // 2) Parse SQL
            var stmt  = pipeline.parse(req.getSqlQuery().trim());

            // 3) Normalizar a Core con tu pipeline
            var core  = pipeline.normalizeToCore(stmt, schema);

            // 4) Validaciones (único punto de verdad)
            var guards = new SchemaGuards(schema);
            var ci     = new CoreInvariants(guards);
            var vr     = ci.validate(core);

            if (!vr.isValido()) {
                String detalle = vr.getMensajes().stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.joining("; "));
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "Validación falló: " + detalle
                );
            }

            // 5) Core -> AR con trazas (usamos CoreToAr directamente para poder inyectar Trace)
            var coreToAr = new CoreToAr();
            var trace    = new CoreToAr.Trace();
            var arTree   = coreToAr.convert(core, trace);

            // 6) Render AR final (usa tu printer/pipeline)
            String arStr = new ArPrinter().print(arTree);

            // 7) Regresar DTO con pasos
            ArConvertResponse out = new ArConvertResponse();
            out.setAlgebraRelacional(arStr);
            out.setPasos(trace.steps());
            return out;

        } catch (ResponseStatusException e) {
            throw e; // ya mapeado a HTTP correcto
        } catch (IllegalArgumentException e) {
            // Errores de entrada/sintaxis -> 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            // Invariantes o estados internos inesperados -> 500
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        } catch (Exception e) {
            // Falla no clasificada -> 500
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al convertir SQL a AR", e);
        }
    }
}