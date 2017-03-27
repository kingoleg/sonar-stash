package org.sonar.plugins.stash;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.CheckForNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputFile;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class InputFileCache implements BatchComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputFileCache.class);

    private final Map<String, InputFile> inputFileByKey = new HashMap<>();

    public InputFileCache() {
        LOGGER.debug("New InputFileCache created");
    }

    // For debug purpose
    public Map<String, InputFile> getInputFileByKeyMap() {
        return Collections.unmodifiableMap(inputFileByKey);
    }

    public void putInputFile(String componentKey, InputFile inputFile) {
        inputFileByKey.put(componentKey, inputFile);
    }

    @CheckForNull
    public InputFile getInputFile(String componentKey) {
        return inputFileByKey.get(componentKey);
    }

    @Override
    public String toString() {
        return "Stash Plugin InputFile Cache";
    }

}
