package com.ipn.escom.conversor_sql.models;

import java.util.List;

import lombok.Data;

@Data
public class ArConvertResponse {
    private String algebraRelacional;
    private List<String> pasos;
}