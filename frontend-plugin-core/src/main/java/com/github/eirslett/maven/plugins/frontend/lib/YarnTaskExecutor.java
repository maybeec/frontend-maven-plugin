package com.github.eirslett.maven.plugins.frontend.lib;

import static com.github.eirslett.maven.plugins.frontend.lib.Utils.implode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class YarnTaskExecutor {
    private static final String DS = "//";

    private static final String AT = "@";

    private final Logger logger;

    private final String taskName;

    private final List<String> additionalArguments;

    private final YarnExecutorConfig config;

    public YarnTaskExecutor(YarnExecutorConfig config, String taskLocation) {
        this(config, taskLocation, Collections.<String> emptyList());
    }

    public YarnTaskExecutor(YarnExecutorConfig config, String taskName, String taskLocation) {
        this(config, taskName, taskLocation, Collections.<String> emptyList());
    }

    public YarnTaskExecutor(YarnExecutorConfig config, String taskLocation,
        List<String> additionalArguments) {
        this(config, getTaskNameFromLocation(taskLocation), taskLocation, additionalArguments);
    }

    public YarnTaskExecutor(YarnExecutorConfig config, String taskName, String taskLocation,
        List<String> additionalArguments) {
        logger = LoggerFactory.getLogger(getClass());
        this.config = config;
        this.taskName = taskName;
        this.additionalArguments = additionalArguments;
    }

    private static String getTaskNameFromLocation(String taskLocation) {
        return taskLocation.replaceAll("^.*/([^/]+)(?:\\.js)?$", "$1");
    }

    public final void execute(String args, Map<String, String> environment) throws TaskRunnerException {
        final List<String> arguments = getArguments(args);
        logger.info("Running " + taskToString(taskName, arguments) + " in " + config.getWorkingDirectory());

        File root = config.getWorkingDirectory();
        List<File> worklist = new ArrayList<>();
        worklist.addAll(Arrays.asList(root.listFiles()));
        while (!worklist.isEmpty()) {
            List<File> addToWorklist = new ArrayList<>(worklist);
            Iterator<File> it = worklist.iterator();
            while (it.hasNext()) {
                File next = it.next();
                if (next.isDirectory()) {
                    addToWorklist.addAll(Arrays.asList(next.listFiles()));
                } else {
                    logger.debug("-- " + next.getAbsolutePath());
                }
                it.remove();
            }
            worklist.addAll(addToWorklist);
        }

        try {
            final int result =
                new YarnExecutor(config, arguments, environment).executeAndRedirectOutput(logger);
            if (result != 0) {
                throw new TaskRunnerException(
                    taskToString(taskName, arguments) + " failed. (error code " + result + ")");
            }
        } catch (ProcessExecutionException e) {
            throw new TaskRunnerException(taskToString(taskName, arguments) + " failed.", e);
        }
    }

    private List<String> getArguments(String args) {
        List<String> arguments = new ArrayList<>();
        if (args != null && !args.equals("null") && !args.isEmpty()) {
            arguments.addAll(Arrays.asList(args.split("\\s+")));
        }

        for (String argument : additionalArguments) {
            if (!arguments.contains(argument)) {
                arguments.add(argument);
            }
        }
        return arguments;
    }

    private static String taskToString(String taskName, List<String> arguments) {
        List<String> clonedArguments = new ArrayList<>(arguments);
        for (int i = 0; i < clonedArguments.size(); i++) {
            final String s = clonedArguments.get(i);
            final boolean maskMavenProxyPassword = s.contains("proxy=");
            if (maskMavenProxyPassword) {
                final String bestEffortMaskedPassword = maskPassword(s);
                clonedArguments.set(i, bestEffortMaskedPassword);
            }
        }
        return "'" + taskName + " " + implode(" ", clonedArguments) + "'";
    }

    private static String maskPassword(String proxyString) {
        String retVal = proxyString;
        if (proxyString != null && !"".equals(proxyString.trim())) {
            boolean hasSchemeDefined = proxyString.contains("http:") || proxyString.contains("https:");
            boolean hasProtocolDefined = proxyString.contains(DS);
            boolean hasAtCharacterDefined = proxyString.contains(AT);
            if (hasSchemeDefined && hasProtocolDefined && hasAtCharacterDefined) {
                final int firstDoubleSlashIndex = proxyString.indexOf(DS);
                final int lastAtCharIndex = proxyString.lastIndexOf(AT);
                boolean hasPossibleURIUserInfo = firstDoubleSlashIndex < lastAtCharIndex;
                if (hasPossibleURIUserInfo) {
                    final String userInfo =
                        proxyString.substring(firstDoubleSlashIndex + DS.length(), lastAtCharIndex);
                    final String[] userParts = userInfo.split(":");
                    if (userParts.length > 0) {
                        final int startOfUserNameIndex = firstDoubleSlashIndex + DS.length();
                        final int firstColonInUsernameOrEndOfUserNameIndex =
                            startOfUserNameIndex + userParts[0].length();
                        final String leftPart =
                            proxyString.substring(0, firstColonInUsernameOrEndOfUserNameIndex);
                        final String rightPart = proxyString.substring(lastAtCharIndex);
                        retVal = leftPart + ":***" + rightPart;
                    }
                }
            }
        }
        return retVal;
    }
}
