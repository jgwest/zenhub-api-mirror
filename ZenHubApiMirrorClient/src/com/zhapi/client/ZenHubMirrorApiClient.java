/*
 * Copyright 2019 Jonathan West
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
*/

package com.zhapi.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhapi.ApiResponse;
import com.zhapi.ZHUtil;
import com.zhapi.ZenHubApiException;

/**
 * This class stores the server API URL, and the preshared key required for
 * auth. To use this library, instantiate an instance of this class then pass
 * that to one of the (resource)Service classes.
 */
public class ZenHubMirrorApiClient {

	private final String presharedKey;
	private final String apiUrl; // Will not end with a slash, will begin with http(s)://

	private static final String HEADER_AUTHORIZATION = "Authorization";

	public ZenHubMirrorApiClient(String apiUrl, String presharedKey) {
		if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
			throw new IllegalArgumentException("API URL must begin with HTTP(S) prefix");
		}

		apiUrl = ensureDoesNotEndWithSlash(apiUrl);

		this.apiUrl = apiUrl;
		this.presharedKey = presharedKey;
	}

	public <T> ApiResponse<T> get(String apiUrl, Class<T> clazz) {

		ApiResponse<String> body = getRequest(apiUrl);

//		System.out.println(body.getResponse());
//		log.out(body.getResponse());

		ObjectMapper om = new ObjectMapper();
		try {
			T parsed = om.readValue(body.getResponse(), clazz);
			return new ApiResponse<T>(parsed, null, body.getResponse());
		} catch (Exception e) {
			throw ZenHubApiException.createFromThrowable(e);
		}

	}

	private ApiResponse<String> getRequest(String requestUrlParam) {

		requestUrlParam = ensureDoesNotBeginsWithSlash(requestUrlParam);

		HttpURLConnection httpRequest;
		try {
			httpRequest = createConnection(this.apiUrl + "/" + requestUrlParam, "GET", presharedKey);
			final int code = httpRequest.getResponseCode();

			InputStream is = httpRequest.getInputStream();

			String body = getBody(is);

			if (code != 200) {
				throw new ZenHubApiException("Request failed - HTTP Code: " + code + "  body: " + body);
			}

			return new ApiResponse<String>(body, null, body);

		} catch (IOException e) {
			throw ZenHubApiException.createFromThrowable(e);
		}

	}

	private static HttpURLConnection createConnection(String uri, String method, String authorization)
			throws IOException {

		URL url = new URL(uri);
		HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
		if (httpConnection instanceof HttpsURLConnection) {
			HttpsURLConnection connection = (HttpsURLConnection) httpConnection;
			connection.setSSLSocketFactory(generateSslContext().getSocketFactory());
			connection.setHostnameVerifier((a, b) -> true);
			connection.setRequestMethod(method);
		}

		if (authorization != null) {
			httpConnection.setRequestProperty(HEADER_AUTHORIZATION, authorization);
		}

		return httpConnection;
	}

	private static String getBody(InputStream is) {
		StringBuilder sb = new StringBuilder();
		int c;
		byte[] barr = new byte[1024 * 64];
		try {
			while (-1 != (c = is.read(barr))) {
				sb.append(new String(barr, 0, c));
			}
		} catch (IOException e) {
			throw ZenHubApiException.createFromThrowable(e);
		}
		return sb.toString();
	}

	private static SSLContext generateSslContext() {
		SSLContext sslContext = null;
		try {
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] { new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
				}

				public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
				}

				public X509Certificate[] getAcceptedIssuers() {

					return new X509Certificate[0];
				}

			} }, new java.security.SecureRandom());
		} catch (Exception e) {
			ZHUtil.throwAsUnchecked(e);
		}

		return sslContext;
	}

	private static String ensureDoesNotBeginsWithSlash(String input) {
		while (input.startsWith("/")) {
			input = input.substring(1);
		}

		return input;
	}

	@SuppressWarnings("unused")
	private static String ensureEndsWithSlash(String input) {
		if (!input.endsWith("/")) {
			input = input + "/";
		}
		return input;
	}

	private static String ensureDoesNotEndWithSlash(String input) {
		while (input.endsWith("/")) {
			input = input.substring(0, input.length() - 1);
		}

		return input;

	}

}
