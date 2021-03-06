package org.sonar.plugins.stash.issue;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sonar.plugins.stash.StashPlugin;

public class StashDiffReportTest {

    StashDiff diff1;
    StashDiff diff2;
    StashDiff diff3;

    StashDiffReport report1 = new StashDiffReport();

    @Before
    public void setUp() {
        StashComment comment1 = mock(StashComment.class);
        when(comment1.getId()).thenReturn((long) 12345);

        StashComment comment2 = mock(StashComment.class);
        when(comment2.getId()).thenReturn((long) 54321);

        diff1 = new StashDiff(StashPlugin.CONTEXT_ISSUE_TYPE, "path/to/diff1", 10, 20);
        diff1.addComment(comment1);

        diff2 = new StashDiff(StashPlugin.ADDED_ISSUE_TYPE, "path/to/diff2", 20, 30);
        diff2.addComment(comment2);

        diff3 = new StashDiff(StashPlugin.CONTEXT_ISSUE_TYPE, "path/to/diff3", 30, 40);

        report1.add(diff1);
        report1.add(diff2);
        report1.add(diff3);
    }

    @Test
    public void testAdd() {
        StashDiffReport report = new StashDiffReport();
        assertEquals(report.getDiffs().size(), 0);

        report.add(diff1);
        assertEquals(report.getDiffs().size(), 1);

        StashDiff result1 = report.getDiffs().get(0);
        assertEquals(result1.getPath(), "path/to/diff1");
        assertEquals(result1.getType(), StashPlugin.CONTEXT_ISSUE_TYPE);
        assertEquals(result1.getSource(), 10);
        assertEquals(result1.getDestination(), 20);

        report.add(diff2);
        assertEquals(report.getDiffs().size(), 2);

        StashDiff result2 = report.getDiffs().get(1);
        assertEquals(result2.getPath(), "path/to/diff2");
        assertEquals(result2.getType(), StashPlugin.ADDED_ISSUE_TYPE);
        assertEquals(result2.getSource(), 20);
        assertEquals(result2.getDestination(), 30);
    }

    @Test
    public void testAddReport() {
        assertEquals(report1.getDiffs().size(), 3);

        StashDiffReport report = new StashDiffReport();
        assertEquals(report.getDiffs().size(), 0);

        report.add(report1);
        assertEquals(report.getDiffs().size(), 3);
    }

    @Test
    public void testGetType() {
        assertEquals(report1.getType("path/to/diff1", 20), StashPlugin.CONTEXT_ISSUE_TYPE);
        assertEquals(report1.getType("path/to/diff2", 30), StashPlugin.ADDED_ISSUE_TYPE);

        assertEquals(report1.getType("path/to/diff2", 20), null);
        assertEquals(report1.getType("path/to/diff1", 30), null);
        assertEquals(report1.getType("path/to/diff4", 60), null);
    }

    @Test
    public void testGetTypeWithNoDestination() {
        assertEquals(report1.getType("path/to/diff1", 0), StashPlugin.CONTEXT_ISSUE_TYPE);
        assertEquals(report1.getType("path/to/diff", 0), null);
    }

    @Test
    public void testHasPath() {
        assertEquals(report1.hasPath("path/to/diff1"), true);
        assertEquals(report1.hasPath("path/to/diff2"), true);
        assertEquals(report1.hasPath("path/to/diff3"), true);
        assertEquals(report1.hasPath("path/to/diff4"), false);
    }

    @Test
    public void testGetDiff() {
        assertThat(report1.getDiff("path/to/diff1"), contains(diff1));
        assertThat(report1.getDiff("path/to/diff2"), contains(diff2));
        assertThat(report1.getDiff("path/to/diff3"), contains(diff3));
        assertThat(report1.getDiff("path/to/diff4"), empty());
    }

    @Test
    public void testGetLine() {
        assertEquals(report1.getLine("path/to/diff1", 20), 10);
        assertEquals(report1.getLine("path/to/diff2", 30), 30);
        assertEquals(report1.getLine("path/to/diff3", 40), 30);

        assertEquals(report1.getLine("path/to/diff1", 50), 0);
    }

    @Test
    public void testGetDiffByComment() {
        StashDiff diff1 = report1.getDiffByComment(12345);
        assertEquals(diff1.getPath(), "path/to/diff1");
        assertEquals(diff1.getType(), StashPlugin.CONTEXT_ISSUE_TYPE);
        assertEquals(diff1.getSource(), 10);
        assertEquals(diff1.getDestination(), 20);

        StashDiff diff2 = report1.getDiffByComment(123456);
        assertEquals(diff2, null);
    }

    @Test
    public void testGetComments() {
        List<StashComment> comments = report1.getComments();

        assertEquals(comments.size(), 2);
        assertEquals(comments.get(0).getId(), 12345);
        assertEquals(comments.get(1).getId(), 54321);
    }

    @Test
    public void testGetCommentsWithoutAnyIssues() {
        StashDiffReport report = new StashDiffReport();
        List<StashComment> comments = report.getComments();
        assertEquals(comments.size(), 0);
    }

    @Test
    public void testGetCommentsWithDuplicatedComments() {
        StashComment comment1 = new StashComment(12345, "message", "path", (long) 1, mock(StashUser.class), 1);
        diff1.addComment(comment1);

        StashComment comment2 = new StashComment(12345, "message", "path", (long) 1, mock(StashUser.class), 1);
        diff2.addComment(comment2);

        StashComment comment3 = new StashComment(54321, "message", "path", (long) 1, mock(StashUser.class), 1);
        diff3.addComment(comment3);

        List<StashComment> comments = report1.getComments();

        assertEquals(comments.size(), 2);
        assertEquals(comments.get(0).getId(), 12345);
        assertEquals(comments.get(1).getId(), 54321);
    }

}
