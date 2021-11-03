.PHONY : clean clean-benchmarks benchmarks

BENCHMARK_DIR=benchmark

all:

benchmarks: clean-benchmarks
	sbt "benchmark/runMain generator.App"

clean-benchmarks:
	rm -rf $(BENCHMARK_DIR)/benchmark*
	rm -rf $(BENCHMARK_DIR)/src/multi-jvm/scala/*

clean:
	/usr/bin/find . -name "target" -type d -exec rm -rf {} \;