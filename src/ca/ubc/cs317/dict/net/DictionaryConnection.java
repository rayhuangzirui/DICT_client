package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        try {
            // initialize a socket that take host and port to connect to DICT server
            socket = new Socket(host, port);

            // initialize input and output streams
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);


            // Handle (print) initial welcome message
            String welcomeMessage = input.readLine();
            System.out.println(welcomeMessage);

            // throws DictConnectionException if there is no initial message
            if (welcomeMessage == null) {
                throw new DictConnectionException("No initial welcome message");
            }

            // throws DictConnectionException If the message don't match their expected value
            if (!welcomeMessage.startsWith("220")) {
                throw new DictConnectionException("Unexpected welcome messages");
            }

        } catch (ConnectException e) {
            // throws DictConnectionException If the connection runs out of time to connect
            throw new DictConnectionException("Connection timed out");
        } catch (UnknownHostException e) {
            // throws DictConnectionException If the host does not exist
            throw new DictConnectionException("getaddrinfo for host " + host + " port " + port + ": Name or service not known");
        } catch (IOException e) {
            // throws DictConnectionException If the connection can't be established
            throw new DictConnectionException("Connection cannot be established: " + e);
        }

        // TODO Replace this with code that creates the requested connection
//        throw new DictConnectionException("Not implemented");
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        try {
            if (socket != null && socket.isConnected()) {
                // Send the QUIT message to the server
                output.print("QUIT\r\n");
                output.flush();

                // print the quit message received
                System.out.println(input.readLine());

                // close the connection
                socket.close();
            }
        } catch (Exception e) {
            // ignore exceptions
        }

        // TODO Add your code here
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        try {
            if (socket != null && socket.isConnected()) {
                // send the "DEFINE" request to the server
                String command = "DEFINE " + database.getName() + " " + word + "\r\n";
                output.print(command);
                output.flush();

                // handle the response
                String outputLines = input.readLine();

                // handle no matching and invalid database, return an empty list
                if (outputLines.startsWith("550") || outputLines.startsWith("552")) {
                    return new ArrayList<>();
                } else if (outputLines.startsWith("150")) {
                    // Definition starts
                    // get the number of definition from the first line
                    String[] parts = outputLines.split(" ", 4);
                    int numDef = Integer.parseInt(parts[1]);

                    for (int i = 0; i < numDef; i++) {
                        // iterate on each definition
                        outputLines = input.readLine();

                        if (outputLines.startsWith("151")) {
                            // get the name of database from the 151 line
                            String[] defInfo = outputLines.split(" ", 4);
                            String databaseName = defInfo[2];

                            Definition definition = new Definition(word, databaseName);

                            // read definitions by line starts from response line 151
                            while (!(outputLines = input.readLine()).equals(".")) {
                                definition.appendDefinition(outputLines);
                            }

                            // add the definition to the set
                            set.add(definition);
                        }
                    }

                    for (Definition definition: set) {
                        System.out.println(definition);
                    }

                    // check if the response ends properly
                    outputLines = input.readLine();
                    if (!outputLines.startsWith("250")) {
                        throw new DictConnectionException("Unexpected ending message: " + outputLines);
                    }
                }
            }
        } catch (Exception e) {
            throw new DictConnectionException("Fail to connect");
        }

        // TODO Add your code here
