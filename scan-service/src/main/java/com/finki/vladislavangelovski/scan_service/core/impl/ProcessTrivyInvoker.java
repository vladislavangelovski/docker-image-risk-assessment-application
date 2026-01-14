package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.api.dto.RegistryCreds;
import com.finki.vladislavangelovski.scan_service.core.ScannerException;
import com.finki.vladislavangelovski.scan_service.core.TrivyInvocationRequest;
import com.finki.vladislavangelovski.scan_service.core.TrivyInvoker;
import com.finki.vladislavangelovski.scan_service.core.TrivyOutput;
import com.finki.vladislavangelovski.scan_service.core.config.ScanProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ProcessTrivyInvoker implements TrivyInvoker {
  // ---- Tunables (safe defaults) ----
  /** Guard against pathological outputs (JSON is typically a few MB). Set to 32 MiB by default. */
  private static final int MAX_STDOUT_BYTES = 32 * 1024 * 1024;

  /** Bound stderr to keep logs/error messages small. */
  private static final int MAX_STDERR_BYTES = 32 * 1024;

  private volatile String cachedVersion;
  private final ScanProperties properties;

  public ProcessTrivyInvoker(ScanProperties properties) {
    this.properties = properties;
  }

  @Override
  public TrivyOutput run(TrivyInvocationRequest request) throws ScannerException {
    final List<String> cmd = buildCommand(request);
    final ProcessBuilder pb = new ProcessBuilder(cmd);

    pb.redirectErrorStream(false);

    final Map<String, String> env = pb.environment();
    RegistryCreds registryCreds = request.registryCreds();
    if (registryCreds != null) {
      env.put("TRIVY_USERNAME", registryCreds.username());
      env.put("TRIVY_PASSWORD", registryCreds.password());
    }

    ExecutorService executorService =
        Executors.newFixedThreadPool(
            2,
            r -> {
              Thread thread = new Thread(r, "trivy-io");
              thread.setDaemon(true);
              return thread;
            });
    long deadlineNanos = System.nanoTime() + request.timeout().toNanos();

    try {
      final Process process = pb.start();
      Future<byte[]> outF =
          executorService.submit(() -> readAllBounded(process.getInputStream(), MAX_STDOUT_BYTES));
      Future<byte[]> errF =
          executorService.submit(() -> readAllBounded(process.getErrorStream(), MAX_STDERR_BYTES));

      boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        outF.cancel(true);
        errF.cancel(true);
        throw new ScannerException(
            "Trivy scan timed out after " + request.timeout().toSeconds() + "s");
      }

      long remainingMillis = remainingMillis(deadlineNanos);
      byte[] stdout = getFuture(outF, "stdout", remainingMillis);
      byte[] stderr = getFuture(errF, "stderr", remainingMillis);
      int exit = process.exitValue();

      String rawJson = new String(stdout, StandardCharsets.UTF_8);
      String errStr = new String(stderr, StandardCharsets.UTF_8);

      if (exit != 0) {
        String msg = sanitizeError(errStr);
        if (msg.isBlank()) {
          msg = "Trivy returned non-zero exit code: " + exit;
        }
        throw new ScannerException(msg);
      }

      return new TrivyOutput(rawJson, detectTrivyVersion(), null);
    } catch (IOException e) {
      throw new ScannerException(
          "Failed to start Trivy process (is the binary available on PATH or at scan.trivy.path?)",
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ScannerException("Trivy process interrupted", e);
    } finally {
      executorService.shutdownNow();
    }
  }

  private List<String> buildCommand(TrivyInvocationRequest request) {
    List<String> cmd = new ArrayList<>();
    String trivyPath =
        properties.getTrivy().getPath() != null ? properties.getTrivy().getPath() : "trivy";

    cmd.add(trivyPath);
    cmd.add("image");

    if (properties.getTrivy().isDisableTelemetry()) {
      cmd.add("--disable-telemetry");
    }
    if (properties.getTrivy().isSkipVersionCheck()) {
      cmd.add("--skip-version-check");
    }
    cmd.add("--format");
    cmd.add("json");

    if (request.scanners() != null && !request.scanners().isEmpty()) {
      cmd.add("--scanners");
      cmd.add(String.join(",", request.scanners()));
    }
    if (request.ignoreUnfixed()) {
      cmd.add("--ignore-unfixed");
    }

    Duration t = request.timeout();
    long secs = Math.max(1, t.toSeconds());
    cmd.add("--timeout");
    cmd.add(secs + "s");

    cmd.add(request.image());

    return cmd;
  }

  private static byte[] readAllBounded(InputStream inputStream, int hardLimitBytes)
      throws IOException {
    try (InputStream is = inputStream;
        ByteArrayOutputStream byteArrayOutputStream =
            new ByteArrayOutputStream(Math.min(hardLimitBytes, 64 * 1024))) {
      byte[] buffer = new byte[8192];
      int read;
      int total = 0;
      while ((read = inputStream.read(buffer)) != -1) {
        total += read;
        if (total > hardLimitBytes) {
          throw new IOException("Stream exceeded limit of " + hardLimitBytes + " bytes");
        }
        byteArrayOutputStream.write(buffer, 0, read);
      }
      return byteArrayOutputStream.toByteArray();
    }
  }

  private static byte[] getFuture(Future<byte[]> f, String which, long timeoutMillis)
      throws ScannerException {
    try {
      if (timeoutMillis <= 0) {
        if (!f.isDone()) {
          throw new TimeoutException("Timed out collecting " + which + " from Trivy");
        }
        return f.get(0, TimeUnit.MILLISECONDS);
      }
      return f.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (CancellationException e) {
      throw new ScannerException("Collection of " + which + " from Trivy was canceled", e);
    } catch (TimeoutException e) {
      f.cancel(true);
      throw new ScannerException("Timed out collecting " + which + " from Trivy", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException io) {
        throw new ScannerException(
            "Failed reading " + which + " from Trivy: " + io.getMessage(), io);
      }
      throw new ScannerException("Failed collecting " + which + " from Trivy", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ScannerException("Interrupted collecting " + which + " from Trivy", e);
    }
  }

  private static long remainingMillis(long deadlineNanos) {
    long remainingNanos = deadlineNanos - System.nanoTime();
    return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
  }

  private String detectTrivyVersion() {
    String cached = this.cachedVersion;
    if (cached != null) {
      return cached;
    }

    List<String> cmd =
        List.of(
            properties.getTrivy().getPath() != null ? properties.getTrivy().getPath() : "trivy",
            "--version");
    ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
    try {
      Process p = pb.start();
      boolean finished = p.waitFor(3, TimeUnit.SECONDS);
      if (!finished) {
        p.destroyForcibly();
        return this.cachedVersion = "Trivy";
      }
      byte[] out = readAllBounded(p.getInputStream(), MAX_STDERR_BYTES);
      String s = new String(out, StandardCharsets.UTF_8).trim();
      // Typical output starts with: "Version: 0.65.0"
      String ver = parseVersionLine(s);
      return this.cachedVersion = (ver != null ? "Trivy " + ver : "Trivy");
    } catch (Exception ignored) {
      return this.cachedVersion = "Trivy";
    }
  }

  private static String parseVersionLine(String all) {
    // Very tolerant: look for "Version: x.y.z"
    for (String line : all.split("\\R")) {
      int i = line.indexOf("Version:");
      if (i >= 0) {
        String tail = line.substring(i + "Version:".length()).trim();
        // capture first token
        int sp = tail.indexOf(' ');
        return sp > 0 ? tail.substring(0, sp).trim() : tail;
      }
    }
    return null;
  }

  private static String sanitizeError(String err) {
    if (err == null) {
      return "";
    }
    String s = err.strip();
    s = s.replaceAll("(?i)(password|token|secret|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
    s = s.replaceAll("ghp_[A-Za-z0-9]+", "ghp_<redacted>");
    return s;
  }
}
