package com.ipn.escom.conversor_sql.core;

public record CoreRename(CoreNodeInterface input, String newName) implements CoreNodeInterface {}