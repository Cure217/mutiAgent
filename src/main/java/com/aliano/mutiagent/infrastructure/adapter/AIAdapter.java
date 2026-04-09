package com.aliano.mutiagent.infrastructure.adapter;

import com.aliano.mutiagent.domain.instance.AppInstance;
import com.aliano.mutiagent.domain.session.AiSession;

public interface AIAdapter {

    String getType();

    LaunchPlan buildLaunchPlan(AppInstance instance, AiSession session);

    ParseResult parseOutput(AppInstance instance, String streamName, String chunk);

    boolean supportsStructuredMessage();

    boolean supportsInteractiveInput();
}
