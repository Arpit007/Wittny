package com.starkx.arpit.wittny.models;

import com.simperium.client.FullTextIndex;

import java.util.HashMap;
import java.util.Map;

public class NoteFullTextIndexer implements FullTextIndex.Indexer<Note> {
    static public final String[] INDEXES = Note.FULL_TEXT_INDEXES;

    @Override
    public Map<String, String> index(String[] keys, Note note) {
        Map<String, String> values = new HashMap<>(keys.length);
        values.put(INDEXES[0], note.getTitle());
        values.put(INDEXES[1], note.getContent());
        return values;
    }
}