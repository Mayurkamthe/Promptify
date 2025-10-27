package com.promptify.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.promptify.entity.User;
import com.promptify.repo.UserRepo;
import com.promptify.services.PaymentService;
import com.razorpay.Order;
import com.razorpay.Utils;

@Controller
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepo userRepo;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpaySecret;

    @GetMapping("/buy-credits")
    public String showBuyCreditsPage(Model model, Principal principal) {
        // ✅ unwrap Optional<User>
        User user = userRepo.findByEmail(principal.getName())
                            .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("razorpayKey", razorpayKeyId);
        return "buy-credits";
    }

    @PostMapping("/create-order")
    @ResponseBody
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> data) {
        try {
            int amount = Integer.parseInt(data.get("amount").toString());
            Order order = paymentService.createOrder(amount);
            Map<String, Object> response = new HashMap<>();
            response.put("id", order.get("id"));
            response.put("amount", order.get("amount"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Error creating order");
        }
    }

    @PostMapping("/payment-success")
    @ResponseBody
    public ResponseEntity<?> handlePaymentSuccess(@RequestBody Map<String, Object> data, Principal principal) {
        try {
            String orderId = (String) data.get("razorpay_order_id");
            String paymentId = (String) data.get("razorpay_payment_id");
            String signature = (String) data.get("razorpay_signature");
            int credits = Integer.parseInt(data.get("credits").toString());

            String payload = orderId + "|" + paymentId;
            boolean isValid = Utils.verifySignature(payload, signature, razorpaySecret);

            if (isValid) {
                User user = userRepo.findByEmail(principal.getName())
                                    .orElseThrow(() -> new RuntimeException("User not found"));
                if (user.getCredits() == null)
                    user.initCredits();

                user.getCredits().addBalance(credits);
                userRepo.save(user);

                return ResponseEntity.ok("Payment verified and credits added");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Payment verification failed");
        }
    }
}
