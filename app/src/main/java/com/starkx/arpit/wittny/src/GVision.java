package com.starkx.arpit.wittny.src;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.Feature;
import com.starkx.arpit.wittny.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.starkx.arpit.wittny.src.Api.API_KEY;
import static com.starkx.arpit.wittny.utils.ImageUtils.getBitmap;
import static com.starkx.arpit.wittny.utils.ImageUtils.getImageEncodeImage;
import static com.starkx.arpit.wittny.utils.ImageUtils.scaleBitmapDown;

/**
 * Created by Home Laptop on 20-Jan-18.
 */

public class GVision {
	private static final String TAG = GVision.class.getSimpleName();
	private Callback callback;

	public GVision(@NonNull Callback callback) {
		this.callback = callback;
	}

	public void convert(Context context, Uri uri) {
		if (uri != null) {
			try {
				Bitmap bitmap = scaleBitmapDown(getBitmap(context, uri), 1200);
				callCloudVision(bitmap);
			}
			catch (IOException e) {
				Log.d(TAG, "Image picking failed because " + e.getMessage());
				Toast.makeText(context, R.string.image_picker_error, Toast.LENGTH_LONG).show();
			}
		}
		else {
			Log.d(TAG, "Image picker gave us a null image.");
			Toast.makeText(context, R.string.image_picker_error, Toast.LENGTH_LONG).show();
		}
	}

	private void callCloudVision(final Bitmap bitmap) throws IOException {
		final List<Feature> featureList = new ArrayList<>();
		featureList.add(getFeature());

		final List<AnnotateImageRequest> annotateImageRequests = new ArrayList<>();

		AnnotateImageRequest annotateImageReq = new AnnotateImageRequest();
		annotateImageReq.setFeatures(featureList);
		annotateImageReq.setImage(getImageEncodeImage(bitmap));
		annotateImageRequests.add(annotateImageReq);


		new AsyncTask<Object, Void, String>() {
			@Override
			protected String doInBackground(Object... params) {
				try {

					HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
					JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

					VisionRequestInitializer requestInitializer = new VisionRequestInitializer(API_KEY);

					Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
					builder.setVisionRequestInitializer(requestInitializer);

					Vision vision = builder.build();

					BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
					batchAnnotateImagesRequest.setRequests(annotateImageRequests);

					Vision.Images.Annotate annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);
					annotateRequest.setDisableGZipContent(true);
					BatchAnnotateImagesResponse response = annotateRequest.execute();
					return convertResponseToString(response);
				}
				catch (GoogleJsonResponseException e) {
					Log.d(TAG, "failed to make API request because " + e.getContent());
				}
				catch (IOException e) {
					Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
				}
				return "Cloud Vision API request failed. Check logs for details.";
			}

			protected void onPostExecute(String result) {
				Log.d(TAG, result);
				callback.onResponse(result);
			}
		}.execute();
	}


	private Feature getFeature() {
		Feature feature = new Feature();
		feature.setType("DOCUMENT_TEXT_DETECTION");
		return feature;
	}

	private String convertResponseToString(BatchAnnotateImagesResponse response) {
		return response.getResponses().get(0).getTextAnnotations().get(0).getDescription();
	}

	public interface Callback {
		void onResponse(String response);
	}
}
