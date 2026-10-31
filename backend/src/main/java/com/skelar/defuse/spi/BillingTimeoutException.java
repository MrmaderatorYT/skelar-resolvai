package com.skelar.defuse.spi;
import com.skelar.defuse.service.executor.BillingException;
/** The provider did not answer in time. The command may have been applied remotely: reconcile, never retry blindly. */
public class BillingTimeoutException extends BillingException {
    public BillingTimeoutException(String message) { super(message); }
}
