package com.ipn.escom.conversor_sql.ar.relacionales;

import com.ipn.escom.conversor_sql.ar.proyeccion.ArProjItem;

//Proyección π  (AR espera que * y t.* ya estén expandidos)
public record ArProject(ArRel input, java.util.List<ArProjItem> items) implements ArRel {}