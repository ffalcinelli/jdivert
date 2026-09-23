/*
 * Copyright (c) Fabio Falcinelli 2026.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Code shared by the JNA and Panama adapters of libebpfdivert.
 * <p>
 * Linux-only options are read from system properties:
 * <ul>
 *     <li>{@code jdivert.ebpf.interfaces}: comma-separated interfaces to capture on (default: all)</li>
 *     <li>{@code jdivert.ebpf.ringBytes}: kernel ring buffer size per handle (default: 8 MiB)</li>
 * </ul>
 */
public final class EBPFDivertSupport {

    static final int EAGAIN = 11;
    static final int EINVAL = 22;
    static final int ESHUTDOWN = 108;

    private static final ExecutorService EXECUTOR = createExecutor();

    private EBPFDivertSupport() {
    }

    private static ExecutorService createExecutor() {
        try {
            // Java 21+: one virtual thread per blocking native call.
            return (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (Throwable t) {
            return Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "jdivert-ebpf-async");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /**
     * @return the interfaces selected with {@code jdivert.ebpf.interfaces}, or null for all
     */
    public static String[] interfaces() {
        String value = System.getProperty("jdivert.ebpf.interfaces");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String name : value.split(",")) {
            if (!name.trim().isEmpty()) {
                names.add(name.trim());
            }
        }
        return names.isEmpty() ? null : names.toArray(new String[0]);
    }

    /**
     * @return the ring buffer size selected with {@code jdivert.ebpf.ringBytes}, 0 for the default
     */
    public static int ringBytes() {
        return Integer.getInteger("jdivert.ebpf.ringBytes", 0);
    }

    /**
     * Builds the exception for a negative errno returned by libebpfdivert.
     *
     * @param rc      the return code
     * @param op      the operation that failed
     * @param message the library's description of the error
     * @return the exception to throw
     */
    public static WinDivertException error(int rc, String op, String message) {
        int err = -rc;
        return new WinDivertException(err, op + ": " + (message != null ? message : "errno " + err));
    }

    /**
     * Runs a blocking native call on the async executor.
     *
     * @param call the call, returning a byte count
     * @return the async implementation to wrap in a {@link WinDivertAsyncResult}
     */
    public static WinDivertAsyncResult.AsyncImplementation async(Callable<Integer> call) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        EXECUTOR.submit(() -> {
            try {
                future.complete(call.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return new WinDivertAsyncResult.AsyncImplementation() {
            @Override
            public boolean isCompleted() {
                return future.isDone();
            }

            @Override
            public int waitAndGetResult() throws WinDivertException {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WinDivertException(-1, "Async operation interrupted", e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof WinDivertException) {
                        throw (WinDivertException) e.getCause();
                    }
                    throw new WinDivertException(-1, "Async operation failed", e.getCause());
                }
            }
        };
    }
}
