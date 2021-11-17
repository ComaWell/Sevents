package me.connor.event;

import java.util.concurrent.atomic.*;
import java.util.function.*;

import me.connor.util.*;

public interface Cancellable<T> {

	T value();
	
	boolean isCancelled();
	
	void setCancelled(boolean cancel);
	
	public static <T> Cancellable<T> simple(@Nonnull T value) {
		return new Simple<>(value);
	}
	
	public static <T> Cancellable<T> atomic(@Nonnull T value) {
		return new Atomic<>(value);
	}
	
	public static <T> Cancellable<T> proxy(@Nonnull T value, @Nonnull BooleanSupplier supplier, @Nonnull Consumer<Boolean> updater) {
		Assert.allNotNull(value, supplier, updater);
		return new Cancellable<>() {
			
			@Override
			public T value() {
				return value;
			}
			
			@Override
			public boolean isCancelled() {
				return supplier.getAsBoolean();
			}
			
			@Override
			public void setCancelled(boolean cancel) {
				updater.accept(cancel);
			}
			
		};
	}
	
	public static <T> Cancellable<T> proxy(@Nonnull T value, @Nonnull Function<? super T, Boolean> supplier, @Nonnull Consumer<Boolean> updater) {
		return proxy(value, () -> supplier.apply(value), updater);
	}
	
	static final class Simple<T> implements Cancellable<T> {
		
		private final T value;
		
		private boolean cancelled;
		
		private Simple(@Nonnull T value) {
			Assert.notNull(value);
			this.value = value;
			cancelled = false;
		}
		
		@Override
		public T value() {
			return value;
		}
		
		@Override
		public boolean isCancelled() {
			return cancelled;
		}
		
		@Override
		public void setCancelled(boolean cancel) {
			cancelled = cancel;
		}
		
	}
	
	static final class Atomic<T> implements Cancellable<T> {
		
		private final T value;
		
		private final AtomicBoolean cancelled;
		
		private Atomic(@Nonnull T value) {
			Assert.notNull(value);
			this.value = value;
			cancelled = new AtomicBoolean(false);
		}
		
		@Override
		public T value() {
			return value;
		}
		
		@Override
		public boolean isCancelled() {
			return cancelled.getAcquire();
		}
		
		@Override
		public void setCancelled(boolean cancel) {
			cancelled.lazySet(cancel);
		}
		
	}
	
}
