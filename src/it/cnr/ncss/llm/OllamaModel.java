package it.cnr.ncss.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaModel {
    public String name;
    public String model;
    public String modified_at;
    public long size;
}