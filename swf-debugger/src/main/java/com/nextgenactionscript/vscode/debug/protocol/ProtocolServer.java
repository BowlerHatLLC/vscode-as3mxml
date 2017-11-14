/*
Copyright 2016-2017 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.nextgenactionscript.vscode.debug.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nextgenactionscript.vscode.debug.requests.InitializeRequest;

public abstract class ProtocolServer
{
    public boolean TRACE;
    public boolean TRACE_RESPONSE;

    protected static final int BUFFER_SIZE = 4096;
    protected static final String TWO_CRLF = "\r\n\r\n";
    protected static final Charset ENCODING = StandardCharsets.UTF_8;

    private OutputStream _outputStream;
    private Pattern contentLengthPattern = Pattern.compile("^Content-Length: (\\d+)");
    private ByteBuffer _rawData;
    private int _bodyLength;
    private int _sequenceNumber;
    protected static Gson gson;
    private boolean _stopRequested;

    public ProtocolServer()
    {
        gson = createGson();
        _sequenceNumber = 1;
        _bodyLength = -1;
        _rawData = new ByteBuffer();
    }

    protected Gson createGson()
    {
        GsonBuilder builder = new GsonBuilder();
        return builder.create();
    }

    public void start(InputStream inputStream, OutputStream outputStream)
    {
        _outputStream = outputStream;

        byte[] buffer = new byte[BUFFER_SIZE];

        _stopRequested = false;
        while (!_stopRequested)
        {
            int read = 0;
            try
            {
                read = inputStream.read(buffer, 0, buffer.length);
            }
            catch (IOException exception)
            {
                System.err.println("Exception reading input stream:");
                exception.printStackTrace(System.err);
            }

            if (read == 0)
            {
                // end of stream
                break;
            }

            if (read > 0)
            {
                _rawData.append(buffer, read);
                processData();
            }
        }
    }

    public void stop()
    {
        _stopRequested = true;
    }

    public void sendEvent(Event<?> e)
    {
        sendMessage(e);
    }

    protected abstract void dispatchRequest(String command, Request.RequestArguments arguments, Response response);

    private void processData()
    {
        while (true)
        {
            if (_bodyLength >= 0)
            {
                if (_rawData.getLength() >= _bodyLength)
                {
                    byte[] buf = _rawData.removeFirst(_bodyLength);
                    _bodyLength = -1;
                    dispatch(new String(buf, ENCODING));
                    continue;   // there may be more complete messages to process
                }
            }
            else
            {
                String s = _rawData.getString(ENCODING);
                int idx = s.indexOf(TWO_CRLF);
                if (idx != -1)
                {
                    Matcher m = contentLengthPattern.matcher(s);
                    if (m.lookingAt() && m.groupCount() == 1)
                    {
                        String contentLength = m.group(1);
                        _bodyLength = Integer.parseInt(contentLength);

                        _rawData.removeFirst(idx + TWO_CRLF.length());

                        continue;   // try to handle a complete message
                    }
                }
            }
            break;
        }
    }

    private void dispatch(String req)
    {
        Request request = gson.fromJson(req, Request.class);
        if (request != null && request.type.equals("request"))
        {
            switch (request.command)
            {
                case InitializeRequest.REQUEST_COMMAND:
                {
                    request = gson.fromJson(req, InitializeRequest.class);
                }
            }
            Request.RequestArguments arguments = null;
            try
            {
                Field field = request.getClass().getField("arguments");
                arguments = (Request.RequestArguments) field.get(request);
            }
            catch (Exception e)
            {
                arguments = new Request.RequestArguments();
            }
            if (TRACE)
            {
                System.err.print(String.format("\r\n\r\n=====C %1$s: %2$s", request.command, req));
            }
            Response response = new Response(request);
            dispatchRequest(request.command, arguments, response);
        }
    }

    protected void sendMessage(ProtocolMessage message)
    {
        message.seq = _sequenceNumber++;

        if (TRACE_RESPONSE && message.type.equals(Response.PROTOCOL_MESSAGE_TYPE))
        {
            System.err.print(String.format("\r\n\r\n+++++R: %1$s", gson.toJson(message)));
        }
        if (TRACE && message.type.equals(Event.PROTOCOL_MESSAGE_TYPE))
        {
            Event<?> e = (Event<?>) message;
            System.err.print(String.format("\r\n\r\n-----E %1$s: %2$s", e.event, gson.toJson(e.body)));
        }

        byte[] data = convertToBytes(message);
        try
        {
            _outputStream.write(data, 0, data.length);
            _outputStream.flush();
        }
        catch (IOException e)
        {
            // ignore
            System.err.println("Exception writing to output stream.");
            e.printStackTrace(System.err);
        }
    }

    private byte[] convertToBytes(ProtocolMessage request)
    {
        String asJson = gson.toJson(request);
        byte[] jsonBytes = asJson.getBytes(ENCODING);

        String header = String.format("Content-Length: %1$s%2$s", jsonBytes.length, TWO_CRLF);
        byte[] headerBytes = header.getBytes(ENCODING);

        byte[] data = new byte[headerBytes.length + jsonBytes.length];
        System.arraycopy(headerBytes, 0, data, 0, headerBytes.length);
        System.arraycopy(jsonBytes, 0, data, headerBytes.length, jsonBytes.length);

        return data;
    }

    private class ByteBuffer
    {
        private byte[] _buffer;

        public ByteBuffer()
        {
            _buffer = new byte[0];
        }

        public int getLength()
        {
            return _buffer.length;
        }

        public String getString(Charset enc)
        {
            return new String(_buffer, enc);
        }

        public void append(byte[] b, int length)
        {
            byte[] newBuffer = new byte[_buffer.length + length];
            System.arraycopy(_buffer, 0, newBuffer, 0, _buffer.length);
            System.arraycopy(b, 0, newBuffer, _buffer.length, length);
            _buffer = newBuffer;
        }

        public byte[] removeFirst(int n)
        {
            byte[] b = new byte[n];
            System.arraycopy(_buffer, 0, b, 0, n);
            byte[] newBuffer = new byte[_buffer.length - n];
            System.arraycopy(_buffer, n, newBuffer, 0, _buffer.length - n);
            _buffer = newBuffer;
            return b;
        }
    }
}
