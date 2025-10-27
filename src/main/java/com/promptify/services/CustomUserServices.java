package com.promptify.services;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.promptify.entity.User;
import com.promptify.repo.UserRepo;

@Service
public class CustomUserServices implements UserDetailsService {

	private final UserRepo userRepository;

	// Constructor injection (Spring will automatically inject the repository)
	public CustomUserServices(UserRepo userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

		return new CustomUserDetails(user); // return custom UserDetails
	}
}
