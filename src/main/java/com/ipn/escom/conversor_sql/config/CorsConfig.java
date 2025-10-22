package com.ipn.escom.conversor_sql.config;

import org.springframework.context.annotation.Configuration;

import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
      .allowedOriginPatterns("*")                   // usa patterns en lugar de origins para permitir wildcard
      .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
      .allowedHeaders("*")
      .maxAge(3600)
      .allowCredentials(false);                     // con "*" NO puedes usar credenciales
  }
}