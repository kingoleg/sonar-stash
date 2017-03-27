package org.sonar.plugins.stash.coverage;

import static org.sonar.plugins.stash.StashPluginUtils.formatPercentage;
import static org.sonar.plugins.stash.StashPluginUtils.roundedPercentageGreaterThan;
import static org.sonar.plugins.stash.coverage.CoverageUtils.calculateCoverage;
import static org.sonar.plugins.stash.coverage.CoverageUtils.createSonarClient;
import static org.sonar.plugins.stash.coverage.CoverageUtils.getLineCoverage;

import java.text.MessageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.plugins.stash.StashPluginConfiguration;
import org.sonar.wsclient.Sonar;

// We have to execute after all coverage sensors, otherwise we are not able to read their measurements
@Phase(name = Phase.Name.POST)
public class CoverageSensor implements Sensor, BatchComponent {

	private static final Logger LOGGER = LoggerFactory.getLogger(CoverageSensor.class);

	private static final double COVERAGE_THRESHOLD = 85;

    private final FileSystem fileSystem;
    private final ResourcePerspectives perspectives;
    private final StashPluginConfiguration config;
    private ActiveRules activeRules;
    private CoverageProjectStore coverageProjectStore;


    public CoverageSensor(FileSystem fileSystem, ResourcePerspectives perspectives, StashPluginConfiguration config, ActiveRules activeRules, CoverageProjectStore coverageProjectStore) {
        this.fileSystem = fileSystem;
        this.perspectives = perspectives;
        this.config = config;
        this.activeRules = activeRules;
        this.coverageProjectStore = coverageProjectStore;
    }

    @Override
    public void analyse(Project module, SensorContext context) {
        Sonar sonar = createSonarClient(config);

        for (InputFile f : fileSystem.inputFiles(fileSystem.predicates().all())) {
			LOGGER.debug("Getting coverage for {}, status = {}, language = {}, lines = {}, type = {}", f, f.status(),
					f.language(), f.lines(), f.type());

            Integer linesToCover = null;
            Integer uncoveredLines = null;

            Resource fileResource = context.getResource(f);
            Measure<Integer> linesToCoverMeasure = context.getMeasure(fileResource, CoreMetrics.LINES_TO_COVER);
			LOGGER.debug("Lines to cover measure = {}", linesToCoverMeasure);

			if (linesToCoverMeasure != null) {
                linesToCover = linesToCoverMeasure.value();
            }
			LOGGER.debug("Lines to cover = {}", linesToCover);

            Measure<Integer> uncoveredLinesMeasure = context.getMeasure(fileResource, CoreMetrics.UNCOVERED_LINES);
			LOGGER.debug("Uncovered lines measure = {}", uncoveredLinesMeasure);

			if (uncoveredLinesMeasure != null) {
                uncoveredLines = uncoveredLinesMeasure.value();
            }
			LOGGER.debug("Uncovered lines measure = {}", uncoveredLines);

            // get lines_to_cover, uncovered_lines
            if (linesToCover != null && uncoveredLines != null) {
				Double previousLineCoverage = getLineCoverage(sonar, fileResource.getEffectiveKey());
				if (previousLineCoverage == null) {
					LOGGER.debug("Previous coverage from sonar is null");
				}

                double coverage = calculateCoverage(linesToCover, uncoveredLines);

                coverageProjectStore.updateMeasurements(linesToCover, uncoveredLines);

				double previousCoverage = 0d;
				if (previousLineCoverage != null) {
					previousCoverage = previousLineCoverage;
                }

				LOGGER.debug("Previous coverage is {}", previousCoverage);
				LOGGER.debug("Current coverage is {}", coverage);

                // The API returns the coverage rounded.
                // So we can only report anything if the rounded value has changed,
                // otherwise we could report false positives.
				if (shouldAddIssue(f.status(), previousCoverage, coverage)) {
                    addIssue(f, coverage, previousCoverage);
                }
            }
        }
    }

	private boolean shouldAddIssue(InputFile.Status status, double previousCoverage, double coverage) {
		boolean isAddedWithoutTests = previousCoverage == 0 && status == InputFile.Status.ADDED;
		if (isAddedWithoutTests) {
			LOGGER.debug("File is added without tests");
			return true;
		}
		if (roundedPercentageGreaterThan(previousCoverage, coverage)) {
			LOGGER.debug("Previous coverage is better then current");
			return true;
		}

		if (roundedPercentageGreaterThan(coverage, COVERAGE_THRESHOLD)) {
			LOGGER.debug("Current coverage {} is more then {}%", coverage, COVERAGE_THRESHOLD);
		}

		LOGGER.debug("Coverage is not OK");
		return false;
	}

    private void addIssue(InputFile file, double coverage, double previousCoverage) {
        Issuable issuable = perspectives.as(Issuable.class, file);
        if (issuable == null) {
            LOGGER.warn("Could not get a perspective of Issuable to create an issue for {}, skipping", file);
            return;
        }

        String message = formatIssueMessage(file.relativePath(), coverage, previousCoverage);
		if (file.status() == InputFile.Status.ADDED) {
			message += " Forget to add a test for a new added class?";
		}

        Issue issue = issuable.newIssueBuilder()
                .ruleKey(CoverageRule.decreasingLineCoverageRule(file.language()))
                .message(message)
                .build();
        issuable.addIssue(issue);

		LOGGER.debug(
				"Added {} to issuable, issue.componentKey = {}, issue.actionPlanKey = {}, issue.key = {}, issue.ruleKey = {}, issue.message = {}, issue.line = {}, issue.resolution = {}, issue.attributes = {}",
				issue, issue.componentKey(), issue.actionPlanKey(), issue.key(), issue.ruleKey(), issue.message(), issue.line(),
				issue.resolution(), issue.attributes());
    }

    static String formatIssueMessage(String path, double coverage, double previousCoverage) {
        return MessageFormat.format("Line coverage of file {0} lowered from {1}% to {2}%.",
                                    path, formatPercentage(previousCoverage), formatPercentage(coverage));
    }

    @Override
    public String toString() {
        return "Stash Plugin Coverage Sensor";
    }

    @Override
    public boolean shouldExecuteOnProject(Project project) {
        // We only execute when run in stash reporting mode
        // This indicates we are running in preview mode,
        // I don't know how we should behave during a normal scan
        return config.hasToNotifyStash() && CoverageRule.shouldExecute(activeRules);
    }
}
