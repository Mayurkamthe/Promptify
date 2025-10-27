package com.promptify.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.promptify.entity.User;
import com.promptify.services.CustomUserDetails;

@Controller
public class GenratorController {
	@GetMapping("/genrate")
	public String dashboard(Model model) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

		// Get full User entity from CustomUserDetails
		User user = userDetails.getUser();

		// Add data to model
		model.addAttribute("user", user);
		return "generate"; 
	}
//	
//	@GetMapping("/buy-credits")
//	public String buyCredits(Model model) {
//		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//		CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
//
//		// Get full User entity from CustomUserDetails
//		User user = userDetails.getUser();
//
//		// Add data to model
//		model.addAttribute("user", user);
//
//		return "buy-credits"; 
//	}

}
