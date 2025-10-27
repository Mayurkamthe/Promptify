package com.promptify.services;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;

@Service
public class PaymentService {

	@Value("${razorpay.key.id}")
	private String razorpayKeyId;

	@Value("${razorpay.key.secret}")
	private String razorpaySecret;

	public Order createOrder(int amount) throws Exception {
		RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpaySecret);

		JSONObject orderRequest = new JSONObject();
		orderRequest.put("amount", amount); // in paise
		orderRequest.put("currency", "INR");
		orderRequest.put("receipt", "txn_" + System.currentTimeMillis());
		orderRequest.put("payment_capture", 1);

		return client.orders.create(orderRequest);
	}
}
