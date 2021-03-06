package org.sonar.plugins.stash.issue.collector;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.DeserializationException;
import org.json.simple.JsonArray;
import org.json.simple.JsonObject;
import org.json.simple.Jsoner;
import org.sonar.plugins.stash.PullRequestRef;
import org.sonar.plugins.stash.StashPlugin;
import org.sonar.plugins.stash.exceptions.StashReportExtractionException;
import org.sonar.plugins.stash.issue.StashComment;
import org.sonar.plugins.stash.issue.StashCommentReport;
import org.sonar.plugins.stash.issue.StashDiff;
import org.sonar.plugins.stash.issue.StashDiffReport;
import org.sonar.plugins.stash.issue.StashPullRequest;
import org.sonar.plugins.stash.issue.StashTask;
import org.sonar.plugins.stash.issue.StashUser;

public final class StashCollector {

    private static final String AUTHOR = "author";
    private static final String VERSION = "version";

    private StashCollector() {
        // NOTHING TO DO
        // Pure static class
    }

    public static StashCommentReport extractComments(JsonObject jsonComments) throws StashReportExtractionException {
        StashCommentReport result = new StashCommentReport();

        JsonArray jsonValues = (JsonArray) jsonComments.get("values");
        if (jsonValues != null) {

            for (Object obj : jsonValues.toArray()) {
                JsonObject jsonComment = (JsonObject) obj;

                StashComment comment = extractComment(jsonComment);
                result.add(comment);
            }
        }

        return result;
    }

    public static StashComment extractComment(JsonObject jsonComment, String path, Long line) {

        long id = jsonComment.getLong("id");
        String message = jsonComment.getString("text");

        long version = jsonComment.getLong(VERSION);

        JsonObject jsonAuthor = (JsonObject) jsonComment.get(AUTHOR);
        StashUser stashUser = extractUser(jsonAuthor);

        return new StashComment(id, message, path, line, stashUser, version);
    }

    public static StashComment extractComment(JsonObject jsonComment) throws StashReportExtractionException {

        JsonObject jsonAnchor = (JsonObject) jsonComment.get("anchor");
        if (jsonAnchor == null) {
            throw new StashReportExtractionException(
                    "JSON Comment does not contain any \"anchor\" tag" + " to describe comment \"line\" and \"path\"");
        }

        String path = (String) jsonAnchor.get("path");

        // can be null if comment is attached to the global file
        Long line = null;
        if (jsonAnchor.get("line") != null) {
            line = jsonAnchor.getLong("line");
        }

        return extractComment(jsonComment, path, line);
    }

    public static StashPullRequest extractPullRequest(PullRequestRef pr, JsonObject jsonPullRequest) {
        StashPullRequest result = new StashPullRequest(pr);

        long version = jsonPullRequest.getLong(VERSION);
        result.setVersion(version);

        JsonArray jsonReviewers = (JsonArray) jsonPullRequest.get("reviewers");
        if (jsonReviewers != null) {
            for (Object objReviewer : jsonReviewers.toArray()) {
                JsonObject jsonReviewer = (JsonObject) objReviewer;

                JsonObject jsonUser = (JsonObject) jsonReviewer.get("user");
                if (jsonUser != null) {
                    StashUser reviewer = extractUser(jsonUser);
                    result.addReviewer(reviewer);
                }
            }
        }

        return result;
    }

    public static StashUser extractUser(JsonObject jsonUser) {
        long id = jsonUser.getLong("id");
        String name = jsonUser.getString("name");
        String slug = jsonUser.getString("slug");
        String email = jsonUser.getString("email");

        return new StashUser(id, name, slug, email);
    }

    public static StashDiffReport extractDiffs(JsonObject jsonObject) throws StashReportExtractionException {

        StashDiffReport result = new StashDiffReport();
        JsonArray jsonDiffs = (JsonArray) jsonObject.get("diffs");

        if (jsonDiffs == null) {
            return null;
        }

        // Let's call this for loop "objdiff_loop"
        for (Object objDiff : jsonDiffs.toArray()) {

            JsonObject jsonDiff = (JsonObject) objDiff;
            // destination path in diff view
            // if status of the file is deleted, destination == null
            JsonObject destinationPath = (JsonObject) jsonDiff.get("destination");

            if (destinationPath == null) {
                continue; // Let's process the next item in "objdiff_loop"
            }

            String path = (String) destinationPath.get("toString");
            JsonArray jsonHunks = (JsonArray) jsonDiff.get("hunks");

            if (jsonHunks == null) {
                continue; // Let's process the next item in "objdiff_loop"
            }

            // calling the extracted section to scan the jsonHunks & jsonDiff
            // into usable diffs
            result.add(parseHunksIntoDiffs(path, jsonHunks, jsonDiff));

            // Extract File Comments: this kind of comment will be attached to
            // line 0
            JsonArray jsonLineComments = (JsonArray) jsonDiff.get("fileComments");

            if (jsonLineComments == null) {
                continue; // Let's process the next item in "objdiff_loop"
            }

            StashDiff initialDiff = new StashDiff(StashPlugin.CONTEXT_ISSUE_TYPE, path, 0, 0);

            // Let's call this for loop "objlinc_loop"
            for (Object objLineComment : jsonLineComments.toArray()) {

                JsonObject jsonLineComment = (JsonObject) objLineComment;

                long lineCommentId = jsonLineComment.getLong("id");
                String lineCommentMessage = jsonLineComment.getString("text");
                long lineCommentVersion = jsonLineComment.getLong(VERSION);

                JsonObject objAuthor = (JsonObject) jsonLineComment.get(AUTHOR);

                if (objAuthor == null) {
                    continue; // Let's process the next item in "objlinc_loop"
                }

                StashUser author = extractUser(objAuthor);

                StashComment comment = new StashComment(lineCommentId, lineCommentMessage, path, (long) 0, author,
                        lineCommentVersion);
                initialDiff.addComment(comment);
            }

            result.add(initialDiff);
        }
        return result;
    }

