package org.sobotics.chatexchange.chat;

/**
 * Exception when the login fails. Contains information about the hosts
 * 
 * @author FelixSFD
 */
public class ChatLoginException extends Exception {
	private static final long serialVersionUID = 3836431112726533617L;
	
	/**
	 * {@link ChatHost} that was used for the login
	 */
	private ChatHost host;
	
	/**
	 * Initializes the exception
	 * @param chatHost {@link ChatHost} that was used for the login
	 * @param message Error-message
	 */
	public ChatLoginException(ChatHost chatHost, String message) {
		super(message);
		this.host = chatHost;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMessage() {
		String msg = "Unable to login to " + this.host.getName();
		
		if (!this.host.getName().equalsIgnoreCase(this.host.getLoginHost())) {
			msg += " (via " + this.host.getLoginHost() + ")";
		}
		
		msg += ": " + super.getMessage();
		
		return msg;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getLocalizedMessage() {
		return this.getMessage();
	}
}
