package org.corfudb.benchmarks.merics;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MetricsBenchmark {

    /**
     * Cluster benchmark
     *
     * @param args args
     * @throws RunnerException jmh exception
     */
    public static void main(String[] args) throws RunnerException {
        String benchmarkName = MetricsBenchmark.class.getSimpleName();

        int warmUpIterations = 0;

        int measurementIterations = 1;
        TimeValue measurementTime = TimeValue.seconds(30);

        int threads = 4;
        int forks = 1;

        Options opt = new OptionsBuilder()
                .include(benchmarkName)

                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)

                .warmupIterations(warmUpIterations)

                .measurementIterations(measurementIterations)
                .measurementTime(measurementTime)

                .threads(threads)
                .forks(forks)

                .shouldFailOnError(true)

                .resultFormat(ResultFormatType.CSV)
                .result(Paths.get("benchmarks", "build", benchmarkName + ".csv").toString())

                .build();

        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    @Getter
    @Slf4j
    public static class MetricsBenchmarkState {
        private final CollectorRegistry registry = CollectorRegistry.defaultRegistry;
        private RocksDbExporter exporter;

        /**
         * Init benchmark state
         */
        @Setup
        public void init() {
            Enumeration<MetricFamilySamples> samples = registry.metricFamilySamples();
            exporter = new RocksDbExporter();
            exporter.put();
        }
    }

    @Benchmark
    public void textMetricsSizeBenchmark(MetricsBenchmarkState state) throws Exception {
        try (Writer writer = new StringWriter()) {
            TextFormat.write004(writer, state.registry.metricFamilySamples());
            writer.flush();

            //save to file via logger appender?
        }
    }

    @Benchmark
    public void rocksDbMetricsSizeBenchmark(MetricsBenchmarkState state) throws Exception {
        try (Writer writer = new StringWriter()) {
            TextFormat.write004(writer, state.registry.metricFamilySamples());
            writer.flush();

            //save to file via logger appender?
        }
    }

    static class RocksDbExporter {
        String dbDir = "metrics.db";
        org.rocksdb.Options opts = new org.rocksdb.Options().setCreateIfMissing(true);
        private final RocksDB db;

        @SneakyThrows
        RocksDbExporter() {
            db = RocksDB.open(opts, dbDir);
        }

        void put(String key, String value) throws RocksDBException {
            db.put(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
        }

        Map<String, String> findAll() {
            Map<String, String> data = new HashMap<>();

            try(RocksIterator iter = db.newIterator()){
                iter.seekToFirst();
                while (iter.isValid()) {
                    data.put(new String(iter.key()), new String(iter.value()));
                }
            }

            return data;
        }
    }

}
