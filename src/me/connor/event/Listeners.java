package me.connor.event;

import java.util.function.*;

import me.connor.util.*;

public class Listeners {

	public static Consumer<Cancellable> handleCancelled(@Nonnull Runnable ifCancelled) {
		Assert.notNull(ifCancelled);
		return (c) -> {
			if (c.isCancelled()) ifCancelled.run();
		};
	}
	
	public static Consumer<Cancellable> handleNotCancelled(@Nonnull Runnable ifNotCancelled) {
		Assert.notNull(ifNotCancelled);
		return (c) -> {
			if (!c.isCancelled()) ifNotCancelled.run();
		};
	}
	
	public static Consumer<Cancellable> handleCancellable(@Nonnull Runnable ifCancelled, @Nonnull Runnable ifNotCancelled) {
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
