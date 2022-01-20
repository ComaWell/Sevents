package me.connor.sevents;

import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * <p>Cancelables are a wrapper Interface used for {@link Event Events} that can be canceled
 * in some way by their listeners. This Object cannot by itself enforce cancelation however,
 * it only serves as an entry point for listeners. Code that is to be executed or not executed
 * based on a Cancelable {@link Event Event's} state should utilize said Cancelable's
 * {@link Cancelable#isCanceled()} method after it has been dispatched. Note that because it is not
 * guaranteed that asynchronous listeners will complete execution before a dispatch is complete,
 * asynchronously mutating a Cancelable instance, even an {@link Atomic} one, may yield
 * undefined or undesirable behavior.
 * </p>
 * 
 * @author Connor Wellington
 *
 * @param <T> The type of Object that is being wrapped by this Cancelable.
 */
public interface Cancelable<T> {

	T value();
	
	boolean isCanceled();
	
	void setCanceled(boolean cancel);
	
	public static <T> Cancelable<T> simple(T value, boolean canceled) {
		return new Simple<>(value, canceled);
	}
	
	public static <T> Cancelable<T> simple(T value) {
		return simple(value, false);
	}
	
	public static <T> Cancelable<T> atomic(T value, boolean canceled) {
		return new Atomic<>(value, canceled);
	}
	
	public static <T> Cancelable<T> atomic(T value) {
		return atomic(value, false);
	}
	
	public static <T> Cancelable<T> proxy(T value, BooleanSupplier supplier, Consumer<Boolean> updater) {
		if (value == null || supplier == null || updater == null)
			throw new NullPointerException();
		return new Cancelable<>() {
			
			@Override
			public T value() {
				return value;
			}
			
			@Override
			public boolean isCanceled() {
				return supplier.getAsBoolean();
			}
			
			@Override
			public void setCanceled(boolean cancel) {
				updater.accept(cancel);
			}
			
		};
	}
	
	public static <T> Cancelable<T> proxy(T value, Function<? super T, Boolean> supplier, Consumer<Boolean> updater) {
		return proxy(value, () -> supplier.apply(value), updater);
	}
	
	static final class Simple<T> implements Cancelable<T> {
		
		private final T value;
		
		private boolean canceled;
		
		private Simple(T value, boolean canceled) {
			if (value == null)
				throw new NullPointerException();
			this.value = value;
			this.canceled = canceled;
		}
		
		@Override
		public T value() {
			return value;
		}
		
		@Override
		public boolean isCanceled() {
			return canceled;
		}
		
		@Override
		public void setCanceled(boolean cancel) {
			canceled = cancel;
		}
		
	}
	
	static final class Atomic<T> implements Cancelable<T> {
		
		private final T value;
		
		private final AtomicBoolean canceled;
		
		private Atomic(T value, boolean canceled) {
			if (value == null)
				throw new NullPointerException();
			this.value = value;
			this.canceled = new AtomicBoolean(canceled);
		}
		
		@Override
		public T value() {
			return value;
		}
		
		@Override
		public boolean isCanceled() {
			return canceled.getAcquire();
		}
		
		@Override
		public void setCanceled(boolean cancel) {
			canceled.lazySet(cancel);
		}
		
	}
	
}
