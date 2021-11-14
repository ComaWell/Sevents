package me.connor.event;

import java.util.concurrent.atomic.*;
import java.util.function.*;

import me.connor.util.*;

public interface Cancellable {

	boolean isCancelled();
	
	void setCancelled(boolean cancel);
	
	public static Cancellable simple() {
		return new Simple();
	}
	
	public static Cancellable atomic() {
		return new Atomic();
	}
	
	public static Cancellable proxy(@Nonnull BooleanSupplier supplier, @Nonnull Consumer<Boolean> updater) {
		Assert.allNotNull(supplier, updater);
		return new Cancellable() {
			
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
	
	static final class Simple implements Cancellable {
		
		private boolean cancelled;
		
		private Simple() {
			cancelled = false;
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
	
	static final class Atomic implements Cancellable {
		
		private final AtomicBoolean cancelled;
		
		private Atomic() {
			cancelled = new AtomicBoolean(false);
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
