package com.example.kitchen.cli;

import com.example.kitchen.service.KitchenSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "challenge.cli", name = "enabled", havingValue = "true")
// CLI entrypoint: runs one simulation round and exits the app
public class ChallengeRunner implements CommandLineRunner {

    /**
     * Simulation orchestrator (place → wait → pickup → submit to /solve).
     */
    private final KitchenSimulator simulator;

    /**
     * Application context used to exit Spring Boot gracefully after CLI completes.
     */
    private final ApplicationContext ctx;

    /**
     * Default rate (orders/sec) if not overridden via CLI.
     */
    @Value("${challenge.rate:2}")
    private int cfgRate;
    /**
     * Default min pickup delay (seconds) if not overridden via CLI.
     */
    @Value("${challenge.min:4}")
    private int cfgMin;
    /**
     * Default max pickup delay (seconds) if not overridden via CLI.
     */
    @Value("${challenge.max:8}")
    private int cfgMax;

    @Override
    public void run(String... args) {
        // Parse CLI flags and merge with config defaults.
        CliOptions opts = CliOptions.parse(args, cfgRate, cfgMin, cfgMax);

        // Print short help and exit if requested.
        if (opts.help) {
            printHelp();
            SpringApplication.exit(ctx, () -> 0);
            return;
        }

        int exit = 0;
        try {
            log.info("Starting simulation: rate={} orders/sec, min={}s, max={}s",
                    opts.rate, opts.min, opts.max);

            // Run the full simulation and block until /solve responds.
            simulator.runSimulation(opts.rate, opts.min, opts.max).block();

            log.info("Simulation finished successfully");
        } catch (Throwable t) {
            // Any unhandled error results in non-zero exit.
            log.error("Simulation failed", t);
            exit = 1;
        } finally {
            // Shut down the Spring context, then terminate the JVM with the chosen exit code.
            // Note: SpringApplication.exit(..) exit code supplier does not affect System.exit below.
            SpringApplication.exit(ctx, () -> 0 /* or () -> exit if you propagate it via ExitCodeGenerator */);
            System.exit(exit);
        }
    }

    /** Prints a concise usage guide for CLI mode. */
    private void printHelp() {
        System.out.println("""
                Kitchen Challenge CLI
                Usage:
                  java -jar app.jar --challenge.cli.enabled=true [--rate=N] [--min=S] [--max=S]
                  or with system props: -Dchallenge.cli.enabled=true
                
                Options:
                  --rate   orders per second (>= 1)      [default from config]
                  --min    min pickup delay, seconds (>=0)
                  --max    max pickup delay, seconds (>=min)
                  --help   show this help
                """);
    }

    // ---- simple CLI parser with sane defaults & clamps ----
    static final class CliOptions {
        final int rate;
        final int min;
        final int max;
        final boolean help;

        private CliOptions(int rate, int min, int max, boolean help) {
            this.rate = rate;
            this.min = min;
            this.max = max;
            this.help = help;
        }

        static CliOptions parse(String[] args, int defRate, int defMin, int defMax) {
            int rate = defRate;
            int min = defMin;
            int max = defMax;
            boolean help = false;

            for (String a : args) {
                if ("--help" .equalsIgnoreCase(a) || "-h" .equalsIgnoreCase(a)) {
                    help = true;
                    continue;
                }
                if (a.startsWith("--rate=")) rate = parseIntSafe(a.substring(7), defRate);
                else if (a.startsWith("--min=")) min = parseIntSafe(a.substring(6), defMin);
                else if (a.startsWith("--max=")) max = parseIntSafe(a.substring(6), defMax);
            }

            // clamps & normalization
            if (rate < 1) rate = 1;
            if (min < 0) min = 0;
            if (max < min) max = min;

            return new CliOptions(rate, min, max, help);
        }

        private static int parseIntSafe(String s, int def) {
            try {
                return Integer.parseInt(s.trim());
            } catch (Exception ignored) {
                return def;
            }
        }
    }
}
