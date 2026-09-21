package com.sudo0x.simple.identity.common.exception;

public class AccountDisabledException extends RuntimeException {
    public AccountDisabledException() {
        super("Account is disabled");
    }
}
