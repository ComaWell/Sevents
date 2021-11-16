package me.connor.event;

import java.util.function.*;

import me.connor.util.*;

public class Listeners {

	public static <T> Consumer<Cancellable<T>> handleCancelled(@Nonnull Consumer<T> ifCancelled) {
		Assert.notNull(ifCancelled);
		return (c) -> {
			if (c.isCancelled()) ifCancelled.accept(c.value());
		};
	}
	
	public static <T> Consumer<Cancellable<T>> handleCancelled(@Nonnull Runnable ifCancelled) {
		Assert.notNull(ifCancelled);
		return handleCancelled(() -> ifCancelled.run());
	}
	
	public static <T> Consumer<Cancellable<T>> handleNotCancelled(@Nonnull Consumer<T> ifNotCancelled) {
		Assert.notNull(ifNotCancelled);
		return (c) -> {
			if (!c.isCancelled()) ifNotCancelled.accept(c.value());
		};
	}
	
	public static <T> Consumer<Cancellable<T>> handleNotCancelled(@Nonnull Runnable ifNotCancelled) {
		Assert.notNull(ifNotCancelled);
		return handleNotCancelled(() -> ifNotCancelled.run());
	}
	
	public static <T> Consumer<Cancellable<T>> handleCancellable(@Nonnull Consumer<T> ifCancelled, @Nonnull Consumer<T> ifNotCancelled) {
		Assert.allNotNull(ifCancelled, ifNotCancelled);
		return (c) -> {
			(c.isCancelled() ? ifCancelled : ifNotCancelled).accept(c.value());
		};
	}
	
	public static <T> Consumer<Cancellable<T>> handleCancellable(@Nonnull Runnable ifCancelled, @Nonnull Runnable ifNotCancelled) {
		Assert.allNotNull(ifCancelled, ifNotCancelled);
		return (c) -> {
			(c.isCancelled() ? ifCancelled : ifNotCancelled).run();
		};
	}
	
	public static Consumer<Object> generic(@Nonnull Runnable onEvent) {
		Assert.notNull(onEvent);
		return (o) -> onEvent.run();
	}
	
}
