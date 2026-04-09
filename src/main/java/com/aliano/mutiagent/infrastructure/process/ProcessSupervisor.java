package com.aliano.mutiagent.infrastructure.process;

import java.io.IOException;
import java.util.List;

public interface ProcessSupervisor {

    ProcessRuntime start(SessionLaunchContext context) throws IOException;

    void sendInput(String sessionId, String input, boolean appendNewLine) throws IOException;

    void stop(String sessionId, StopMode stopMode);

    List<ProcessRuntime> listRunning();

    long countRunning();
}
