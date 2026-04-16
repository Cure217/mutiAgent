package com.aliano.mutiagent.application.command;

public final class CommandTypes {

    public static final String SESSION_CREATE_START = "session.create.start";
    public static final String SESSION_INPUT_SEND = "session.input.send";
    public static final String SESSION_STOP = "session.stop";
    public static final String CLIENT_ATTACH = "client.attach";
    public static final String CLIENT_DETACH = "client.detach";
    public static final String CLIENT_OBSERVE = "client.observe";

    private CommandTypes() {
    }
}
