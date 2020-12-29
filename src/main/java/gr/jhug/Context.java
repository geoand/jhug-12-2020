package gr.jhug;

import java.io.IOException;

interface Context {

    String getData();

    void write(byte[] out) throws IOException;
}
