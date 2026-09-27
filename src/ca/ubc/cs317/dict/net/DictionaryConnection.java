package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

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
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            Status status = Status.readStatus(reader);
            int statusCode = status.getStatusCode();
            if (statusCode != 220) {
                throw new DictConnectionException("Status Code is not 220!");
            }
        } catch (IOException e) {
            throw new DictConnectionException("Can't build a new connection with the server.", e);
        }
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
            writer.println("QUIT");
            Status status = Status.readStatus(reader);
            System.out.println(status);
        } catch (Exception e) { // Ignore
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) { // Ignore
            }
        }
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
            writer.println("DEFINE " + database.getName() + " \"" + word + "\"");
            Status status = Status.readStatus(reader);
            int statusCode = status.getStatusCode();
            if (statusCode == 550) {
                return set; // Invalid database
            } else if (statusCode == 552) {
                return set;  // No match
            } else if (statusCode != 150) {
                throw new DictConnectionException("Unexpected status code.");
            }

            // parse the defs
            while (true) {
                Status s = Status.readStatus(reader);
                int sCode = s.getStatusCode();
                if (sCode == 250) {
                    break;
                }
                if (sCode != 151) {
                    throw new DictConnectionException("Invalid Status Code.");
                }
                // code is 151
                String details = s.getDetails();
                String[] atoms = DictStringParser.splitAtoms(details); // parameter 1 is the word retrieved, parameter 2 is the database name
                Definition def = new Definition(atoms[0], atoms[1]);

                String l = reader.readLine();
                while (!l.equals(".")) {
                    def.appendDefinition(l);
                    l = reader.readLine();
                }
                set.add(def);
            }
        } catch (IOException e) {
            throw new DictConnectionException("Unexpected error", e);
        }
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
            writer.println("MATCH " + database.getName() + " " + strategy.getName() + " \"" + word + "\"");
            Status status = Status.readStatus(reader);
            int statusCode = status.getStatusCode();
            if (statusCode == 550 || statusCode == 551 || statusCode == 552) {
                return set;
            } else if (statusCode != 152) {
                throw new DictConnectionException("Unexpected status code.");
            }

            // code is 152
            String line = reader.readLine();
            while (!line.equals(".")) {
                String[] atoms = DictStringParser.splitAtoms(line);
                set.add(atoms[1]);
                line = reader.readLine();
            }

            Status finalstatus = Status.readStatus(reader);
            int finalstatusCode = finalstatus.getStatusCode();
            if (finalstatusCode != 250) {
                throw new DictConnectionException("Unexpected final status code.");
            }
        } catch (IOException e) {
            throw new DictConnectionException(e);
        }
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
            writer.println("SHOW DB");
            Status status = Status.readStatus(reader);
            int statusCode = status.getStatusCode();
            if (statusCode == 554) { // No databases present
                return databaseMap;
            } else if (statusCode != 110) {
                throw new DictConnectionException("Unexpected status code received.");
            }

            String l = reader.readLine();
            while (!l.equals(".")) {
                String[] atoms = DictStringParser.splitAtoms(l);
                String name = atoms[0];
                String des = atoms[1];
                Database db = new Database(name, des);
                databaseMap.put(name, db);
                l = reader.readLine();
            }

            Status finalStatus = Status.readStatus(reader);
            int finalStatusCode = finalStatus.getStatusCode();
            if (finalStatusCode != 250) {
                throw new DictConnectionException("unexpected status code");
            }
        } catch (IOException e) {
            throw new DictConnectionException(e);
        }
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
            writer.println("SHOW STRAT");
            Status status = Status.readStatus(reader);
            int statusCode = status.getStatusCode();
            if (statusCode == 555) {
                return set;
            } else if (statusCode != 111) {
                throw new DictConnectionException("Unexpected status code.");
            }

            // code is 111
            String l = reader.readLine();
            while (!l.equals(".")) {
                String[] atoms = DictStringParser.splitAtoms(l);
                String start = atoms[0];
                String des = atoms[1];
                MatchingStrategy ele = new MatchingStrategy(start, des);
                set.add(ele);
                l = reader.readLine();
            }

            Status finalStatus = Status.readStatus(reader);
            int finalStatusCode = finalStatus.getStatusCode();
            if (finalStatusCode != 250) {
                throw new DictConnectionException("unexpected status code other than 250");
            }
        } catch (IOException e) {
            throw new DictConnectionException(e);
        }
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
            writer.println("SHOW INFO " + d.getName());
            Status status = Status.readStatus(reader);
            int statusCode = status.getStatusCode();
            if (statusCode == 550) {
                return sb.toString();
            } else if (statusCode != 112) {
                throw new DictConnectionException("Unexpected status code.");
            }

            // code is 112
            String line = reader.readLine();
            boolean b = true;
            while (!line.equals(".")) {
                if (b) {
                    sb.append(line);
                } else {
                    sb.append("\n");
                    sb.append(line);
                }
                line = reader.readLine();
                b = false;
            }

            Status finalStatus = Status.readStatus(reader);
            int finalStatusCode = finalStatus.getStatusCode();
            if (finalStatusCode != 250) {
                throw new DictConnectionException("unexpected status code other than 250");
            }
        } catch (IOException e) {
            throw new DictConnectionException(e);
        }
        return sb.toString();
    }      
}
