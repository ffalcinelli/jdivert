package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;

import java.nio.ByteBuffer;

/**
 * eBPF implementation of NativeAdapter for Linux using Project Panama.
 * Targets Java 22+.
 */
public class EBPFDivertPanamaNativeAdapter implements NativeAdapter {

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        // TODO: Implement using libbpf via FFM API
        throw new UnsupportedOperationException("eBPF backend not fully implemented yet");
    }

    @Override
    public int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int send(Handle handle, ByteBuffer packet, WinDivertAddress address) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> WinDivertAsyncResult<T> sendAsync(Handle handle, ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown(Handle handle, int how) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setParam(Handle handle, int param, long value) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getParam(Handle handle, int param) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int calcChecksums(byte[] packet, WinDivertAddress address, long flags) {
        return 0;
    }

    @Override
    public long hashPacket(byte[] packet, long seed) {
        return 0;
    }

    @Override
    public Buffer allocateBuffer(int size) {
        return null;
    }

    @Override
    public String formatMessage(int errorCode) {
        return "Error " + errorCode;
    }

    @Override
    public int getLastError() {
        return 0;
    }
}