    private static StashDiffReport parseHunksIntoDiffs(String path, JsonArray jsonHunks, JsonObject jsonDiff)
            throws StashReportExtractionException {

        StashDiffReport result = new StashDiffReport();

        // Let's call this for loop "objhunk_loop"
        for (Object objHunk : jsonHunks.toArray()) {

            JsonObject jsonHunk = (JsonObject) objHunk;
            JsonArray jsonSegments = (JsonArray) jsonHunk.get("segments");

            if (jsonSegments == null) {
                continue; // Let's process the next item in "objhunk_loop"
            }

            // Let's call this for loop "objsegm_loop"
            for (Object objSegment : jsonSegments.toArray()) {

                JsonObject jsonSegment = (JsonObject) objSegment;
                // type of the diff in diff view
                // We filter REMOVED type, like useless for SQ analysis
                String type = (String) jsonSegment.get("type");
                //
                JsonArray jsonLines = (JsonArray) jsonSegment.get("lines");

                if (StringUtils.equals(type, StashPlugin.REMOVED_ISSUE_TYPE) || jsonLines == null) {

                    continue; // Let's process the next item in "objsegm_loop"
                }

                // Let's call this for loop "objline_loop"
                for (Object objLine : jsonLines.toArray()) {

                    JsonObject jsonLine = (JsonObject) objLine;
                    // destination line in diff view
                    long source = jsonLine.getLong("source");
                    long destination = jsonLine.getLong("destination");

                    StashDiff diff = new StashDiff(type, path, source, destination);
                    // Add comment attached to the current line
                    JsonArray jsonCommentIds = (JsonArray) jsonLine.get("commentIds");

                    // To keep this method depth under control (squid:S134), we
                    // outsourced the comments extraction
                    result.add(extractCommentsForDiff(diff, jsonDiff, jsonCommentIds));
                }
            }
        }
        return result;
    }

    private static StashDiff extractCommentsForDiff(StashDiff diff, JsonObject jsonDiff, JsonArray jsonCommentIds)
            throws StashReportExtractionException {

        // If there is no comments, we just return the diff as-is
        if (jsonCommentIds == null) {
            return diff;
        }

        // Let's call this for loop "objcomm_loop"
        for (BigDecimal objCommentId : jsonCommentIds.toArray(new BigDecimal[] {})) {

            long commentId = objCommentId.longValueExact();
            JsonArray jsonLineComments = (JsonArray) jsonDiff.get("lineComments");

            if (jsonLineComments == null) {
                continue; // Let's process the next item in "objcomm_loop"
            }

            // Let's call this for loop "objlico_loop"
            for (Object objLineComment : jsonLineComments.toArray()) {

                JsonObject jsonLineComment = (JsonObject) objLineComment;
                long lineCommentId = jsonLineComment.getLong("id");

                if (lineCommentId != commentId) {
                    continue; // Let's process the next item in "objlico_loop"
                }

                String lineCommentMessage = (String) jsonLineComment.get("text");
                long lineCommentVersion = jsonLineComment.getLong(VERSION);

                JsonObject objAuthor = (JsonObject) jsonLineComment.get(AUTHOR);

                if (objAuthor == null) {
                    continue; // Let's process the next item in "objlico_loop"
                }

                StashUser author = extractUser(objAuthor);

                StashComment comment = new StashComment(lineCommentId, lineCommentMessage, diff.getPath(), diff.getDestination(),
                        author, lineCommentVersion);
                diff.addComment(comment);

                // get the tasks linked to the current comment
                JsonArray jsonTasks = (JsonArray) jsonLineComment.get("tasks");

                if (jsonTasks == null) {
                    continue; // Let's process the next item in "objlico_loop"
                }

                for (Object objTask : jsonTasks.toArray()) {
                    JsonObject jsonTask = (JsonObject) objTask;

                    comment.addTask(extractTask(jsonTask.toJson()));
                }
            }
        }
        return diff;
    }

    public static StashTask extractTask(String jsonBody) throws StashReportExtractionException {
        try {
            JsonObject jsonTask = (JsonObject) Jsoner.deserialize(jsonBody);

            long taskId = jsonTask.getLong("id");
            String taskText = jsonTask.getString("text");
            String taskState = jsonTask.getString("state");

            boolean deletable = true;

            JsonObject objPermission = (JsonObject) jsonTask.get("permittedOperations");
            if (objPermission != null) {
                deletable = objPermission.getBoolean("deletable");
            }

            return new StashTask(taskId, taskText, taskState, deletable);

        } catch (DeserializationException e) {
            throw new StashReportExtractionException(e);
        }
    }

    public static boolean isLastPage(JsonObject jsonObject) throws StashReportExtractionException {
        return jsonObject.getBooleanOrDefault("isLastPage", true);
    }

    public static long getNextPageStart(JsonObject jsonObject) throws StashReportExtractionException {
        return jsonObject.getLongOrDefault("nextPageStart", 0);
    }

}
