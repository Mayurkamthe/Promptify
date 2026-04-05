package com.promptify.controller;

import com.promptify.entity.Credits;
import com.promptify.entity.User;
import com.promptify.repo.CreditsRepo;
import com.promptify.repo.UserRepo;
import com.promptify.services.CustomUserDetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    @Value("${clipdrop.api.key}")
    private String clipdropApiKey;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private CreditsRepo creditsRepo;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private User getAuthUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((CustomUserDetails) auth.getPrincipal()).getUser();
    }

    private Credits getCredits(User user) {
        return creditsRepo.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Credits not found"));
    }

    private ResponseEntity<byte[]> insufficientCredits() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Insufficient credits. Please buy more credits to continue.".getBytes());
    }

    private void deductCredit(Credits credits) {
        credits.setBalance(credits.getBalance() - 1);
        creditsRepo.save(credits);
    }

    // ─── Text to Image ───────────────────────────────────────────────────────

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateImage(@RequestParam String prompt) {
        try {
            User user = getAuthUser();
            Credits credits = getCredits(user);
            if (credits.getBalance() <= 0) return insufficientCredits();

            HttpClient client = HttpClient.newHttpClient();
            String jsonBody = "{\"prompt\":\"" + prompt.replace("\"", "\\\"") + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://clipdrop-api.co/text-to-image/v1"))
                    .header("x-api-key", clipdropApiKey)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                deductCredit(credits);
                return ResponseEntity.ok()
                        .header("Content-Type", "image/png")
                        .body(response.body());
            } else {
                System.out.println("ClipDrop text-to-image error: " + response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ─── Remove Background ───────────────────────────────────────────────────

    @PostMapping("/remove-background")
    public ResponseEntity<byte[]> removeBackground(@RequestParam("image_file") MultipartFile imageFile) {
        try {
            User user = getAuthUser();
            Credits credits = getCredits(user);
            if (credits.getBalance() <= 0) return insufficientCredits();

            String boundary = "----FormBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String crlf = "\r\n";

            // image_file part
            baos.write(("--" + boundary + crlf).getBytes());
            baos.write(("Content-Disposition: form-data; name=\"image_file\"; filename=\"" + imageFile.getOriginalFilename() + "\"" + crlf).getBytes());
            baos.write(("Content-Type: " + imageFile.getContentType() + crlf + crlf).getBytes());
            baos.write(imageFile.getBytes());
            baos.write(crlf.getBytes());
            baos.write(("--" + boundary + "--" + crlf).getBytes());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://clipdrop-api.co/remove-background/v1"))
                    .header("x-api-key", clipdropApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(BodyPublishers.ofByteArray(baos.toByteArray()))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                deductCredit(credits);
                return ResponseEntity.ok()
                        .header("Content-Type", "image/png")
                        .body(response.body());
            } else {
                System.out.println("ClipDrop remove-bg error: " + response.statusCode() + " " + new String(response.body()));
                return ResponseEntity.status(response.statusCode()).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ─── Replace Background ──────────────────────────────────────────────────

    @PostMapping("/replace-background")
    public ResponseEntity<byte[]> replaceBackground(
            @RequestParam("image_file") MultipartFile imageFile,
            @RequestParam(value = "bg_image_file", required = false) MultipartFile bgImageFile,
            @RequestParam(value = "bg_prompt", required = false) String bgPrompt) {
        try {
            User user = getAuthUser();
            Credits credits = getCredits(user);
            if (credits.getBalance() <= 0) return insufficientCredits();

            String boundary = "----FormBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String crlf = "\r\n";

            // image_file part
            baos.write(("--" + boundary + crlf).getBytes());
            baos.write(("Content-Disposition: form-data; name=\"image_file\"; filename=\"" + imageFile.getOriginalFilename() + "\"" + crlf).getBytes());
            baos.write(("Content-Type: " + imageFile.getContentType() + crlf + crlf).getBytes());
            baos.write(imageFile.getBytes());
            baos.write(crlf.getBytes());

            // bg_image_file or bg_prompt
            if (bgImageFile != null && !bgImageFile.isEmpty()) {
                baos.write(("--" + boundary + crlf).getBytes());
                baos.write(("Content-Disposition: form-data; name=\"bg_image_file\"; filename=\"" + bgImageFile.getOriginalFilename() + "\"" + crlf).getBytes());
                baos.write(("Content-Type: " + bgImageFile.getContentType() + crlf + crlf).getBytes());
                baos.write(bgImageFile.getBytes());
                baos.write(crlf.getBytes());
            } else if (bgPrompt != null && !bgPrompt.isBlank()) {
                baos.write(("--" + boundary + crlf).getBytes());
                baos.write(("Content-Disposition: form-data; name=\"bg_prompt\"" + crlf + crlf).getBytes());
                baos.write(bgPrompt.getBytes());
                baos.write(crlf.getBytes());
            }

            baos.write(("--" + boundary + "--" + crlf).getBytes());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://clipdrop-api.co/replace-background/v1"))
                    .header("x-api-key", clipdropApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(BodyPublishers.ofByteArray(baos.toByteArray()))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                deductCredit(credits);
                return ResponseEntity.ok()
                        .header("Content-Type", "image/png")
                        .body(response.body());
            } else {
                System.out.println("ClipDrop replace-bg error: " + response.statusCode() + " " + new String(response.body()));
                return ResponseEntity.status(response.statusCode()).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ─── Sketch to Image ────────────────────────────────────────────────────

    @PostMapping("/sketch-to-image")
    public ResponseEntity<byte[]> sketchToImage(
            @RequestParam("sketch_file") MultipartFile sketchFile,
            @RequestParam("prompt") String prompt) {
        try {
            User user = getAuthUser();
            Credits credits = getCredits(user);
            if (credits.getBalance() <= 0) return insufficientCredits();

            String boundary = "----FormBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String crlf = "\r\n";

            // sketch_file
            baos.write(("--" + boundary + crlf).getBytes());
            baos.write(("Content-Disposition: form-data; name=\"sketch_file\"; filename=\"" + sketchFile.getOriginalFilename() + "\"" + crlf).getBytes());
            baos.write(("Content-Type: " + sketchFile.getContentType() + crlf + crlf).getBytes());
            baos.write(sketchFile.getBytes());
            baos.write(crlf.getBytes());

            // prompt
            baos.write(("--" + boundary + crlf).getBytes());
            baos.write(("Content-Disposition: form-data; name=\"prompt\"" + crlf + crlf).getBytes());
            baos.write(prompt.getBytes());
            baos.write(crlf.getBytes());

            baos.write(("--" + boundary + "--" + crlf).getBytes());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://clipdrop-api.co/sketch-to-image/v1/sketch-to-image"))
                    .header("x-api-key", clipdropApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(BodyPublishers.ofByteArray(baos.toByteArray()))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                deductCredit(credits);
                return ResponseEntity.ok()
                        .header("Content-Type", "image/png")
                        .body(response.body());
            } else {
                System.out.println("ClipDrop sketch-to-image error: " + response.statusCode() + " " + new String(response.body()));
                return ResponseEntity.status(response.statusCode()).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
