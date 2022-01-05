package me.connor.sevents;

import java.util.concurrent.CompletableFuture;
import java.util.function.*;

public class Listeners {

	public static <T> BiConsumer<Event<?>, Cancelable<T>> handleCanceled(Consumer<? super T> ifCanceled) {
		if (ifCanceled == null)
			throw new NullPointerException();
		return (e, v) -> {
			if (v.isCanceled()) ifCanceled.accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> observeCanceled(Runnable ifCanceled) {
		if (ifCanceled == null)
			throw new NullPointerException();
		return (e, v) -> {
			if (v.isCanceled()) ifCanceled.run();
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> handleNotCanceled(Consumer<? super T> ifNotCanceled) {
		if (ifNotCanceled == null)
			throw new NullPointerException();
		return (e, v) -> {
			if (!v.isCanceled()) ifNotCanceled.accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> observeNotCanceled(Runnable ifNotCanceled) {
		if (ifNotCanceled == null)
			throw new NullPointerException();
		return (e, v) -> {
			if (!v.isCanceled()) ifNotCanceled.run();
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> handleCancelable(Consumer<T> ifCanceled, Consumer<T> ifNotCanceled) {
		if (ifCanceled == null || ifNotCanceled == null)
			throw new NullPointerException();
		return (e, v) -> {
			(v.isCanceled() ? ifCanceled : ifNotCanceled).accept(v.value());
		};
	}
	
	public static <T> BiConsumer<Event<?>, Cancelable<T>> observeCancelable(Runnable ifCanceled, Runnable ifNotCanceled) {
		if (ifCanceled == null || ifNotCanceled == null)
			throw new NullPointerException();
		return (e, v) -> {
			(v.isCanceled() ? ifCanceled : ifNotCanceled).run();
		};
	}
	
	//Note: Async listeners can potentially complete after the dispatch method call returns, so 
	//it's extremely important that async listeners NEVER mutate the state of the dispatched value.
	public static <T> BiConsumer<Event<?>, ? super T> observeAsync(BiConsumer<Event<?>, ? super T> listener) {
		if (listener == null)
			throw new NullPointerException();
		return (e, v) -> CompletableFuture.runAsync(() -> listener.accept(e, v));
	}
	
	public static <T> BiConsumer<Event<?>, ? super T> observeAsync(Consumer<? super T> listener) {
		if (listener == null)
			throw new NullPointerException();
		return (e, v) -> CompletableFuture.runAsync(() -> listener.accept(v));
	}
	
	public static BiConsumer<Event<?>, Object> observeAsync(Runnable listener) {
		if (listener == null)
			throw new NullPointerException();
		return (e, v) -> CompletableFuture.runAsync(listener);
	}
	
}
