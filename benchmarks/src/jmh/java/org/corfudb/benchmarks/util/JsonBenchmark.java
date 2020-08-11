package org.corfudb.benchmarks.util;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Data generator benchmark measures latency created by a string generation code and other utility
 * classes/methods
 */
public class JsonBenchmark {

    /**
     * Data generator benchmark
     *
     * @param args args
     * @throws RunnerException jmh runner exception
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JsonBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .build();

        new Runner(opt).run();
    }

    @State(Scope.Benchmark)
    @Getter
    public static class JsonState {
        private final Gson gsonParser = new GsonBuilder().create();

        private JsonData jsonData;

        /**
         * Init benchmark state
         */
        @Setup
        public void init() {
            jsonData = new JsonData(DataGenerator.generateDataString(1024 * 4096));
        }
    }

    /**
     * Java random generator benchmark
     *
     * @param blackhole jmh blackhole
     * @param state     the benchmark state
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 5)
    @Threads(value = 1)
    public void googleGson(Blackhole blackhole, JsonState state) {
        String json = state.gsonParser.toJson(state.jsonData);
        blackhole.consume(json);
    }

    /**
     * Java secure random generator benchmark
     *
     * @param blackhole kmh blackhole
     * @param state     the benchmark state
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 1, time = 1)
    @Measurement(iterations = 1, time = 5)
    @Threads(value = 1)
    public void fastJson(Blackhole blackhole, JsonState state) {
        String json = JSON.toJSONString(state.jsonData);
        blackhole.consume(json);
    }

    public static class JsonData {
        private String payload;

        public JsonData(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }
}
