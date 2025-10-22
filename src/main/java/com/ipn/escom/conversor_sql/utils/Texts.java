package com.ipn.escom.conversor_sql.utils;

import java.util.Locale;

public class Texts {
	public static boolean isBlank(String text) {
		return text == null || text.trim().isEmpty();
	}

	public static String toLowerTrimmed(String text) {
		return (text == null) ? null : text.toLowerCase(Locale.ROOT).trim();
	}
}
