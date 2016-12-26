build:
	sbt clean compile assembly

local-run:
	spark-submit \
			--class io.asuna.totsuki.Main \
			target/scala-2.11/totsuki-assembly.jar NA localhost:9092

deploy:
	sbt publish
