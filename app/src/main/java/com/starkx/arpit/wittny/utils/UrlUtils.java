package com.starkx.arpit.wittny.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Created by Home Laptop on 20-Jan-18.
 */

public class UrlUtils {
	public static String getRequest(String baseUrl, Map<String, String> params) {
		StringBuilder builder = new StringBuilder();

		for (String key : params.keySet()) {
			Object value = params.get(key);
			if (value != null) {
				try {
					value = URLEncoder.encode(String.valueOf(value), "UTF-8");
					if (builder.length() > 0) {
						builder.append("&");
					}
					builder.append(key).append("=").append(value);
				}
				catch (UnsupportedEncodingException e) {
				}
			}
		}

		baseUrl += "?" + builder.toString();
		return baseUrl;
	}
}
