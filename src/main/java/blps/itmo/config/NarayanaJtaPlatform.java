package blps.itmo.config;

import org.hibernate.engine.transaction.jta.platform.internal.AbstractJtaPlatform;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

public class NarayanaJtaPlatform extends AbstractJtaPlatform {

    @Override
    protected TransactionManager locateTransactionManager() {
        return com.arjuna.ats.jta.TransactionManager.transactionManager();
    }

    @Override
    protected UserTransaction locateUserTransaction() {
        return com.arjuna.ats.jta.UserTransaction.userTransaction();
    }
}
