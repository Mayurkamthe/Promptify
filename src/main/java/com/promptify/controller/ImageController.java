package com.promptify.controller;

import com.promptify.entity.Credits;
import com.promptify.entity.User;
import com.promptify.repo.CreditsRepo;
import com.promptify.repo.UserRepo;
import com.promptify.services.CustomUserDetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    @Value("${clipdrop.api.key}")
    private String clipdropApiKey;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private CreditsRepo creditsRepo;

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateImage(@RequestParam String prompt) {
        try {
            // Get logged-in user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = ((CustomUserDetails) auth.getPrincipal()).getUser();

            // Get user's credits
            Credits credits = creditsRepo.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Credits not found"));

            if (credits.getBalance() <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Insufficient credits".getBytes());
            }

            // Call ClipDrop API
            HttpClient client = HttpClient.newHttpClient();
            String jsonBody = "{\"prompt\":\"" + prompt.replace("\"", "\\\"") + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://clipdrop-api.co/text-to-image/v1"))
                    .header("x-api-key", clipdropApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                // Deduct 1 credit
                credits.setBalance(credits.getBalance() - 1);
                creditsRepo.save(credits);

                return ResponseEntity.ok()
                        .header("Content-Type", "image/png")
                        .body(response.body());
            } else {
                System.out.println("ClipDrop API error: " + response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
