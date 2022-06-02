package me.connor.sevents;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/* Okay, let's talk. So the basic idea behind why this is what it is is as follows:
 * In a given execution, it is assumed that events will be dispatched far more
 * often than new listeners will be registered, and when new listeners are
 * registered, they will usually be registered all at once for a given EventChannel
 * (say when the program is initializing or a plugin is loaded or whatever).
 * With that in mind, Listener iteration speed during Event dispatch should be
 * heavily prioritized over Listener registration speed. But there are some things
 * we need to keep in mind:
 * 
 * 1: When an event is dispatched, its ancestors are also dispatched, so the value
 * that was dispatched will need to be converted before being passed to parent listeners.
 * 
 * 2: The above constraint does not exist for an Event or its ancestors if the event
 * is blank, meaning for all Event lineages, there will be a point in the dispatch
 * (either at the root or before then), where we no longer need to reference or
 * convert a dispatch value.
 * 
 * 3: Listeners should be executed in order of lineage, meaning all of the Listeners of a given
 * Event should be executed first, then all of its parent's listeners, etc. Within a given
 * Event Node, the order of Listener execution can be async/undefined if the Listeners
 * are Observers or Monitors, but must be consistent and sequential if the Listeners are Mutators.
 * The implementation detail of how they're executed is left to the EventChannel, we just
 * need to ensure these rules can be followed by the structure we return.
 * 
 * 4: When an Event is dispatched, only it and its ancestors will be dispatched, not its
 * descendants. This means that a parent Node can safely ignore Listeners being added
 * to a descendant Node, but a child Node must be aware of new Listeners that are added to
 * its ancestors.
 * 
 * 5: Because of the above rules, the Listeners of a given lineage can be treated kind of
 * like Matryoshka dolls, where the Listeners of an Event and its ancestors can be shared by
 * its children.
 * 
 * This is not a space-efficient implementation by any means, but our goal here is speed above all
 * else. We may be able to reduce the number of arrays being used with further tweaking, but for
 * now I am sticking with this.
 */

public class EventMap {
	
	/* Abstracting away the type signatures of the consumers. The types
	 * are enforced externally by the EventMap itself.
	 */
	@SuppressWarnings("rawtypes")
	public static final class Listener {
		
		final BiConsumer listener;
		
		Listener(BiConsumer<Event<?>, ?> listener) {
			this.listener = listener;
		}
		
		public <T> void accept(Event<?> event, T value) {
			listener.accept(event, value);
		}
		
	}
	
	static final class Node {
		
		final Event<?> event;
		final List<Listener> listeners = new ArrayList<>();
		final List<Node> children = new ArrayList<>();
		
		//These do not need to be volatile because their writes are synchronized with the Listeners List
		Listener[] baked = new Listener[0];
		boolean needsBake = false;
		
		Node(Event<?> event) {
			this.event = event.isProxy() ? ((Event.Proxy<?>) event).proxied() : event;
		}
		
		Listener[] baked() {
			return (needsBake ? bake() : this.baked).clone();
		}
		
		/* Structured this way so that an in-progress dispatch won't be data-raced. If a bake does occur,
		 * it guarantees that the dispatch that called the bake gets only the state of the Listeners at the
		 * moment the bake occurred, meaning it won't get overwritten if while building the bakedTree, a Listener
		 * is added and then another dispatch calls bake asynchronously.
		 */
		Listener[] bake() {
			Listener[] baked;
			synchronized (listeners) {
				baked = listeners.toArray(Listener[]::new);
				this.baked = baked;
				needsBake = false;
			}
			return baked;
		}
		
		void add(Listener listener) {
			synchronized (listeners) {
				listeners.add(listener);
				needsBake = true;
			}
		}
		
		boolean add(Node child) {
			synchronized(children) {
				for (Node c : children) {
					if (c.event == child.event)
						return false;
				}
				return children.add(child);
			}
		}
		
		//TODO: This whole thing is a big stupid mess. I'm certain there's a better way to do this.
		Node[] get(Event<?> child) {
			Event<?>[] eventLineage = lineageBetween(child.isProxy() ? ((Event.Proxy<?>) child).proxied() : child, event);
			Node[] nodeLineage = new Node[eventLineage.length];
			nodeLineage[eventLineage.length - 1] = this;
			Node node = this;
			boolean existing = true;
			//starting at 1 less than the last index because the last index is populated by this instance.
			int index = eventLineage.length - 1;
			while (existing && index > 0) {
				Event<?> event = eventLineage[--index];
				existing = false;
				for (Node childNode : node.children) {
					if (childNode.event == event) {
						nodeLineage[index] = node = childNode;
						existing = true;
						break;
					}
				}
			}
			//One or more of the descendant Nodes are missing; we need to make them.
			if (!existing && index >= 0) {
				Node newLineage = new Node(eventLineage[0]);
				nodeLineage[0] = newLineage;
				for (int i = 1; i <= index; i++) {
					Node n = new Node(eventLineage[i]);
					//At this moment all of these nodes are only referenced within this block
					//so it's okay to bypass synchronization to add to their children Lists
					n.children.add(newLineage);
					newLineage = nodeLineage[i] = n;
				}
				//Something else added a node for the missing Event before this thread could,
				//so we're just scrapping the nodeLineage array we've made and are retrying
				if (!node.add(newLineage))
					nodeLineage = get(child);
			}
			return nodeLineage;
		}
		
		@Override
		public String toString() {
			return "Event: " + event
					+ ", Children: " + children.toString();
		}
		
		@Override
		public int hashCode() {
			return event.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			return obj instanceof Node && ((Node) obj).event == event;
		}
		
	}
	
	final Node root = new Node(Event.ROOT);
	
	EventMap() {
		
	}
	
	public Listener[][] get(Event<?> event) {
		if (event == null)
			throw new NullPointerException();
		Node[] lineage = root.get(event);
		Listener[][] baked = new Listener[lineage.length][];
		int index = 0;
		for (Node n : lineage) {
			baked[index++] = n.baked();
		}
		return baked;
	}
	
	public <T> void add(Event<T> event, BiConsumer<Event<?>, ? super T> listener) {
		if (event == null || listener == null)
			throw new NullPointerException();
		Listener l = new Listener(listener);
		if (event.isRoot()) {
			root.add(l);
		}
		else {
			Node[] lineage = root.get(event);
			lineage[0].add(l);
		}
		
	}
	
	
	//--- Static Shit ---//
	
	//NOTE: Array is ordered by youngest to oldest, which is what we need for our purposes
	public static Event<?>[] lineageBetween(Event<?> descendant, Event<?> ancestor) {
		//if (!ancestor.isAncestorOf(descendant))
		//	throw new IllegalArgumentException(descendant.name() + " is not descended from " + ancestor.name());
		List<Event<?>> lineage = new ArrayList<>();
		Event<?> event = descendant;
		do {
			lineage.add(event);
		} while ((event = event.parent()) != ancestor);
		lineage.add(ancestor);
		//Collections.reverse(lineage);
		return lineage.toArray(Event[]::new);
	}

}
