package org.sonar.plugins.stash.issue.collector;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.plugins.stash.IssuePathResolver;

public final class SonarQubeCollector {

  private static final Logger LOGGER = LoggerFactory.getLogger(SonarQubeCollector.class);

  private SonarQubeCollector() {}

  /**
   * Create issue report according to issue list generated during SonarQube
   * analysis.
   */
  public static List<Issue> extractIssueReport(ProjectIssues projectIssues, IssuePathResolver issuePathResolver) {
    return StreamSupport.stream(
                         projectIssues.issues().spliterator(), false)
                        .filter(issue -> shouldIncludeIssue(issue, issuePathResolver))
                        .collect(Collectors.toList());
  }

  private static boolean shouldIncludeIssue(Issue issue, IssuePathResolver issuePathResolver) {
    if (!issue.isNew()){
      LOGGER.debug(
        "Issue {} is not a new issue and so, NOT ADDED to the report, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
        issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(),
        issue.line(), issue.resolution(), issue.attributes());
      return false;
    }

    String path = issuePathResolver.getIssuePath(issue);
    if (path == null) {
        LOGGER.debug(
          "Issue {} is not linked to a file, NOT ADDED to the report, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
          issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(),
          issue.line(), issue.resolution(), issue.attributes());
      return false;
    }
    
    LOGGER.debug(
      "Issue {} is new and linked to a file, ADDED to the report, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
      issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(),
      issue.line(), issue.resolution(), issue.attributes());
    return true;
  }
}
