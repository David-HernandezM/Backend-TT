package com.ipn.escom.conversor_sql.models;

public class ArConvertResponse {
    private String algebraRelacional;

    public ArConvertResponse() { }
    public ArConvertResponse(String algebraRelacional) {
        this.algebraRelacional = algebraRelacional;
    }

    public String getAlgebraRelacional() { return algebraRelacional; }
    public void setAlgebraRelacional(String algebraRelacional) { this.algebraRelacional = algebraRelacional; }
}