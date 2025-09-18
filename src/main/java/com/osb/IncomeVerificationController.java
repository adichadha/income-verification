package com.osb;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/verify")
@RequiredArgsConstructor
public class IncomeVerificationController {

    private final AnthropicService service;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<MessageResponse> verify(@RequestParam("payslip") MultipartFile payslip,
                                                  @RequestParam("bankStatement") MultipartFile bankStatement) {
        try {
            MessageResponse response = service.verify(payslip, bankStatement);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new MessageResponse("Error: " + e.getMessage()));
        }
    }
}