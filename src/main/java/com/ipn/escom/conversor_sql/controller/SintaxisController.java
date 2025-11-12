package com.ipn.escom.conversor_sql.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

	@PostMapping(value = "/convert", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> convert(@Valid @RequestBody SqlRequest request) {	
		try {
			ArConvertResponse out = arConverterService.toAlgebraRelacionalConPasos(request);
			return ResponseEntity.ok(out); // { algebraRelacional, pasos }
		} catch (IllegalArgumentException | UnsupportedOperationException e) {
			logger.warn("Entrada fuera de reglas. sqlQuery='{}'", safeSql(request, 200), e);
			return ResponseEntity.badRequest().body(error("ERROR_LOGICO", e.getMessage()));
		} catch (Exception e) {
			logger.error("Error inesperado al convertir. sqlQuery='{}'", safeSql(request, 200), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(error("ERROR_INTERNO", "Error al convertir SQL a AR"));
		}
	}

	private static String safeSql(SqlRequest r, int maxLen) {
		try {
			String s = r.getSqlQuery();
			if (s == null)
				return "<null>";
			if (s.length() <= maxLen)
				return s;
			return s.substring(0, maxLen) + "…";
		} catch (Exception ignore) {
			return "<unavailable>";
		}
	}

	private static ErrorResponse error(String tipoDetallado, String contenido) {
		return new ErrorResponse(false, java.util.List.of(new ErrorMessage("error", tipoDetallado, contenido)));
	}

	public record ErrorResponse(boolean valido, java.util.List<ErrorMessage> mensajes) {
	}

	public record ErrorMessage(String tipo, String tipoDetallado, String contenido) {
	}

}