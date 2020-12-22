package com.humio.rdns;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Utils {
    public static String basicAuthHeader(String username, String password) {
        byte[] authBytes = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(authBytes);
    }
}
