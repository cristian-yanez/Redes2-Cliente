import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.net.*;

public class TCPServer implements Runnable {
	// Connect status constants
	public final static int NULL = 0;
	public final static int DISCONNECTING = 1;
	public final static int BEGIN_CONNECT = 2;
	public final static int CONNECTED = 3;

	// Other constants
	public final static String statusMessages[] = {
			" Error! Could not connect!", " Disconnecting...",
			" Listening...", " Connected" };
	public final static TCPServer tcpObj = new TCPServer();
	public final static String END_CHAT_SESSION = new Character((char) 0)
			.toString(); // Indicates the end of a session

	// Connection atate info
	public static String hostIP = "localhost";
	public static int port = 1234;
	public static int connectionStatus = BEGIN_CONNECT;
	public static String statusString = statusMessages[connectionStatus];
	public static StringBuffer toAppend = new StringBuffer("");
	public static StringBuffer toSend = new StringBuffer("");

	// Various GUI components and info
	public static JFrame mainFrame = null;
	public static JTextArea chatText = null;
	public static JTextField chatLine = null;
	public static JPanel statusBar = null;
	public static JLabel statusField = null;
	public static JTextField statusColor = null;
	public static JTextField ipField = null;
	public static JTextField portField = null;

	// TCP Components
	public static ServerSocket hostServer = null;
	public static Socket socket = null;
	public static BufferedReader in = null;
	public static PrintWriter out = null;

	// ///////////////////////////////////////////////////////////////

	private static JPanel initOptionsPane() {
		JPanel pane = null;

		// Create an options pane
		JPanel optionsPane = new JPanel(new GridLayout(4, 1));

		// IP address input
		pane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		pane.add(new JLabel("Host IP:"));
		ipField = new JTextField(10);
		ipField.setText(hostIP);
		ipField.setEnabled(false);
		
		pane.add(ipField);
		optionsPane.add(pane);

		// Port input
		pane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		pane.add(new JLabel("Port:"));
		portField = new JTextField(10);
		portField.setEditable(true);
		portField.setText((new Integer(port)).toString());

		pane.add(portField);
		optionsPane.add(pane);

		return optionsPane;
	}

	// ///////////////////////////////////////////////////////////////

