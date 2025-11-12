package com.ipn.escom.conversor_sql.core.relacionales;

import java.util.List;

import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjItem;

public record CoreProject(CoreRel input, List<CoreProjItem> items) implements CoreRel {}