package com.osb;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

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
    ResponseEntity<String> response = restTemplate.postForEntity(config.getBaseUrl() + "/v1/files", request,
        String.class);

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
    contentArray.append(
        """
            [
              {
                "type": "text",
                "text": "You are an income verification assistant. Based on the uploaded documents (payslips and bank statements), extract the income details (monthly/annual), check document authenticity, detect potential fraud, and assign a confidence score for underwriting. Return a JSON with the following keys: documentSummary, extractedIncome, authenticityCheck, fraudIndicators, confidenceScore. Make sure the json is in this format {\\\"documentSummary\\\": {\\\"documentsProvided\\\": [{\\\"type\\\": \\\"payslip\\\",\\\"employer\\\": \\\"string\\\",\\\"employee\\\": \\\"string\\\",\\\"payDate\\\": \\\"string\\\",\\\"payPeriod\\\": \\\"string\\\"},{\\\"type\\\": \\\"bank_statement\\\",\\\"bank\\\": \\\"string\\\",\\\"accountHolder\\\": \\\"string\\\",\\\"statementDate\\\": \\\"string\\\",\\\"accountNumber\\\": \\\"string\\\"}],\\\"addressConsistency\\\": \\\"string\\\"},\\\"extractedIncome\\\": {\\\"monthlyGrossIncome\\\": 0,\\\"monthlyNetIncome\\\": 0,\\\"annualizedGrossIncome\\\": 0,\\\"ytdGrossIncome\\\": 0,\\\"ytdNetIncome\\\": 0,\\\"payFrequency\\\": \\\"string\\\",\\\"bankDepositVerification\\\": {\\\"payslipNetPay\\\": 0,\\\"bankStatementDeposit\\\": 0,\\\"match\\\": true}},\\\"authenticityCheck\\\": {\\\"payslipAuthenticity\\\": {\\\"formatConsistency\\\": \\\"string\\\",\\\"calculationAccuracy\\\": \\\"string\\\"},\\\"bankStatementAuthenticity\\\": {\\\"formatConsistency\\\": \\\"string\\\",\\\"officialBranding\\\": \\\"string\\\"},\\\"crossVerification\\\": {\\\"salaryDeposit\\\": \\\"string\\\",\\\"timingConsistency\\\": \\\"string\\\",\\\"nameConsistency\\\": \\\"string\\\"}},\\\"fraudIndicators\\\": {\\\"suspiciousElements\\\": [{\\\"indicator\\\": \\\"string\\\",\\\"severity\\\": \\\"string\\\"}],\\\"positiveIndicators\\\": [{\\\"indicator\\\": \\\"string\\\"}],\\\"riskLevel\\\": \\\"string\\\"},\\\"confidenceScore\\\": {\\\"overall\\\": 0,\\\"breakdown\\\": {\\\"payStubAuthenticity\\\": 0,\\\"bankStatementAuthenticity\\\": 0,\\\"bankDepositsVsClaimedIncome\\\": 0,\\\"incomeToExpenseRatio\\\": 0,\\\"employerLegitimacy\\\": 0,\\\"employerStability\\\": 0,\\\"jobTitleToPayConsistency\\\": 0,\\\"anomalousBankTransactionPatterns\\\": 0,\\\"incomeVerification\\\": 0,\\\"crossVerification\\\": 0,\\\"fraudRisk\\\": 0},\\\"recommendation\\\": \\\"APPROVED | REJECTED | CONDITIONALLY APPROVED | CONDITIONALLY REJECTED\\\",\\\"rejectionReason\\\": string, \\\"conditionalReason\\\": string, \\\"detailedReasons\\\": [A list of reasons for deducted scores.]}}"
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
          "max_tokens": 2048,
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