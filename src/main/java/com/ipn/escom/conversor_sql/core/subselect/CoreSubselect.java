package com.ipn.escom.conversor_sql.core.subselect;

import com.ipn.escom.conversor_sql.core.predicados.CorePred;
import com.ipn.escom.conversor_sql.core.proyeccion.CoreProjItem;
import com.ipn.escom.conversor_sql.core.relacionales.CoreRel;

//En la gramática: "(" SELECT <proyeccion_1col> FROM <fuentes> [WHERE <bool_nosub>] ")"
public record CoreSubselect(CoreRel fromTree, CoreProjItem oneProjected, CorePred whereOrNull) {}