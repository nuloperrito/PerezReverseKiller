package com.perez.revkiller;

import java.util.List;
import java.io.IOException;
import java.io.OutputStream;

public interface Edit {
    void read(List<String> data, byte[] input) throws IOException;

    void write(String data, OutputStream output) throws IOException;
}
