package fr.tunaki.stackoverflow.chat.event;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Base class for all events raised in chat.
 * <p>An event represents an action that was triggered by a user or a system action. Actions made by user typically corresponds
 * to posting messages, editing messages, etc. and actions made by the system typically corresponds to feeds added, change in 
 * access level, etc.
 * <p>All events have a instant at which they occured, represented by an {@link Instant} object (UTC). They also have the user that
 * triggered the action (ID and display name), with the exception of anonymous events (like starring).
 * For system events, the ID will be strictly negative and for anonymous events, it is will be 0.  
 * @author Tunaki
 */
public abstract class Event {
	
	private Instant instant;
	private long userId;
	private String userName;
	private int roomId;

	Event(JsonElement jsonElement) {
		JsonObject jsonObject = jsonElement.getAsJsonObject();
		instant = Instant.ofEpochSecond(jsonObject.get("time_stamp").getAsLong());
		userId = orDefault(jsonObject.get("user_id"), 0, JsonElement::getAsLong);
		userName = orDefault(jsonObject.get("user_name"), null, JsonElement::getAsString);
		roomId = orDefault(jsonObject.get("room_id"), 0, JsonElement::getAsInt);
	}
	
	/**
	 * Returns the instant in time (UTC) at which this event occured.
	 * @return Instant in time (UTC) at which this event occured.
	 */
	public Instant getInstant() {
		return instant;
	}

	/**
	 * Returns the id of the user that raised this event.
	 * <p>For system generated event, the id will be strictly negative. For events where there was
	 * no registered user, this will be 0.
	 * @return Id of the user that raised this event.
	 */
	public long getUserId() {
		return userId;
	}

	/**
	 * Returns the display name of the user that raised this event. This can be <code>null</code> under unreproducible conditions.
	 * @return Display name of the user that raised this event.
	 */
	public String getUserName() {
		return userName;
	}
	
	/**
	 * The id of the room this event took place.
	 * @return ID of the room this event took place.
	 */
	public int getRoomId() {
		return roomId;
	}

	protected <T> T orDefault(JsonElement element, T defaultValue, Function<JsonElement, T> function) {
		return element == null ? defaultValue : function.apply(element);
	}
	
	protected int orDefault(JsonElement element, int defaultValue, ToIntFunction<JsonElement> function) {
		return element == null ? defaultValue : function.applyAsInt(element);
	}
	
	protected long orDefault(JsonElement element, long defaultValue, ToLongFunction<JsonElement> function) {
		return element == null ? defaultValue : function.applyAsLong(element);
	}
	
	protected boolean orDefault(JsonElement element, boolean defaultValue) {
		return element == null ? defaultValue : element.getAsBoolean();
	}
	
}