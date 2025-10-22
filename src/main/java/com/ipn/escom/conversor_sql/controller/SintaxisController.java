package com.ipn.escom.conversor_sql.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ipn.escom.conversor_sql.models.ArConvertResponse;
import com.ipn.escom.conversor_sql.models.SqlRequest;
import com.ipn.escom.conversor_sql.models.TipoDetallado;
import com.ipn.escom.conversor_sql.service.ArConverterService;
import com.ipn.escom.conversor_sql.utils.ValidationProcessor;
import com.ipn.escom.conversor_sql.validation.SqlValidator;
import com.ipn.escom.conversor_sql.validation.ValidationResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class SintaxisController {

    private static final Logger logger = LoggerFactory.getLogger(SintaxisController.class);
    
    @Autowired
    private ArConverterService arConverterService;

    @PostMapping("/sintaxis")
    public ResponseEntity<?> sintaxisSQL(@Valid @RequestBody SqlRequest request) {
        logger.info("Validando SQL: {}", request.getSqlQuery());

        ValidationResult validacion = SqlValidator.validar(request);
        ValidationProcessor.procesarMensajes(validacion);

        if (!validacion.isValido()) {
            logger.warn("Validación fallida: {}", validacion);
            return ResponseEntity.badRequest().body(validacion);
        }

        ValidationResult ok = new ValidationResult();
        ok.agregarMensaje("exito", TipoDetallado.VALIDACION_EXITOSA, "Consulta válida.");
        logger.info("Validación exitosa");
        return ResponseEntity.ok(ok);
    }
    
    @PostMapping("/convert")
    public ResponseEntity<ArConvertResponse> convert(@Valid @RequestBody SqlRequest request) {
        if (request.getTables() == null || request.getTables().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(new ArConvertResponse("Falta el esquema (tables[]) para convertir a AR."));
        }
        
        String ar = arConverterService.toAlgebraRelacional(request);
        
        return ResponseEntity.ok(new ArConvertResponse(ar));
    }

}