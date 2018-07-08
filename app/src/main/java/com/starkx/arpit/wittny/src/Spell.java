package com.starkx.arpit.wittny.src;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.starkx.arpit.wittny.utils.UrlUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static com.starkx.arpit.wittny.src.Api.GINGER_API_KEY;

/**
 * Created by Home Laptop on 20-Jan-18.
 */

public class Spell {
	int Count;
	private Callback callback;
	private final String url = "http://services.gingersoftware.com/Ginger/correct/json/GingerTheText";

	public Spell(Callback callback) {
		this.callback = callback;
	}

	public void convert(Context context, final String sourceText) {
		if (sourceText.isEmpty()) {
			return;
		}
		final String[] sColl = sourceText.split("\n");
		RequestQueue queue = Volley.newRequestQueue(context);
		Count = 0;

		for (int x = 0; x < sColl.length; x++) {
			final int index = x;
			final String sentence = sColl[index];
			Map<String, String> params = new HashMap<>();
			params.put("apiKey", GINGER_API_KEY);
			params.put("lang", "US");
			params.put("clientVersion", "2.0");
			params.put("text", sentence);
			String reqUrl = UrlUtils.getRequest(url, params);

			JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
					reqUrl, null,
					new Response.Listener<JSONObject>() {
						@Override
						public void onResponse(JSONObject response) {
							try {
								if (!response.has("LightGingerTheTextResult")) {
									return;
								}
								String correct = sentence;
								int gap = 0;
								JSONArray results = response.getJSONArray("LightGingerTheTextResult");
								for (int x = 0; x < results.length(); x++) {
									JSONObject result = results.getJSONObject(x);
									if (result.has("Suggestions")) {
										int from = result.getInt("From");
										int to = result.getInt("To") + 1;
										String suggest = result.getJSONArray("Suggestions").getJSONObject(0).getString("Text");
										correct = correct.substring(0, from - gap) + suggest + correct.substring(to - gap);
										gap += to - from - suggest.length();
									}
								}
								sColl[index] = correct;
							}
							catch (Exception e) {
								e.printStackTrace();
							}
							finally {
								Count++;
								if (Count == sColl.length) {
									if (sColl.length == 0) {
										callback.onResponse("");
									}
									else {
										StringBuilder builder = new StringBuilder(sColl[0]);
										for (int x = 1; x < sColl.length; x++) {
											builder.append("\n").append(sColl[x]);
										}
										Log.d("data", builder.toString());
										callback.onResponse(builder.toString());
									}
								}
							}
						}
					}, new Response.ErrorListener() {

				@Override
				public void onErrorResponse(VolleyError error) {
					error.printStackTrace();
				}
			});
			queue.add(jsonObjReq);
		}
	}

	public interface Callback {
		void onResponse(String response);
	}
}
