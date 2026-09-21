package com.sudo0x.simple.identity.common.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Account is temporarily locked due to too many failed login attempts");
    }
}
