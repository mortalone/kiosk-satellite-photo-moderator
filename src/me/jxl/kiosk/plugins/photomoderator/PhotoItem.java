// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.photomoderator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhotoItem {
    public final int id;
    public final String fileName;
    public final String thumbUrl;
    public final String takenAt;
    public final List<String> matchedSubjects;
    public final int shownAgoSeconds;

    public PhotoItem(int id, String fileName, String thumbUrl, String takenAt,
                     List<String> matchedSubjects, int shownAgoSeconds) {
        this.id = id;
        this.fileName = fileName == null ? "" : fileName;
        this.thumbUrl = thumbUrl == null ? "" : thumbUrl;
        this.takenAt = takenAt == null ? "" : takenAt;
        this.matchedSubjects = matchedSubjects == null ? Collections.emptyList() : matchedSubjects;
        this.shownAgoSeconds = shownAgoSeconds;
    }

    public static PhotoItem fromJson(JSONObject obj) {
        if (obj == null) return null;
        int id = obj.optInt("id", -1);
        if (id <= 0) return null;
        String fileName = obj.optString("file_name", "");
        String thumbUrl = obj.optString("thumb_url", "");
        String takenAt = obj.optString("taken_at", "");
        int shownAgo = obj.optInt("shown_ago_seconds", 0);

        List<String> subjects = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("matched_subjects");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty()) subjects.add(s);
            }
        }
        return new PhotoItem(id, fileName, thumbUrl, takenAt, subjects, shownAgo);
    }

    public String subjectsString() {
        if (matchedSubjects.isEmpty()) return "Ingen registrerede personer";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matchedSubjects.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(matchedSubjects.get(i));
        }
        return sb.toString();
    }
}
