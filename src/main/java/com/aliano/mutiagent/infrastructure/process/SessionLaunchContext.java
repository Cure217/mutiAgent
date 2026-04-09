package com.aliano.mutiagent.infrastructure.process;

import com.aliano.mutiagent.domain.instance.AppInstance;
import com.aliano.mutiagent.domain.session.AiSession;
import com.aliano.mutiagent.infrastructure.adapter.AIAdapter;

public record SessionLaunchContext(AiSession session, AppInstance instance, AIAdapter adapter) {
}
