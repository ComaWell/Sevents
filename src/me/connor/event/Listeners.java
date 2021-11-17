package me.connor.event;

import java.util.concurrent.CompletableFuture;
import java.util.function.*;

import me.connor.util.*;

public class Listeners {

	public static <T> BiConsumer<Event<?>, Cancellable<T>> handleCancelled(@Nonnull Consumer<? super T> ifCancelled) {
		Assert.notNull(ifCancelled);
		return (e, v) -> {
			if (v.isCancelled()) ifCancelled.accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancellable<T>> observeCancelled(@Nonnull Runnable ifCancelled) {
		Assert.notNull(ifCancelled);
		return (e, v) -> {
			if (v.isCancelled()) ifCancelled.run();
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancellable<T>> handleNotCancelled(@Nonnull Consumer<? super T> ifNotCancelled) {
		Assert.notNull(ifNotCancelled);
		return (e, v) -> {
			if (!v.isCancelled()) ifNotCancelled.accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancellable<T>> observeNotCancelled(@Nonnull Runnable ifNotCancelled) {
		Assert.notNull(ifNotCancelled);
		return (e, v) -> {
			if (!v.isCancelled()) ifNotCancelled.run();
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancellable<T>> handleCancellable(@Nonnull Consumer<T> ifCancelled, @Nonnull Consumer<T> ifNotCancelled) {
		Assert.allNotNull(ifCancelled, ifNotCancelled);
		return (e, v) -> {
			(v.isCancelled() ? ifCancelled : ifNotCancelled).accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancellable<T>> observeCancellable(@Nonnull Runnable ifCancelled, @Nonnull Runnable ifNotCancelled) {
		Assert.allNotNull(ifCancelled, ifNotCancelled);
		return (e, v) -> {
			(v.isCancelled() ? ifCancelled : ifNotCancelled).run();
		};
	}
	
	//Note: Async listeners can potentially complete after the dispatch method call returns, so 
	//it's extremely important that async listeners NEVER mutate the state of the dispatched value.
	public static <T> BiConsumer<Event<?>, ? super T> observeAsync(@Nonnull BiConsumer<Event<?>, ? super T> listener) {
		Assert.notNull(listener);
		return (e, v) -> CompletableFuture.runAsync(() -> listener.accept(e, v));
	}
	
	public static <T> BiConsumer<Event<?>, ? super T> observeAsync(@Nonnull Consumer<? super T> listener) {
		Assert.notNull(listener);
		return (e, v) -> CompletableFuture.runAsync(() -> listener.accept(v));
	}
	
	public static BiConsumer<Event<?>, Object> observeAsync(@Nonnull Runnable listener) {
		Assert.notNull(listener);
		return (e, v) -> CompletableFuture.runAsync(listener);
	}
	
}
