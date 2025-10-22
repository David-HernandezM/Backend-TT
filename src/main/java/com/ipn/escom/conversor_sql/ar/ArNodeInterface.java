package com.ipn.escom.conversor_sql.ar;

/** ÚNICO tipo public del archivo */
public sealed interface ArNodeInterface
  permits ArTable, ArRename, ArProject, ArSelect, ArJoin, ArCross,
          ArUnion, ArIntersect, ArMinus, ArSemiJoin {}