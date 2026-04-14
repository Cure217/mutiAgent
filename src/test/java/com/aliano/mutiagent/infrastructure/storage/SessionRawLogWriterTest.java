package com.aliano.mutiagent.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionRawLogWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void appendWritesHumanReadableLogAndReplayTranscript() throws IOException {
        SessionRawLogWriter writer = new SessionRawLogWriter();
        Path logPath = tempDir.resolve("session.log");

        writer.append(logPath, "stdout", "hello\r\nworld");
        writer.append(logPath, "stderr", "\u001B[31merror\u001B[0m");

        String humanReadableLog = Files.readString(logPath);
        assertThat(humanReadableLog).contains("[stdout] hello\r\nworld");
        assertThat(humanReadableLog).contains("[stderr] \u001B[31merror\u001B[0m");

        String replayTranscript = Files.readString(SessionRawLogWriter.resolveReplayPath(logPath));
        assertThat(replayTranscript).isEqualTo("hello\r\nworld\u001B[31merror\u001B[0m");
    }
}