//        for (Definition definition : set) {
//            System.out.println(definition);
//        }

        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();
        try {
            if (socket != null && socket.isConnected()) {
                String matchCommand = "MATCH " + database.getName() + " " + strategy.getName() + " " + word + "\r\n";

                // send the "MATCH" request to the server
                output.print(matchCommand);
                output.flush();

                // handle the response
                String outputLines = input.readLine();

                // Handle database and strategy errors, and no matching case
                if (outputLines.startsWith("550") || outputLines.startsWith("551") || outputLines.startsWith("552")) {
                    return Collections.emptySet();
                }

                // each iteration read one line
                while ((outputLines = input.readLine()) != null) {
                    // The response ends with "."
                    if (outputLines.equals(".")) {
                        break;
                    }

                    // Split each line of string into two parts, database name and matching word
                    String[] parts = outputLines.split(" ", 2);
                    if (parts.length == 2) {
//                        String dbName = parts[0];
                        String matchedWord = parts[1].replaceAll("\"", "");

                        // add the matching word to the set
                        set.add(matchedWord);
                    }
                }

                // check if it ends properly
                outputLines = input.readLine();
                if (!outputLines.startsWith("250")) {
                    throw new DictConnectionException("Unexpected ending message: " + outputLines);
                }
            }
        } catch (Exception e) {
            throw new DictConnectionException("Fail to connect");
        }

        // TODO Add your code here

        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        try {
            if (socket != null && socket.isConnected()) {
                // send the "SHOW DB" request to the server
                output.print("SHOW DB\r\n");
                output.flush();

                // handle the response
                String outputLines = input.readLine();

                // handle the empty databases
                if (outputLines.startsWith("554")) {
                    return Collections.emptyMap();
                }

                // each iteration read one line
                while ((outputLines = input.readLine()) != null) {
                    // The response ends with "."
                    if (outputLines.equals(".")) {
                        break;
                    }

                    // Split each line of string into two parts, name and description
                    String[] parts = outputLines.split(" ", 2);
                    if (parts.length == 2) {
                        String dbName = parts[0];
                        String dbDescription = parts[1].replaceAll("\"", "");

                        // Create database object for this line of response and add it to the map
                        Database database = new Database(dbName, dbDescription);
                        databaseMap.put(dbName, database);
                    }
                }

                // check if it ends properly
                outputLines = input.readLine();
                if (!outputLines.startsWith("250")) {
                    throw new DictConnectionException("Unexpected ending message: " + outputLines);
                }
            }
        } catch (Exception e) {
            throw new DictConnectionException("Fail to connect");
        }

        // TODO Add your code here

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        try {
            if (socket != null && socket.isConnected()) {
                // send the "SHOW STRAT" request to the server
                output.print("SHOW STRAT\r\n");
                output.flush();

                // handle the response
                String outputLines = input.readLine();

                // Handle the case that no strategies available
                if (outputLines.startsWith("555")) {
                    return Collections.emptySet();
                }

                // each iteration read one line
                while ((outputLines = input.readLine()) != null) {
                    // The response ends with "."
                    if (outputLines.equals(".")) {
                        break;
                    }

                    // Split each line of string into two parts, name and description
                    String[] parts = outputLines.split(" ", 2);
                    if (parts.length == 2) {
                        String msName = parts[0];
                        String msDescription = parts[1].replaceAll("\"", "");

                        // Create matching strategy object for this line of response and add it to the set
                        MatchingStrategy matchingStrategy = new MatchingStrategy(msName, msDescription);
                        set.add(matchingStrategy);
                    }
                }

                // check if it ends properly
                outputLines = input.readLine();
                if (!outputLines.startsWith("250")) {
                    throw new DictConnectionException("Unexpected ending message: " + outputLines);
                }
            }
        } catch (Exception e) {
            throw new DictConnectionException("Fail to connect");
        }

        // TODO Add your code here

        return set;
    }

    /** Requests and retrieves detailed information about the currently selected database.
     *
     * @return A string containing the information returned by the server in response to a "SHOW INFO <db>" command.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized String getDatabaseInfo(Database d) throws DictConnectionException {
	    StringBuilder sb = new StringBuilder();

        try {
            if (socket != null && socket.isConnected()) {
                String showInfoCommand = "SHOW INFO " + d.getName() + "\r\n";
                // send the "SHOW INFO <db>" to the server
                output.print(showInfoCommand);
                output.flush();

                // handle the response
                String outputLines = input.readLine();

                // throws exception if the message is unexpected
                if (outputLines.startsWith("550")) {
                    throw new DictConnectionException("Invalid database");
                }

                // each iteration read one line
                while ((outputLines = input.readLine()) != null) {
                    // The response ends with "."
                    if (outputLines.equals(".")) {
                        break;
                    }
                    // Append each lines into string builder
                    sb.append(outputLines).append("\n");
                }

                // check if it ends properly
                outputLines = input.readLine();
                if (!outputLines.startsWith("250")) {
                    throw new DictConnectionException("Unexpected ending message: " + outputLines);
                }
            }
        } catch (Exception e) {
            // Throws exception if the connection was interrupted
            throw new DictConnectionException("Fail to connect");
        }

        // TODO Add your code here

        return sb.toString();
    }
}
