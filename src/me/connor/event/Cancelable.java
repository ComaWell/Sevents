package me.connor.event;

import java.util.concurrent.atomic.*;
import java.util.function.*;

public interface Cancelable<T> {

	T value();
	
	boolean isCanceled();
	
	void setCanceled(boolean cancel);
	
	public static <T> Cancelable<T> simple(T value) {
		return new Simple<>(value);
	}
	
	public static <T> Cancelable<T> atomic(T value) {
		return new Atomic<>(value);
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
		
		private Simple(T value) {
			if (value == null)
				throw new NullPointerException();
			this.value = value;
			canceled = false;
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
		
		private Atomic(T value) {
			if (value == null)
				throw new NullPointerException();
			this.value = value;
			canceled = new AtomicBoolean(false);
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