	// Initialize all the GUI components and display the frame
	private static void initGUI() {
		// Set up the status bar
		statusField = new JLabel();
		statusColor = new JTextField(1);
		statusColor.setBackground(Color.red);
		statusColor.setEditable(false);
		statusBar = new JPanel(new BorderLayout());
		statusBar.add(statusColor, BorderLayout.WEST);
		statusBar.add(statusField, BorderLayout.CENTER);

		// Set up the options pane
		JPanel optionsPane = initOptionsPane();

		// Set up the chat pane
		JPanel chatPane = new JPanel(new BorderLayout());
		chatText = new JTextArea(10, 20);
		chatText.setLineWrap(true);
		chatText.setEditable(false);
		chatText.setForeground(Color.blue);
		JScrollPane chatTextPane = new JScrollPane(chatText,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		chatLine = new JTextField();
		chatLine.setEnabled(false);
		chatLine.addActionListener(new ActionAdapter() {
			public void actionPerformed(ActionEvent e) {
				String s = chatLine.getText();
				if (!s.equals("")) {
					appendToChatBox("OUTGOING: " + s + "\n");
					chatLine.selectAll();

					// Send the string
					sendString(s);
				}
			}
		});
		chatPane.add(chatLine, BorderLayout.SOUTH);
		chatPane.add(chatTextPane, BorderLayout.CENTER);
		chatPane.setPreferredSize(new Dimension(200, 200));

		// Set up the main pane
		JPanel mainPane = new JPanel(new BorderLayout());
		mainPane.add(statusBar, BorderLayout.SOUTH);
		mainPane.add(optionsPane, BorderLayout.WEST);
		mainPane.add(chatPane, BorderLayout.CENTER);

		// Set up the main frame
		mainFrame = new JFrame("Servidor TCP");
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setContentPane(mainPane);
		mainFrame.setSize(mainFrame.getPreferredSize());
		mainFrame.setLocation(200, 200);
		mainFrame.pack();
		mainFrame.setVisible(true);
	}

	// ///////////////////////////////////////////////////////////////

	// The thread-safe way to change the GUI components while
	// changing state
	private static void changeStatusTS(int newConnectStatus, boolean noError) {
		// Change state if valid state
		if (newConnectStatus != NULL) {
			connectionStatus = newConnectStatus;
		}

		// If there is no error, display the appropriate status message
		if (noError) {
			statusString = statusMessages[connectionStatus];
		}
		// Otherwise, display error message
		else {
			statusString = statusMessages[NULL];
		}

		// Call the run() routine (Runnable interface) on the
		// error-handling and GUI-update thread
		SwingUtilities.invokeLater(tcpObj);
	}

	// ///////////////////////////////////////////////////////////////

	// The non-thread-safe way to change the GUI components while
	// changing state
	private static void changeStatusNTS(int newConnectStatus, boolean noError) {
		// Change state if valid state
		if (newConnectStatus != NULL) {
			connectionStatus = newConnectStatus;
		}

		// If there is no error, display the appropriate status message
		if (noError) {
			statusString = statusMessages[connectionStatus];
		}
		// Otherwise, display error message
		else {
			statusString = statusMessages[NULL];
		}

		// Call the run() routine (Runnable interface) on the
		// current thread
		tcpObj.run();
	}

	// ///////////////////////////////////////////////////////////////

	// Thread-safe way to append to the chat box
	private static void appendToChatBox(String s) {
		synchronized (toAppend) {
			toAppend.append(s);
		}
	}

	// ///////////////////////////////////////////////////////////////

	// Add text to send-buffer
	private static void sendString(String s) {
		synchronized (toSend) {
			toSend.append(s + "\n");
		}
	}

	// ///////////////////////////////////////////////////////////////

	// Cleanup for disconnect
	private static void cleanUp() {
		try {
			if (hostServer != null) {
				hostServer.close();
				hostServer = null;
			}
		} catch (IOException e) {
			hostServer = null;
		}

		try {
			if (socket != null) {
				socket.close();
				socket = null;
			}
		} catch (IOException e) {
			socket = null;
		}

		try {
			if (in != null) {
				in.close();
				in = null;
			}
		} catch (IOException e) {
			in = null;
		}

		if (out != null) {
			out.close();
			out = null;
		}
	}

	// ///////////////////////////////////////////////////////////////

	// Checks the current state and sets the enables/disables
	// accordingly
	public void run() {
		switch (connectionStatus) {
		case DISCONNECTING:
			ipField.setEnabled(false);
			portField.setEnabled(false);
			chatLine.setEnabled(false);
			statusColor.setBackground(Color.orange);
			break;

		case CONNECTED:
			ipField.setEnabled(false);
			portField.setEnabled(false);
			chatLine.setEnabled(true);
			statusColor.setBackground(Color.green);
			break;

		case BEGIN_CONNECT:
			ipField.setEnabled(false);
			portField.setEnabled(false);
			chatLine.setEnabled(false);
			chatLine.grabFocus();
			statusColor.setBackground(Color.orange);
			break;
		}

		// Make sure that the button/text field states are consistent
		// with the internal states
		ipField.setText(hostIP);
		portField.setText((new Integer(port)).toString());
		statusField.setText(statusString);
		chatText.append(toAppend.toString());
		toAppend.setLength(0);

		mainFrame.repaint();
	}

	// ///////////////////////////////////////////////////////////////

	// The main procedure
	public static void main(String args[]) {
		String s;

		initGUI();
		changeStatusNTS(BEGIN_CONNECT, true);

		while (true) {
			try { // Poll every ~10 ms
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}

			switch (connectionStatus) {
			case BEGIN_CONNECT:
				try {
					// Try to set up a server if host
					hostServer = new ServerSocket(port);
					socket = hostServer.accept();
					
					in = new BufferedReader(new InputStreamReader(
							socket.getInputStream()));
					out = new PrintWriter(socket.getOutputStream(), true);
					changeStatusTS(CONNECTED, true);
				}
				// If error, clean up and output an error message
				catch (IOException e) {
					cleanUp();
					changeStatusTS(NULL, false);
				}
				break;

			case CONNECTED:
				try {
					// Send data
					if (toSend.length() != 0) {
						out.print(toSend);
						out.flush();
						toSend.setLength(0);
						changeStatusTS(DISCONNECTING, true);
					}

					// Receive data
					if (in.ready()) {
						s = in.readLine();
						if ((s != null) && (s.length() != 0)) {
							appendToChatBox("IN: " + s + "\n");
							changeStatusTS(DISCONNECTING, true);
						}
					}
				} catch (IOException e) {
					cleanUp();
					changeStatusTS(NULL, false);
				}
				break;

			case DISCONNECTING:
				// Tell other chatter to disconnect as well
				out.print(END_CHAT_SESSION);
				out.flush();

				// Clean up (close all streams/sockets)
				cleanUp();
				changeStatusTS(BEGIN_CONNECT, true);
				break;

			default:
				break; // do nothing
			}
		}
	}
}

// //////////////////////////////////////////////////////////////////

// Action adapter for easy event-listener coding
class ActionAdapter implements ActionListener {
	public void actionPerformed(ActionEvent e) {
	}
}

// //////////////////////////////////////////////////////////////////
