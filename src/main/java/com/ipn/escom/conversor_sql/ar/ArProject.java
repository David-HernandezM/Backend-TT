package com.ipn.escom.conversor_sql.ar;

import java.util.List;

public record ArProject(ArNodeInterface input, List<ArAttr> attrs) implements ArNodeInterface {}