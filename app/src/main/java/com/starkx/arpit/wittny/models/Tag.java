package com.starkx.arpit.wittny.models;

import com.simperium.client.Bucket;
import com.simperium.client.BucketObject;
import com.simperium.client.BucketSchema;
import com.simperium.client.Query;

import org.json.JSONObject;

public class Tag extends BucketObject {

	public static final String BUCKET_NAME = "tag";
	public static final String NOTE_COUNT_INDEX_NAME = "note_count";
	public static final String INDEX_PROPERTY = "index";
	protected String name = "";

	public Tag(String key, JSONObject properties) {
		super(key, properties);
	}

	public static Query<Tag> all(Bucket<Tag> bucket) {
		return bucket.query().order(INDEX_PROPERTY).orderByKey();
	}

	public static class Schema extends BucketSchema<Tag> {

		public Schema() {
			autoIndex();
		}

		public String getRemoteName() {
			return Tag.BUCKET_NAME;
		}

		public Tag build(String key, JSONObject properties) {
			return new Tag(key, properties);
		}

		public void update(Tag tag, JSONObject properties) {
			tag.setProperties(properties);
		}

	}
}