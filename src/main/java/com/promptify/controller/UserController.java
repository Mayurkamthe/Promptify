package com.promptify.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.promptify.entity.Credits;
import com.promptify.entity.User;
import com.promptify.repo.UserRepo;

@Controller
public class UserController {

    @Autowired
    private UserRepo userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder; 

    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("user", new User());
        return "signup";
    }

    @PostMapping("/signup")
    public String signupSubmit(@ModelAttribute("user") User user) {
        
        Credits credits = new Credits();
        credits.setBalance(5);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        credits.setUser(user);

        user.setCredits(credits);
        userRepository.save(user);

        return "redirect:/login";
    }
}
