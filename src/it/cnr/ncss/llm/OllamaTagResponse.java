package it.cnr.ncss.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaTagResponse {
    public List<OllamaModel> models;
}
