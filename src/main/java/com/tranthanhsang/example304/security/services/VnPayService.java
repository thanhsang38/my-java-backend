package com.tranthanhsang.example304.security.services;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VnPayService {

    private final String vnp_TmnCode = "0WJ3Y7PK"; // Mã website
    private final String vnp_HashSecret = "I1LO0F1WPJY2G80MY1O0G2U13CAUGL6P"; // Secret
    private final String vnp_Url = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"; // TEST URL
    private final String vnp_ReturnUrl = "https://sang-test-cafe-nam.com/api/bills/vnpay-return";

    // ===== TẠO URL THANH TOÁN =====
    public String createPayment(Long orderId, Long amount) {
        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // VND * 100
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", String.valueOf(orderId));
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don " + orderId);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", "203.0.113.45"); // IP khách hoặc server

        // Thời gian tạo đơn & hết hạn
        String createDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        vnp_Params.put("vnp_CreateDate", createDate);

        Calendar expire = Calendar.getInstance();
        expire.add(Calendar.MINUTE, 15);
        String expireDate = new SimpleDateFormat("yyyyMMddHHmmss").format(expire.getTime());
        vnp_Params.put("vnp_ExpireDate", expireDate);

        // Thêm kiểu hash
        vnp_Params.put("vnp_SecureHashType", "SHA512");

        // 1. Tạo chuỗi hash (alphabetical order, không encode, không gồm
        // vnp_SecureHash)
        String hashData = buildDataToHash(vnp_Params);

        // 2. Tính hash SHA512
        String vnp_SecureHash = hmacSHA512(vnp_HashSecret, hashData);

        // 3. Tạo query string URL-encoded
        String queryString = buildQueryString(vnp_Params);

        String paymentUrl = vnp_Url + "?" + queryString + "&vnp_SecureHash=" + vnp_SecureHash;

        // DEBUG
        System.err.println("--- VNPAY HASH DEBUG ---");
        System.err.println("RAW DATA (chuỗi hash): " + hashData);
        System.err.println("SECURE HASH: " + vnp_SecureHash);
        System.err.println("FINAL URL: " + paymentUrl);
        System.err.println("------------------------");

        return paymentUrl;
    }

    // ===== XÁC THỰC CALLBACK =====
    public boolean validateHash(Map<String, String> params) {
        try {
            String vnp_SecureHash = params.get("vnp_SecureHash");
            if (vnp_SecureHash == null || vnp_SecureHash.isEmpty())
                return false;

            // Loại bỏ vnp_SecureHash và vnp_SecureHashType
            Map<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null && !value.isEmpty() &&
                        !key.equals("vnp_SecureHash") &&
                        !key.equals("vnp_SecureHashType")) {
                    sorted.put(key, value);
                }
            }

            // Tạo chuỗi hash
            String hashData = buildDataToHash(sorted);

            // Tính hash SHA512
            String calculatedHash = hmacSHA512(vnp_HashSecret, hashData);

            // So sánh ignore case
            boolean isValid = calculatedHash.equalsIgnoreCase(vnp_SecureHash);

            // DEBUG
            System.err.println("--- VNPAY CALLBACK VALIDATE ---");
            System.err.println("RAW DATA: " + hashData);
            System.err.println("RECEIVED HASH: " + vnp_SecureHash);
            System.err.println("CALCULATED HASH: " + calculatedHash);
            System.err.println("VALID: " + isValid);
            System.err.println("-------------------------------");

            return isValid;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===== HÀM HỖ TRỢ =====
    private String buildDataToHash(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isEmpty() && !key.equals("vnp_SecureHash")) {
                if (sb.length() > 0)
                    sb.append('&');
                sb.append(key).append('=').append(value);
            }
        }
        return sb.toString();
    }

    private String buildQueryString(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null && !value.isEmpty()) {
                    if (sb.length() > 0)
                        sb.append('&');
                    sb.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8.toString()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] hashBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo HMAC SHA512", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1)
                hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }
}
