package com.ipn.escom.conversor_sql.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ipn.escom.conversor_sql.models.ValidationMessage;
import com.ipn.escom.conversor_sql.validation.ValidationResult;

public class ValidationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);

    private ValidationProcessor() {
        // Evitar instanciación
    }

    public static void procesarMensajes(ValidationResult result) {
        if (result == null || result.getMensajes() == null || result.getMensajes().isEmpty()) {
            logger.info("[INFO] No hay mensajes para procesar.");
            return;
        }

        for (ValidationMessage mensaje : result.getMensajes()) {
            switch (mensaje.getTipoDetallado()) {
                case ERROR_SINTAXIS:
                    logger.error("[❌ ERROR DE SINTAXIS] -> {}", mensaje.getContenido());
                    break;
                case ERROR_LOGICO:
                    logger.error("[⚠️ ERROR LÓGICO] -> {}", mensaje.getContenido());
                    break;
                case ADVERTENCIA:
                    logger.warn("[⚠️ ADVERTENCIA] -> {}", mensaje.getContenido());
                    break;
                case VALIDACION_EXITOSA:
                    logger.info("[✅ VALIDACIÓN EXITOSA] -> {}", mensaje.getContenido());
                    break;
                default:
                    logger.warn("[❓ TIPO DESCONOCIDO] -> {}", mensaje.getContenido());
                    break;
            }
        }
    }
}