package com.omernaci.miniflow.common.exception;

import com.omernaci.miniflow.common.i18n.Messages;

public class MiniFlowException extends IllegalArgumentException {

    private final String code;
    private final transient Object[] args;

    public MiniFlowException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args;
    }

    public MiniFlowException(Throwable cause, String code, Object... args) {
        super(code, cause);
        this.code = code;
        this.args = args;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return Messages.get(code, args);
    }
}
