package griffith.s5330916;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class TetrisClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 3000;
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TetrisClient() { }

    // The server closes the socket after every response, so each call creates a new connection
    public static OpMove requestMove(PureGame game) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(MAPPER.writeValueAsString(game));

                String response = in.readLine();
                if (response == null) {
                    throw new EOFException("TetrisServer closed the connection without returning a move.");
                }

                return MAPPER.readValue(response, OpMove.class);
            }
        }
    }
}
