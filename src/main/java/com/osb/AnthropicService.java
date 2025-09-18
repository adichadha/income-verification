package com.osb;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnthropicService {

    private final AnthropicConfig config;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MessageResponse verify(MultipartFile payslip, MultipartFile bankStatement) throws IOException {
        String fileId1 = uploadFile(payslip);
        String fileId2 = uploadFile(bankStatement);
        return sendMessage(List.of(fileId1, fileId2));
    }

    private String uploadFile(MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("x-api-key", config.getApiKey());
        headers.set("anthropic-version", "2023-06-01");
        headers.set("anthropic-beta", "files-api-2025-04-14");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(config.getBaseUrl() + "/v1/files", request, String.class);

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.get("id").asText();
    }

    private MessageResponse sendMessage(List<String> fileIds) throws IOException {
        String url = "https://api.anthropic.com/v1/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", config.getApiKey());
        headers.set("anthropic-version", "2023-06-01");
        headers.set("anthropic-beta", "files-api-2025-04-14");


        // Build content array with text + file_ids
        StringBuilder contentArray = new StringBuilder();
        contentArray.append("""
        [
          {
            "type": "text",
            "text": "You are an income verification assistant. Based on the uploaded documents (payslips and bank statements), extract the income details (monthly/annual), check document authenticity, detect potential fraud, and assign a confidence score for underwriting. Return a JSON with the following keys: documentSummary, extractedIncome, authenticityCheck, fraudIndicators, confidenceScore."
          }
        """);

        // Add each document file
        for (String fileId : fileIds) {
            contentArray.append(String.format("""
          ,
          {
            "type": "document",
            "source": {
              "type": "file",
              "file_id": "%s"
            }
          }""", fileId));
        }

        contentArray.append("\n]");

        String payload = String.format("""
        {
          "model": "claude-sonnet-4-20250514",
          "max_tokens": 1024,
          "messages": [
            {
              "role": "user",
              "content": %s
            }
          ]
        }
        """, contentArray);

        HttpEntity<String> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return new MessageResponse(jsonNode.toPrettyString());
    }
}