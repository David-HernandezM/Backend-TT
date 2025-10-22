package com.ipn.escom.conversor_sql.core;

import java.util.List;

public record CoreProject(CoreNodeInterface input, List<CoreAttr> attrs) implements CoreNodeInterface {}