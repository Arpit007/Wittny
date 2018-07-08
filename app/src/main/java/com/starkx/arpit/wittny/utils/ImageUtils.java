package com.starkx.arpit.wittny.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;

import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by Home Laptop on 20-Jan-18.
 */

public class ImageUtils {

	public static Bitmap getBitmap(Context context, Uri uri) throws IOException{
		return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
	}

	public static Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

		int originalWidth = bitmap.getWidth();
		int originalHeight = bitmap.getHeight();
		int resizedWidth = maxDimension;
		int resizedHeight = maxDimension;

		if (originalHeight > originalWidth) {
			resizedHeight = maxDimension;
			resizedWidth = (int) ( resizedHeight * (float) originalWidth / (float) originalHeight );
		}
		else if (originalWidth > originalHeight) {
			resizedWidth = maxDimension;
			resizedHeight = (int) ( resizedWidth * (float) originalHeight / (float) originalWidth );
		}
		else if (originalHeight == originalWidth) {
			resizedHeight = maxDimension;
			resizedWidth = maxDimension;
		}
		return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
	}

	@NonNull
	public static Image getImageEncodeImage(Bitmap bitmap) {
		Image base64EncodedImage = new Image();
		// Convert the bitmap to a JPEG
		// Just in case it's a format that Android understands but Cloud Vision
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
		byte[] imageBytes = byteArrayOutputStream.toByteArray();

		// Base64 encode the JPEG
		base64EncodedImage.encodeContent(imageBytes);
		return base64EncodedImage;
	}
}
