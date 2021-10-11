#!/usr/bin/env stack
-- stack --resolver lts-18.13 script --package turtle
{-# LANGUAGE OverloadedStrings #-}

-----------------------------------------

import Turtle

-----------------------------------------

main :: IO ()
main = do
  let benchmark = "query1.linear.RoundRobin"
  runBenchmark benchmark
  echo "Finished"

runSbt :: MonadIO m => Text -> m ()
runSbt subCmd = do
  printf ("sbt> " % s % "\n" ) subCmd
  shells (format ("sbt \"" % s %  "\"") subCmd) empty

runBenchmark :: MonadIO m => Text -> m ()
runBenchmark = runSbt . toBenchmarkCmd where
  toBenchmarkCmd = format ("benchmark/multi-jvm:run " % s)
