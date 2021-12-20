package me.connor.event;

import java.util.concurrent.CompletableFuture;
import java.util.function.*;

import me.connor.util.*;

public class Listeners {

	public static <T> BiConsumer<Event<?>, Cancelable<T>> handleCanceled(@Nonnull Consumer<? super T> ifCanceled) {
		Assert.notNull(ifCanceled);
		return (e, v) -> {
			if (v.isCanceled()) ifCanceled.accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> observeCanceled(@Nonnull Runnable ifCanceled) {
		Assert.notNull(ifCanceled);
		return (e, v) -> {
			if (v.isCanceled()) ifCanceled.run();
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> handleNotCanceled(@Nonnull Consumer<? super T> ifNotCanceled) {
		Assert.notNull(ifNotCanceled);
		return (e, v) -> {
			if (!v.isCanceled()) ifNotCanceled.accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> observeNotCanceled(@Nonnull Runnable ifNotCanceled) {
		Assert.notNull(ifNotCanceled);
		return (e, v) -> {
			if (!v.isCanceled()) ifNotCanceled.run();
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> handleCancelable(@Nonnull Consumer<T> ifCanceled, @Nonnull Consumer<T> ifNotCanceled) {
		Assert.allNotNull(ifCanceled, ifNotCanceled);
		return (e, v) -> {
			(v.isCanceled() ? ifCanceled : ifNotCanceled).accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> observeCancelable(@Nonnull Runnable ifCanceled, @Nonnull Runnable ifNotCanceled) {
		Assert.allNotNull(ifCanceled, ifNotCanceled);
		return (e, v) -> {
			(v.isCanceled() ? ifCanceled : ifNotCanceled).run();
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
