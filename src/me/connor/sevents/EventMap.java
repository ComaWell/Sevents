package me.connor.sevents;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public final class EventMap {
	
	@SuppressWarnings("rawtypes")
	private final HashMap<Event, List> listeners = new HashMap<>();
	
	@SuppressWarnings("rawtypes")
	private Map<Event, BiConsumer[]> baked;
	
	public EventMap() {
		
	}
	
	private boolean isBaked() {
		return baked != null;
	}
	
	public <T> BiConsumer<Event<?>, T>[] get(Event<T> event) {
		if (!isBaked())
			bake();
		return baked.get(event).clone();//The cost of cloning the arrays is way lower than the iteration cost of using an immutable list instead
	}
	
	@SuppressWarnings("rawtypes")
	synchronized
	private void bake() {
		HashMap<Event, BiConsumer[]> baked = new HashMap<>();
		for (Event e : Set.copyOf(listeners.keySet())) {
			baked.put(e, bake(e));
		}
		this.baked = baked;//keeping baked mutable *should* be fine
	}
	
	private <T> BiConsumer<Event<?>, T>[] bake(Event<T> event) {
		return (event.isBlank() ? bakeBlank((Event.Blank) event) : bakeValued((Event.Valued<T>) event)).toArray(BiConsumer[]::new);
	}
	
	private List<BiConsumer<Event<?>, Void>> bakeBlank(Event.Blank event) {
		if (event == null)
			throw new NullPointerException();
		List<BiConsumer<Event<?>, Void>> listeners = new ArrayList<>(getListeners(event));
		if (!event.isRoot()) listeners.addAll(bakeBlank(event.parent()));
		return listeners;
	}
	
	private <T> List<BiConsumer<Event<?>, T>> bakeValued(Event.Valued<T> event) {
		List<BiConsumer<Event<?>, T>> listeners = new ArrayList<>(getListeners(event));
		listeners.addAll(bakeForDescendant(event.parent(), event));
		return listeners;
	}
	
	private <T, P> List<BiConsumer<Event<?>, T>> bakeForDescendant(Event<P> event, Event.Valued<T> descendant) {
		if (event.isBlank()) return convertListeners(bakeBlank((Event.Blank) event), (Event.Blank) event, descendant);
		List<BiConsumer<Event<?>, T>> listeners = new ArrayList<>(convertListeners(getListeners(event), event, descendant));
		listeners.addAll(bakeForDescendant(event.parent(), descendant));
		return listeners;
	}
	
	private <T, P> List<BiConsumer<Event<?>, T>> convertListeners(List<BiConsumer<Event<?>, P>> listeners, Event<P> from, Event<T> to) {
		return listeners.stream().map((l) -> convertListener(l, from, to)).collect(Collectors.toList());
	}
	
	//TODO: There's one million percent a better/cleaner/faster way to do this
	@SuppressWarnings("rawtypes")
	private <T, P> BiConsumer<Event<?>, T> convertListener(BiConsumer<Event<?>, P> listener, Event<P> from, Event<T> to) {
		return (e, t) -> {
			Object val = t;
			Event current = from;
			do {
				if (current.isBlank()) break;
				else val = ((Event.Valued) current).convert(val);
			} while((current = current.parent()) != to); 
			listener.accept(e, (P) val);
		};
	}
	
	private <T> List<BiConsumer<Event<?>, T>> getListeners(Event<T> event) {
		if (!listeners.containsKey(event))
			listeners.put(event, new ArrayList<BiConsumer<Event<?>, ? super T>>());
		return listeners.get(event);
	}
	
	public <T> void add(Event<T> event, BiConsumer<Event<?>, ? super T> listener) {
		if (event == null || listener == null)
			throw new NullPointerException();
		List<BiConsumer<Event<?>, T>> listeners;
		synchronized(listeners = getListeners(event.isProxy() ? ((Event.Proxy<T>) event).proxied() : event)) {
			listeners.add((BiConsumer<Event<?>, T>) listener);
			baked = null;
		}
	}

}
