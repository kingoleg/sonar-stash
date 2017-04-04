package org.sonar.plugins.stash.issue.collector;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.plugins.stash.IssuePathResolver;
import org.sonar.plugins.stash.issue.StashDiffReport;

public final class SonarQubeCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SonarQubeCollector.class);

    private SonarQubeCollector() {
    }

    /**
     * Create issue report according to issue list generated during SonarQube
     * analysis.
     * 
     * @param diffReport
     */
    public static List<Issue> extractIssueReport(ProjectIssues projectIssues, StashDiffReport diffReport,
            IssuePathResolver issuePathResolver) {
        return StreamSupport.stream(projectIssues.issues().spliterator(), false)
                .filter(issue -> shouldIncludeIssue(issue, issuePathResolver, diffReport)).collect(Collectors.toList());
    }

    // TODO exclude issue not related to diff
    private static boolean shouldIncludeIssue(Issue issue, IssuePathResolver issuePathResolver, StashDiffReport diffReport) {
        if (!issue.isNew()) {
            LOGGER.debug(
                    "Issue {} is not a new issue and so, not added to the report, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
                    issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(),
                    issue.line(), issue.resolution(), issue.attributes());
            return false;
        }

        String path = issuePathResolver.getIssuePath(issue);
        if (path == null) {
            LOGGER.debug(
                    "Issue {} is not linked to a file, not added to the report, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
                    issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(),
                    issue.line(), issue.resolution(), issue.attributes());
            return false;
        }

        if (!diffReport.hasPath(path)) {
            LOGGER.debug(
                    "Issue {} is not linked to a diff, not added to the report, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
                    issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(),
                    issue.line(), issue.resolution(), issue.attributes());
            return false;
        }

        LOGGER.debug(
                "Issue {} is added to the report, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
                issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(), issue.line(),
                issue.resolution(), issue.attributes());
        return true;
    }
}
