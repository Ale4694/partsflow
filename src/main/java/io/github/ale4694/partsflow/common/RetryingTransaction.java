package io.github.ale4694.partsflow.common;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a unit of work in a transaction and repeats it when it loses an optimistic locking race.
 * <p>
 * Optimistic locking only detects a conflict when the transaction commits, so the retry has to wrap the whole
 * transaction. That is why this is a helper and not a {@code @Transactional} annotation: the caller of
 * {@link #execute} must NOT already be inside a transaction, or the retry would happen too late.
 */
@Component
public class RetryingTransaction {

	private static final Logger log = LoggerFactory.getLogger(RetryingTransaction.class);

	/**
	 * With N concurrent writers on the same row, each round at least one of them wins, so a writer
	 * loses at most N-1 times. 10 attempts is plenty for a small distributor.
	 */
	public static final int MAX_ATTEMPTS = 10;

	private final TransactionTemplate transaction;

	public RetryingTransaction(PlatformTransactionManager transactionManager) {
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public <T> T execute(Supplier<T> work) {
		for (int attempt = 1; ; attempt++) {
			try {
				return transaction.execute(status -> work.get());
			}
			catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
				// Another transaction changed the same row (or created it first): try again on fresh data
				if (attempt == MAX_ATTEMPTS) {
					throw new ConflictException("I dati sono stati modificati nello stesso momento da qualcun altro: riprova");
				}
				log.debug("Concurrent update, retrying (attempt {})", attempt);
			}
		}
	}
}
